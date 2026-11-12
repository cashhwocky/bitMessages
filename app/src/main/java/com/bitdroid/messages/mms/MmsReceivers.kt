package com.bitdroid.messages.mms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.bitdroid.messages.boot.DirectBootQueue
import com.bitdroid.messages.data.Blocked
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.Store
import com.bitdroid.messages.notify.Notifier
import com.bitdroid.messages.runAsync

/** WAP_PUSH_DELIVER: an m-notification-ind telling us an MMS is waiting at the MMSC. */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", SubscriptionManager.getDefaultSmsSubscriptionId()),
        )
        runAsync {
            val um = context.getSystemService(UserManager::class.java)
            if (um != null && !um.isUserUnlocked) {
                // Before first unlock the Telephony provider is unavailable: queue and notify.
                DirectBootQueue.addMms(context, data, subId)
                val from = try { MmsPduParser.parse(data).from } catch (_: Exception) { null }
                Notifier.notifyLocked(context, from?.let(Phone::clean) ?: "Someone", "Picture or group message")
                return@runAsync
            }
            MmsIncoming.handleWapPush(context, data, subId)
        }
    }
}

object MmsIncoming {
    fun handleWapPush(context: Context, data: ByteArray, subId: Int) {
        val pdu = try {
            MmsPduParser.parse(data)
        } catch (e: Exception) {
            android.util.Log.w("bitMessages", "bad WAP push", e)
            return
        }
        when (pdu.messageType) {
            PduType.NOTIFICATION_IND -> {
                if (pdu.contentLocation.isNullOrBlank()) return
                if (MmsStore.isDuplicate(context, pdu.contentLocation)) return
                if (pdu.from != null && Blocked.isBlocked(context, Phone.clean(pdu.from!!))) return
                val id = MmsStore.insertNotification(context, pdu, subId)
                if (id >= 0) MmsSender.download(context, id)
            }
            PduType.DELIVERY_IND -> {
                // Delivery report for an MMS we sent: nothing to show beyond "Sent" today.
            }
            else -> Unit
        }
    }
}

/** Results from SmsManager.sendMultimediaMessage / downloadMultimediaMessage. */
class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = resultCode
        runAsync {
            val file = intent.getStringExtra(MmsSender.EXTRA_FILE)
            when (intent.action) {
                MmsSender.ACTION_SENT -> onSent(context, intent, code)
                MmsSender.ACTION_DOWNLOADED -> onDownloaded(context, intent, code)
            }
            file?.let { MmsFileProvider.file(context, it).delete() }
        }
    }

    private fun onSent(context: Context, intent: Intent, code: Int) {
        val id = intent.getLongExtra(MmsSender.EXTRA_ID, -1)
        if (id < 0) return
        var ok = code == Activity.RESULT_OK
        var messageId: String? = null
        var respStatus = 0
        intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)?.let { bytes ->
            try {
                val conf = MmsPduParser.parse(bytes)
                respStatus = conf.responseStatus
                messageId = conf.messageId
                if (respStatus != 0 && respStatus != PduHeaders.RESPONSE_OK) ok = false
            } catch (_: Exception) {
            }
        }
        val v = android.content.ContentValues().apply {
            put(android.provider.Telephony.Mms.MESSAGE_BOX, if (ok) android.provider.Telephony.Mms.MESSAGE_BOX_SENT else android.provider.Telephony.Mms.MESSAGE_BOX_FAILED)
            messageId?.let { put(android.provider.Telephony.Mms.MESSAGE_ID, it) }
            if (respStatus != 0) put(android.provider.Telephony.Mms.RESPONSE_STATUS, respStatus)
        }
        Store.updateMms(context, id, v)
        if (!ok) {
            val thread = Store.threadOfMms(context, id)
            if (thread >= 0) Notifier.notifyFailed(context, thread)
        }
    }

    private fun onDownloaded(context: Context, intent: Intent, code: Int) {
        val id = intent.getLongExtra(MmsSender.EXTRA_ID, -1)
        val subId = intent.getIntExtra(MmsSender.EXTRA_SUB, -1)
        val name = intent.getStringExtra(MmsSender.EXTRA_FILE) ?: return
        val location = intent.getStringExtra(MmsSender.EXTRA_LOCATION)
        val txn = intent.getStringExtra(MmsSender.EXTRA_TXN)
        val bytes = MmsFileProvider.file(context, name).takeIf { it.exists() }?.readBytes()
        if (code != Activity.RESULT_OK || bytes == null || bytes.isEmpty()) {
            android.util.Log.w("bitMessages", "MMS download failed: result=$code")
            Store.setDownloadState(context, id, 2)
            val thread = Store.threadOfMms(context, id)
            if (thread >= 0) Notifier.notifyThread(context, thread)
            return
        }
        val pdu = try {
            MmsPduParser.parse(bytes)
        } catch (e: Exception) {
            android.util.Log.w("bitMessages", "bad retrieve-conf", e)
            Store.setDownloadState(context, id, 2)
            return
        }
        val (mid, threadId) = MmsStore.insertRetrieved(context, pdu, subId, location)
        if (mid < 0) {
            Store.setDownloadState(context, id, 2)
            return
        }
        // Replace the placeholder notification row.
        context.contentResolver.delete(android.net.Uri.parse("content://mms/$id"), null, null)
        Store.setDownloadState(context, id, 0)
        (txn ?: pdu.transactionId)?.let { MmsSender.sendNotifyResp(context, it, subId) }
        Notifier.notifyThread(context, threadId)
    }
}
