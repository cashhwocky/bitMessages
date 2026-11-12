package com.bitdroid.messages.mms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Mms
import android.telephony.SmsManager
import com.bitdroid.messages.data.Prefs
import com.bitdroid.messages.data.Sims
import com.bitdroid.messages.data.Store

object MmsSender {
    const val ACTION_SENT = "com.bitdroid.messages.MMS_SENT"
    const val ACTION_DOWNLOADED = "com.bitdroid.messages.MMS_DOWNLOADED"
    const val ACTION_NOTIFY_RESP = "com.bitdroid.messages.MMS_NOTIFY_RESP"
    const val EXTRA_ID = "id"
    const val EXTRA_FILE = "file"
    const val EXTRA_SUB = "sub"
    const val EXTRA_LOCATION = "location"
    const val EXTRA_TXN = "txn"

    private fun maxMessageSize(context: Context, subId: Int): Int = try {
        Sims.smsManager(context, subId).carrierConfigValues
            .getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024)
            .coerceIn(100 * 1024, 2 * 1024 * 1024)
    } catch (_: Exception) {
        300 * 1024
    }

    private fun newTxnId(): String = "T" + java.lang.Long.toHexString(System.currentTimeMillis()) +
        java.lang.Integer.toHexString((Math.random() * 0xFFFF).toInt())

    /** Builds parts from text + images, stores in the outbox and sends. Returns the MMS id. */
    fun send(context: Context, threadId: Long, recipients: List<String>, text: String, images: List<Uri>, subId: Int): Long {
        val budget = maxMessageSize(context, subId) - 8 * 1024 - text.toByteArray().size
        val perImage = if (images.isEmpty()) 0 else maxOf(40 * 1024, budget / images.size)
        val parts = ArrayList<MmsPart>()
        images.forEachIndexed { i, uri ->
            try {
                val (mime, bytes) = ImageCompressor.compress(context, uri, perImage)
                val ext = if (mime == "image/gif") "gif" else "jpg"
                val name = "image$i.$ext"
                parts += MmsPart(mime, bytes, contentId = "<image$i>", contentLocation = name, name = name)
            } catch (e: Exception) {
                android.util.Log.w("bitMessages", "image $uri failed", e)
            }
        }
        if (text.isNotBlank()) {
            parts += MmsPart(MIME_TEXT, text.toByteArray(Charsets.UTF_8), contentId = "<text0>", contentLocation = "text0.txt", charset = PduCharset.UTF8)
        }
        if (parts.isEmpty()) return -1
        parts.add(0, MmsPduComposer.smil(parts))
        val txn = newTxnId()
        val id = MmsStore.insertOutgoing(context, threadId, recipients, parts, subId, txn, null)
        if (id < 0) return -1
        transmit(context, id, recipients, parts, subId, txn, null)
        return id
    }

    fun resend(context: Context, id: Long, subIdOverride: Int? = null) {
        val (recipients, parts, subject) = MmsStore.loadForResend(context, id) ?: return
        var subId = subIdOverride ?: -1
        if (subId < 0) {
            context.contentResolver.query(Uri.parse("content://mms/$id"), arrayOf(Mms.SUBSCRIPTION_ID), null, null, null)?.use { c ->
                if (c.moveToFirst()) subId = c.getInt(0)
            }
        }
        Store.updateMms(context, id, ContentValues().apply {
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.SUBSCRIPTION_ID, subId)
        })
        transmit(context, id, recipients, parts, subId, newTxnId(), subject)
    }

    private fun transmit(context: Context, id: Long, recipients: List<String>, parts: List<MmsPart>, subId: Int, txn: String, subject: String?) {
        val pdu = MmsPduComposer.sendReq(txn, recipients, subject, parts, Prefs.deliveryReports(context))
        val name = "send_$id.pdu"
        MmsFileProvider.file(context, name).writeBytes(pdu)
        val uri = MmsFileProvider.uri(context, name, write = false)
        val intent = Intent(context, MmsResultReceiver::class.java).apply {
            action = ACTION_SENT
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_FILE, name)
            putExtra(EXTRA_SUB, subId)
        }
        val pi = PendingIntent.getBroadcast(
            context, Prefs.nextId(context), intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        try {
            Sims.smsManager(context, subId).sendMultimediaMessage(context, uri, null, null, pi)
        } catch (e: Exception) {
            android.util.Log.e("bitMessages", "sendMultimediaMessage failed", e)
            markFailed(context, id)
        }
    }

    fun markFailed(context: Context, id: Long) {
        Store.updateMms(context, id, ContentValues().apply { put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_FAILED) })
    }

    /** Asks the platform MmsService to fetch an MMS whose m-notification-ind we stored as [id]. */
    fun download(context: Context, id: Long) {
        val row = MmsStore.notificationRow(context, id) ?: return
        Store.setDownloadState(context, id, 1)
        val name = "dl_$id.pdu"
        val f = MmsFileProvider.file(context, name)
        f.delete()
        f.createNewFile()
        val uri = MmsFileProvider.uri(context, name, write = true)
        val intent = Intent(context, MmsResultReceiver::class.java).apply {
            action = ACTION_DOWNLOADED
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_FILE, name)
            putExtra(EXTRA_SUB, row.subId)
            putExtra(EXTRA_LOCATION, row.contentLocation)
            putExtra(EXTRA_TXN, row.transactionId)
        }
        val pi = PendingIntent.getBroadcast(
            context, Prefs.nextId(context), intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        try {
            Sims.smsManager(context, row.subId).downloadMultimediaMessage(context, row.contentLocation, uri, null, pi)
        } catch (e: Exception) {
            android.util.Log.e("bitMessages", "downloadMultimediaMessage failed", e)
            Store.setDownloadState(context, id, 2)
        }
    }

    /** Tells the MMSC we retrieved the message (m-notifyresp-ind). */
    fun sendNotifyResp(context: Context, transactionId: String, subId: Int) {
        val name = "resp_${System.nanoTime()}.pdu"
        MmsFileProvider.file(context, name).writeBytes(MmsPduComposer.notifyRespInd(transactionId))
        val uri = MmsFileProvider.uri(context, name, write = false)
        val intent = Intent(context, MmsResultReceiver::class.java).apply {
            action = ACTION_NOTIFY_RESP
            putExtra(EXTRA_FILE, name)
        }
        val pi = PendingIntent.getBroadcast(
            context, Prefs.nextId(context), intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        try {
            Sims.smsManager(context, subId).sendMultimediaMessage(context, uri, null, null, pi)
        } catch (_: Exception) {
            MmsFileProvider.file(context, name).delete()
        }
    }
}
