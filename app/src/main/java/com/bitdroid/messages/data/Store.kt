package com.bitdroid.messages.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import com.bitdroid.messages.mms.PduCharset
import com.bitdroid.messages.mms.PduHeaders
import com.bitdroid.messages.mms.PduType
import java.nio.charset.Charset

/**
 * Everything that reads or writes the system Telephony provider
 * (content://sms, content://mms, content://mms-sms). Call off the main thread.
 */
object Store {
    val THREADS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val CANONICAL_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")
    val MMS_PART_URI: Uri = Uri.parse("content://mms/part")

    // ---------------------------------------------------------------- threads

    private fun canonicalAddresses(context: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            context.contentResolver.query(CANONICAL_URI, arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) map[c.getLong(0)] = c.getString(1) ?: ""
            }
        } catch (_: Exception) {
        }
        return map
    }

    /** Unread incoming messages per thread, SMS + MMS. */
    private fun unreadCounts(context: Context): Map<Long, Int> {
        val m = HashMap<Long, Int>()
        try {
            context.contentResolver.query(
                Sms.CONTENT_URI, arrayOf(Sms.THREAD_ID),
                "${Sms.READ} = 0 AND ${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}", null, null,
            )?.use { c -> while (c.moveToNext()) m.merge(c.getLong(0), 1, Int::plus) }
            context.contentResolver.query(
                Mms.CONTENT_URI, arrayOf(Mms.THREAD_ID),
                "${Mms.READ} = 0 AND ${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX}", null, null,
            )?.use { c -> while (c.moveToNext()) m.merge(c.getLong(0), 1, Int::plus) }
        } catch (_: Exception) {
        }
        return m
    }

    fun conversations(context: Context): List<Conversation> {
        val canon = canonicalAddresses(context)
        val counts = unreadCounts(context)
        val pins = Prefs.pins(context)
        val out = ArrayList<Conversation>()
        context.contentResolver.query(
            THREADS_URI,
            arrayOf("_id", "date", "snippet", "snippet_cs", "read", "error", "recipient_ids", "has_attachment", "message_count"),
            null, null, "date DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(8) == 0) continue
                val threadId = c.getLong(0)
                val recipients = (c.getString(6) ?: "").split(' ')
                    .mapNotNull { it.toLongOrNull()?.let(canon::get) }
                    .filter { it.isNotBlank() }
                    .map { Contacts.lookup(context, it) }
                var snippet = decodeCs(c.getString(2), c.getInt(3))
                if (snippet.isBlank()) {
                    snippet = latestMmsText(context, threadId)
                        ?: if (c.getInt(7) != 0) "Picture" else "MMS"
                }
                out += Conversation(
                    threadId = threadId,
                    recipients = recipients,
                    snippet = snippet,
                    date = c.getLong(1),
                    unread = c.getInt(4) == 0 || (counts[threadId] ?: 0) > 0,
                    hasError = c.getInt(5) != 0,
                    unreadCount = counts[threadId] ?: if (c.getInt(4) == 0) 1 else 0,
                    pinned = threadId in pins,
                )
            }
        }
        return out
    }

    fun threadAddresses(context: Context, threadId: Long): List<String> {
        val canon = canonicalAddresses(context)
        context.contentResolver.query(
            THREADS_URI, arrayOf("_id", "recipient_ids"), "_id = ?", arrayOf(threadId.toString()), null,
        )?.use { c ->
            if (c.moveToFirst()) {
                return (c.getString(1) ?: "").split(' ')
                    .mapNotNull { it.toLongOrNull()?.let(canon::get) }
                    .filter { it.isNotBlank() }
            }
        }
        // Thread row not visible (e.g. brand-new thread): fall back to the newest SMS address.
        context.contentResolver.query(
            Sms.CONTENT_URI, arrayOf(Sms.ADDRESS), "${Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "date DESC",
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { return listOf(it) } }
        return emptyList()
    }

    fun threadIdFor(context: Context, addresses: Collection<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses.map { Phone.clean(it) }.toSet())

    fun deleteThread(context: Context, threadId: Long) {
        context.contentResolver.delete(
            ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), null, null,
        )
    }

    fun markThreadRead(context: Context, threadId: Long) {
        val v = ContentValues().apply {
            put("read", 1)
            put("seen", 1)
        }
        val args = arrayOf(threadId.toString())
        try {
            context.contentResolver.update(Sms.CONTENT_URI, v, "thread_id = ? AND (read = 0 OR seen = 0)", args)
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.update(Mms.CONTENT_URI, v, "thread_id = ? AND (read = 0 OR seen = 0)", args)
        } catch (_: Exception) {
        }
    }

    fun markAllSeen(context: Context) {
        val v = ContentValues().apply { put("seen", 1) }
        try {
            context.contentResolver.update(Sms.CONTENT_URI, v, "seen = 0", null)
            context.contentResolver.update(Mms.CONTENT_URI, v, "seen = 0", null)
        } catch (_: Exception) {
        }
    }

    // --------------------------------------------------------------- messages

    fun messages(context: Context, threadId: Long, limit: Int = 1500): List<Message> {
        val out = ArrayList<Message>()
        val args = arrayOf(threadId.toString())
        context.contentResolver.query(
            Sms.CONTENT_URI,
            arrayOf(Sms._ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.STATUS, Sms.SUBSCRIPTION_ID),
            "${Sms.THREAD_ID} = ? AND ${Sms.TYPE} != ${Sms.MESSAGE_TYPE_DRAFT}", args, "date DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) out += smsFromCursor(c, threadId)
        }
        val mms = ArrayList<Message>()
        context.contentResolver.query(
            Mms.CONTENT_URI,
            arrayOf(Mms._ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.MESSAGE_TYPE, Mms.SUBJECT, Mms.SUBJECT_CHARSET, Mms.SUBSCRIPTION_ID, Mms.STATUS),
            "${Mms.THREAD_ID} = ? AND ${Mms.MESSAGE_BOX} != ${Mms.MESSAGE_BOX_DRAFTS}", args, "date DESC",
        )?.use { c ->
            while (c.moveToNext() && mms.size < limit) {
                val id = c.getLong(0)
                val box = c.getInt(2)
                val type = c.getInt(3)
                val outgoing = box != Mms.MESSAGE_BOX_INBOX
                val status = when {
                    type == PduType.NOTIFICATION_IND -> downloadState(context, id)
                    box == Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.SENDING
                    box == Mms.MESSAGE_BOX_FAILED -> MessageStatus.FAILED
                    box == Mms.MESSAGE_BOX_SENT -> MessageStatus.SENT
                    else -> MessageStatus.RECEIVED
                }
                mms += Message(
                    key = "m$id", id = id, isMms = true, threadId = threadId,
                    address = "", body = "", date = c.getLong(1) * 1000L,
                    outgoing = outgoing, status = status, subId = c.getInt(6),
                    subject = decodeCs(c.getString(4), c.getInt(5)).ifBlank { null },
                )
            }
        }
        if (mms.isNotEmpty()) {
            val parts = partsFor(context, mms.map { it.id })
            for (m in mms) {
                val p = parts[m.id].orEmpty()
                val text = p.filter { it.mime == "text/plain" }.mapNotNull { it.text }.joinToString("\n")
                val atts = p.filter { it.isMedia }.map { Attachment(it.uri, it.mime, it.name) }
                val from = if (!m.outgoing) mmsFrom(context, m.id) else ""
                out += m.copy(body = text, attachments = atts, address = from)
            }
        }
        out.sortBy { it.date }
        return out
    }

    private fun smsFromCursor(c: Cursor, threadId: Long): Message {
        val id = c.getLong(0)
        val type = c.getInt(4)
        val st = c.getInt(5)
        val status = when (type) {
            Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
            Sms.MESSAGE_TYPE_OUTBOX, Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
            Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
            else -> if (st == Sms.STATUS_COMPLETE) MessageStatus.DELIVERED else MessageStatus.SENT
        }
        return Message(
            key = "s$id", id = id, isMms = false, threadId = threadId,
            address = c.getString(1) ?: "", body = c.getString(2) ?: "", date = c.getLong(3),
            outgoing = type != Sms.MESSAGE_TYPE_INBOX, status = status, subId = c.getInt(6),
        )
    }

    data class StoredPart(
        val id: Long, val mid: Long, val mime: String, val text: String?, val name: String?,
        val cid: String?, val cl: String?,
    ) {
        val uri: Uri get() = ContentUris.withAppendedId(MMS_PART_URI, id)
        val isMedia: Boolean get() = mime != "text/plain" && mime != "application/smil"
    }

    fun partsFor(context: Context, mids: List<Long>): Map<Long, List<StoredPart>> {
        val out = HashMap<Long, MutableList<StoredPart>>()
        for (chunk in mids.chunked(400)) {
            val sel = "${Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})"
            context.contentResolver.query(
                MMS_PART_URI,
                arrayOf(Mms.Part._ID, Mms.Part.MSG_ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT, Mms.Part.NAME, Mms.Part.FILENAME, Mms.Part.CONTENT_ID, Mms.Part.CONTENT_LOCATION, Mms.Part.SEQ),
                sel, null, "${Mms.Part.SEQ} ASC, ${Mms.Part._ID} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val p = StoredPart(
                        id = c.getLong(0), mid = c.getLong(1),
                        mime = (c.getString(2) ?: "application/octet-stream").lowercase(),
                        text = c.getString(3), name = c.getString(4) ?: c.getString(5),
                        cid = c.getString(6), cl = c.getString(7),
                    )
                    out.getOrPut(p.mid) { ArrayList() }.add(p)
                }
            }
        }
        return out
    }

    fun mmsAddresses(context: Context, mid: Long): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        context.contentResolver.query(
            Uri.parse("content://mms/$mid/addr"), arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE), null, null, null,
        )?.use { c -> while (c.moveToNext()) out += (c.getString(0) ?: "") to c.getInt(1) }
        return out
    }

    private fun mmsFrom(context: Context, mid: Long): String =
        mmsAddresses(context, mid).firstOrNull { it.second == PduHeaders.FROM }?.first?.let(Phone::clean) ?: ""

    private fun latestMmsText(context: Context, threadId: Long): String? {
        context.contentResolver.query(
            Mms.CONTENT_URI, arrayOf(Mms._ID), "${Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), "date DESC",
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val parts = partsFor(context, listOf(c.getLong(0)))[c.getLong(0)].orEmpty()
            parts.firstOrNull { it.mime == "text/plain" && !it.text.isNullOrBlank() }?.text?.let { return it }
            if (parts.any { it.mime.startsWith("image/") }) return "Picture"
            if (parts.any { it.mime.startsWith("video/") }) return "Video"
            if (parts.any { it.isMedia }) return "Attachment"
        }
        return null
    }

    fun deleteMessage(context: Context, m: Message) {
        val uri = if (m.isMms) ContentUris.withAppendedId(Mms.CONTENT_URI, m.id) else ContentUris.withAppendedId(Sms.CONTENT_URI, m.id)
        context.contentResolver.delete(uri, null, null)
    }

    /** Thread for an SMS/MMS row (used after inserts). */
    fun threadOfSms(context: Context, id: Long): Long =
        longColumn(context, ContentUris.withAppendedId(Sms.CONTENT_URI, id), Sms.THREAD_ID)

    fun threadOfMms(context: Context, id: Long): Long =
        longColumn(context, ContentUris.withAppendedId(Mms.CONTENT_URI, id), Mms.THREAD_ID)

    private fun longColumn(context: Context, uri: Uri, col: String): Long {
        context.contentResolver.query(uri, arrayOf(col), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return -1
    }

    fun lastIncomingSub(context: Context, threadId: Long): Int? {
        context.contentResolver.query(
            Sms.CONTENT_URI, arrayOf(Sms.SUBSCRIPTION_ID),
            "${Sms.THREAD_ID} = ? AND ${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}", arrayOf(threadId.toString()), "date DESC",
        )?.use { c -> if (c.moveToFirst()) return c.getInt(0) }
        return null
    }

    // ------------------------------------------------------------------ unread

    data class Unread(val sender: String, val body: String, val date: Long, val hasImage: Boolean)

    fun unreadIn(context: Context, threadId: Long, limit: Int = 8): List<Unread> {
        val out = ArrayList<Unread>()
        val args = arrayOf(threadId.toString())
        context.contentResolver.query(
            Sms.CONTENT_URI, arrayOf(Sms.ADDRESS, Sms.BODY, Sms.DATE),
            "${Sms.THREAD_ID} = ? AND ${Sms.READ} = 0 AND ${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}", args, "date DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) out += Unread(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2), false)
        }
        val mids = ArrayList<Pair<Long, Long>>()
        context.contentResolver.query(
            Mms.CONTENT_URI, arrayOf(Mms._ID, Mms.DATE),
            "${Mms.THREAD_ID} = ? AND ${Mms.READ} = 0 AND ${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX}", args, "date DESC",
        )?.use { c -> while (c.moveToNext() && mids.size < limit) mids += c.getLong(0) to c.getLong(1) * 1000L }
        if (mids.isNotEmpty()) {
            val parts = partsFor(context, mids.map { it.first })
            for ((id, date) in mids) {
                val p = parts[id].orEmpty()
                val text = p.filter { it.mime == "text/plain" }.mapNotNull { it.text }.joinToString("\n")
                val img = p.any { it.mime.startsWith("image/") }
                out += Unread(mmsFrom(context, id), text.ifBlank { if (img) "Picture" else "MMS message" }, date, img)
            }
        }
        out.sortBy { it.date }
        return out.takeLast(limit)
    }

    // ------------------------------------------------------------------ search

    fun search(context: Context, query: String, conversations: List<Conversation>): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val byThread = conversations.associateBy { it.threadId }
        val hits = ArrayList<SearchHit>()
        val like = "%${query.replace("%", "\\%").replace("_", "\\_")}%"
        context.contentResolver.query(
            Sms.CONTENT_URI, arrayOf(Sms.THREAD_ID, Sms.BODY, Sms.DATE),
            "${Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf(like), "date DESC",
        )?.use { c ->
            while (c.moveToNext() && hits.size < 150) {
                val conv = byThread[c.getLong(0)] ?: continue
                hits += SearchHit(conv.threadId, conv.title, c.getString(1) ?: "", c.getLong(2), conv.recipients.firstOrNull())
            }
        }
        try {
            val mids = HashMap<Long, String>()
            context.contentResolver.query(
                MMS_PART_URI, arrayOf(Mms.Part.MSG_ID, Mms.Part.TEXT),
                "${Mms.Part.CONTENT_TYPE} = 'text/plain' AND ${Mms.Part.TEXT} LIKE ? ESCAPE '\\'", arrayOf(like), null,
            )?.use { c -> while (c.moveToNext() && mids.size < 100) mids[c.getLong(0)] = c.getString(1) ?: "" }
            if (mids.isNotEmpty()) {
                context.contentResolver.query(
                    Mms.CONTENT_URI, arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE),
                    "${Mms._ID} IN (${mids.keys.joinToString(",")})", null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val conv = byThread[c.getLong(1)] ?: continue
                        hits += SearchHit(conv.threadId, conv.title, mids[c.getLong(0)] ?: "", c.getLong(2) * 1000L, conv.recipients.firstOrNull())
                    }
                }
            }
        } catch (_: Exception) {
        }
        hits.sortByDescending { it.date }
        return hits
    }

    // ----------------------------------------------------------------- inserts

    fun insertIncomingSms(
        context: Context, address: String, body: String, dateSent: Long, received: Long, subId: Int,
        serviceCenter: String?, protocol: Int, replyPath: Boolean,
    ): Uri? {
        val v = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, received)
            put(Sms.DATE_SENT, dateSent)
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
            put(Sms.TYPE, Sms.MESSAGE_TYPE_INBOX)
            put(Sms.SUBSCRIPTION_ID, subId)
            put(Sms.PROTOCOL, protocol)
            put(Sms.REPLY_PATH_PRESENT, if (replyPath) 1 else 0)
            serviceCenter?.let { put(Sms.SERVICE_CENTER, it) }
        }
        return context.contentResolver.insert(Sms.Inbox.CONTENT_URI, v)
    }

    fun insertOutgoingSms(context: Context, address: String, body: String, threadId: Long, subId: Int, wantDelivery: Boolean): Uri? {
        val v = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
            put(Sms.TYPE, Sms.MESSAGE_TYPE_OUTBOX)
            put(Sms.THREAD_ID, threadId)
            put(Sms.SUBSCRIPTION_ID, subId)
            put(Sms.STATUS, if (wantDelivery) Sms.STATUS_PENDING else Sms.STATUS_NONE)
        }
        return context.contentResolver.insert(Sms.CONTENT_URI, v)
    }

    fun updateSms(context: Context, id: Long, v: ContentValues) {
        context.contentResolver.update(ContentUris.withAppendedId(Sms.CONTENT_URI, id), v, null, null)
    }

    fun updateMms(context: Context, id: Long, v: ContentValues) {
        context.contentResolver.update(ContentUris.withAppendedId(Mms.CONTENT_URI, id), v, null, null)
    }

    data class SmsRow(val address: String, val body: String, val subId: Int, val threadId: Long)

    fun smsRow(context: Context, id: Long): SmsRow? {
        context.contentResolver.query(
            ContentUris.withAppendedId(Sms.CONTENT_URI, id),
            arrayOf(Sms.ADDRESS, Sms.BODY, Sms.SUBSCRIPTION_ID, Sms.THREAD_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst()) return SmsRow(c.getString(0) ?: "", c.getString(1) ?: "", c.getInt(2), c.getLong(3)) }
        return null
    }

    // ------------------------------------------------------------------ helpers

    /** Snippets / subjects for MMS are stored as ISO-8859-1 bytes with a separate charset. */
    fun decodeCs(s: String?, cs: Int): String {
        if (s.isNullOrEmpty()) return ""
        if (cs == 0 || cs == PduCharset.UTF8) return s
        return try {
            String(s.toByteArray(Charsets.ISO_8859_1), Charset.forName(PduCharset.name(cs)))
        } catch (_: Exception) {
            s
        }
    }

    private fun downloadState(context: Context, id: Long): MessageStatus =
        when (Prefs.get(context).getInt("dl_$id", 0)) {
            1 -> MessageStatus.DOWNLOADING
            2 -> MessageStatus.DOWNLOAD_FAILED
            else -> MessageStatus.PENDING_DOWNLOAD
        }

    fun setDownloadState(context: Context, id: Long, state: Int) {
        val e = Prefs.get(context).edit()
        if (state == 0) e.remove("dl_$id") else e.putInt("dl_$id", state)
        e.apply()
    }
}
