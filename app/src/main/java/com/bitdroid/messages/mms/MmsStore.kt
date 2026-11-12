package com.bitdroid.messages.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.Sims
import com.bitdroid.messages.data.Store

/** Writes MMS rows, addresses and parts into content://mms. */
object MmsStore {

    /** The other people in a conversation: sender + recipients, minus our own number(s). */
    fun participants(context: Context, pdu: MmsPdu, subId: Int): List<String> {
        val own = Sims.ownNumbers(context)
        val ownForSub = Sims.active(context).firstOrNull { it.subId == subId }?.number
        val all = ArrayList<String>()
        pdu.from?.let { all += Phone.clean(it) }
        (pdu.to + pdu.cc).map { Phone.clean(it) }.forEach { all += it }
        val distinct = ArrayList<String>()
        for (a in all) {
            if (a.isBlank() || a.equals("insert-address-token", true)) continue
            if (own.any { Phone.same(it, a) } || (ownForSub != null && Phone.same(ownForSub, a))) continue
            if (distinct.none { Phone.same(it, a) }) distinct += a
        }
        // If we couldn't learn our own number and the message was only addressed to one
        // person, that person is us: keep it a 1:1 thread with the sender.
        if (own.isEmpty() && ownForSub == null && pdu.to.size + pdu.cc.size <= 1) {
            return listOfNotNull(pdu.from?.let(Phone::clean)).ifEmpty { distinct }
        }
        return distinct.ifEmpty { listOfNotNull(pdu.from?.let(Phone::clean)) }
    }

    fun insertNotification(context: Context, pdu: MmsPdu, subId: Int): Long {
        val from = pdu.from?.let(Phone::clean) ?: ""
        val threadId = Store.threadIdFor(context, listOf(from.ifBlank { "unknown" }))
        val v = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, PduType.NOTIFICATION_IND)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.CONTENT_LOCATION, pdu.contentLocation)
            put(Mms.TRANSACTION_ID, pdu.transactionId)
            put(Mms.MESSAGE_SIZE, pdu.messageSize)
            put(Mms.EXPIRY, pdu.expiry)
            put(Mms.MESSAGE_CLASS, "personal")
            put(Mms.MMS_VERSION, pdu.version)
            put(Mms.SUBSCRIPTION_ID, subId)
            pdu.subject?.let { putSubject(this, it) }
        }
        val uri = context.contentResolver.insert(Mms.Inbox.CONTENT_URI, v) ?: return -1
        val id = ContentUris.parseId(uri)
        insertAddr(context, id, from, PduHeaders.FROM)
        return id
    }

    /** True if we already have an MMS with this content location (duplicate WAP push). */
    fun isDuplicate(context: Context, contentLocation: String?): Boolean {
        if (contentLocation.isNullOrBlank()) return false
        context.contentResolver.query(
            Mms.CONTENT_URI, arrayOf(Mms._ID), "${Mms.CONTENT_LOCATION} = ?", arrayOf(contentLocation), null,
        )?.use { return it.count > 0 }
        return false
    }

    data class NotificationRow(val contentLocation: String, val transactionId: String?, val subId: Int, val threadId: Long)

    fun notificationRow(context: Context, id: Long): NotificationRow? {
        context.contentResolver.query(
            ContentUris.withAppendedId(Mms.CONTENT_URI, id),
            arrayOf(Mms.CONTENT_LOCATION, Mms.TRANSACTION_ID, Mms.SUBSCRIPTION_ID, Mms.THREAD_ID), null, null, null,
        )?.use { c ->
            if (c.moveToFirst() && c.getString(0) != null) return NotificationRow(c.getString(0), c.getString(1), c.getInt(2), c.getLong(3))
        }
        return null
    }

    /** Stores a downloaded m-retrieve-conf; returns (messageId, threadId). */
    fun insertRetrieved(context: Context, pdu: MmsPdu, subId: Int, contentLocation: String?): Pair<Long, Long> {
        val people = participants(context, pdu, subId)
        val threadId = Store.threadIdFor(context, people.ifEmpty { listOf("unknown") })
        val textOnly = pdu.parts.all { it.isText || it.isSmil }
        val v = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, PduType.RETRIEVE_CONF)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            if (pdu.date > 0) put(Mms.DATE_SENT, pdu.date)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.MESSAGE_ID, pdu.messageId)
            put(Mms.TRANSACTION_ID, pdu.transactionId)
            put(Mms.CONTENT_TYPE, pdu.contentType)
            put(Mms.CONTENT_LOCATION, contentLocation)
            put(Mms.MMS_VERSION, pdu.version)
            put(Mms.MESSAGE_CLASS, "personal")
            put(Mms.SUBSCRIPTION_ID, subId)
            put(Mms.TEXT_ONLY, if (textOnly) 1 else 0)
            put(Mms.MESSAGE_SIZE, pdu.parts.sumOf { it.data.size })
            pdu.subject?.let { putSubject(this, it) }
        }
        val uri = context.contentResolver.insert(Mms.Inbox.CONTENT_URI, v) ?: return -1L to -1L
        val id = ContentUris.parseId(uri)
        pdu.from?.let { insertAddr(context, id, Phone.clean(it), PduHeaders.FROM) }
        pdu.to.forEach { insertAddr(context, id, Phone.clean(it), PduHeaders.TO) }
        pdu.cc.forEach { insertAddr(context, id, Phone.clean(it), PduHeaders.CC) }
        pdu.parts.forEachIndexed { i, p -> insertPart(context, id, p, i) }
        return id to threadId
    }

    /** Stores an outgoing MMS in the outbox; returns its id. */
    fun insertOutgoing(context: Context, threadId: Long, recipients: List<String>, parts: List<MmsPart>, subId: Int, txnId: String, subject: String?): Long {
        val v = ContentValues().apply {
            put(Mms.THREAD_ID, threadId)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX)
            put(Mms.MESSAGE_TYPE, PduType.SEND_REQ)
            put(Mms.DATE, System.currentTimeMillis() / 1000)
            put(Mms.READ, 1)
            put(Mms.SEEN, 1)
            put(Mms.TRANSACTION_ID, txnId)
            put(Mms.CONTENT_TYPE, MIME_MULTIPART_RELATED)
            put(Mms.MMS_VERSION, PduHeaders.MMS_VERSION_1_2)
            put(Mms.MESSAGE_CLASS, "personal")
            put(Mms.PRIORITY, PduHeaders.PRIORITY_NORMAL)
            put(Mms.EXPIRY, 7 * 24 * 3600)
            put(Mms.SUBSCRIPTION_ID, subId)
            put(Mms.TEXT_ONLY, if (parts.all { it.isText || it.isSmil }) 1 else 0)
            put(Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
            subject?.let { putSubject(this, it) }
        }
        val uri = context.contentResolver.insert(Mms.Outbox.CONTENT_URI, v) ?: return -1
        val id = ContentUris.parseId(uri)
        insertAddr(context, id, "insert-address-token", PduHeaders.FROM)
        recipients.forEach { insertAddr(context, id, Phone.clean(it), PduHeaders.TO) }
        parts.forEachIndexed { i, p -> insertPart(context, id, p, i) }
        return id
    }

    /** Re-reads a stored message as composer input (for retries). */
    fun loadForResend(context: Context, id: Long): Triple<List<String>, List<MmsPart>, String?>? {
        val recipients = Store.mmsAddresses(context, id).filter { it.second == PduHeaders.TO || it.second == PduHeaders.CC }.map { it.first }
        if (recipients.isEmpty()) return null
        val stored = Store.partsFor(context, listOf(id))[id].orEmpty()
        val parts = stored.mapNotNull { sp ->
            val data: ByteArray? = if (sp.mime == MIME_TEXT || sp.mime == MIME_SMIL) {
                sp.text?.toByteArray()
            } else {
                try {
                    context.contentResolver.openInputStream(sp.uri)?.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            }
            data?.let {
                MmsPart(sp.mime, it, sp.cid ?: "<${sp.cl}>", sp.cl, sp.name, if (sp.mime == MIME_TEXT || sp.mime == MIME_SMIL) PduCharset.UTF8 else 0)
            }
        }
        var subject: String? = null
        context.contentResolver.query(ContentUris.withAppendedId(Mms.CONTENT_URI, id), arrayOf(Mms.SUBJECT, Mms.SUBJECT_CHARSET), null, null, null)?.use { c ->
            if (c.moveToFirst()) subject = Store.decodeCs(c.getString(0), c.getInt(1)).ifBlank { null }
        }
        return Triple(recipients, parts, subject)
    }

    private fun putSubject(v: ContentValues, subject: String) {
        // Subjects are stored as ISO-8859-1 "bytes" with the real charset alongside.
        v.put(Mms.SUBJECT, String(subject.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1))
        v.put(Mms.SUBJECT_CHARSET, PduCharset.UTF8)
    }

    private fun insertAddr(context: Context, mid: Long, address: String, type: Int) {
        val v = ContentValues().apply {
            put(Mms.Addr.ADDRESS, address)
            put(Mms.Addr.TYPE, type)
            put(Mms.Addr.CHARSET, PduCharset.UTF8)
        }
        try {
            context.contentResolver.insert(Uri.parse("content://mms/$mid/addr"), v)
        } catch (_: Exception) {
        }
    }

    private fun insertPart(context: Context, mid: Long, p: MmsPart, seq: Int) {
        val v = ContentValues().apply {
            put(Mms.Part.MSG_ID, mid)
            put(Mms.Part.SEQ, if (p.isSmil) -1 else seq)
            put(Mms.Part.CONTENT_TYPE, p.mime)
            p.contentId?.let { put(Mms.Part.CONTENT_ID, it) }
            p.contentLocation?.let { put(Mms.Part.CONTENT_LOCATION, it) }
            p.name?.let { put(Mms.Part.NAME, it) }
            if (p.isText || p.isSmil) {
                put(Mms.Part.CHARSET, PduCharset.UTF8)
                put(Mms.Part.TEXT, p.text())
            }
        }
        val uri = try {
            context.contentResolver.insert(Uri.parse("content://mms/$mid/part"), v)
        } catch (_: Exception) {
            null
        } ?: return
        if (!p.isText && !p.isSmil) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(p.data) }
            } catch (_: Exception) {
            }
        }
    }
}
