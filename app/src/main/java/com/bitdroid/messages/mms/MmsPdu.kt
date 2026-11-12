package com.bitdroid.messages.mms

/** A body part of an MMS (text, SMIL, image, ...). */
class MmsPart(
    val mime: String,
    val data: ByteArray,
    val contentId: String? = null,
    val contentLocation: String? = null,
    val name: String? = null,
    val charset: Int = 0,
) {
    val isText: Boolean get() = mime == MIME_TEXT
    val isSmil: Boolean get() = mime == MIME_SMIL
    fun text(): String = PduCharset.decode(data, if (charset == 0) PduCharset.UTF8 else charset)
}

/** Parsed headers + body of any MMS PDU we care about. */
class MmsPdu {
    var messageType = 0
    var transactionId: String? = null
    var version = PduHeaders.MMS_VERSION_1_2
    var from: String? = null
    val to = ArrayList<String>()
    val cc = ArrayList<String>()
    var subject: String? = null
    var contentLocation: String? = null
    var messageSize = 0L
    var date = 0L
    var expiry = 0L
    var messageId: String? = null
    var responseStatus = 0
    var status = 0
    var retrieveStatus = 0
    var contentType: String? = null
    var messageClass = PduHeaders.CLASS_PERSONAL
    val parts = ArrayList<MmsPart>()
}

object MmsPduParser {
    fun parse(bytes: ByteArray): MmsPdu {
        val r = PduReader(bytes)
        val pdu = MmsPdu()
        while (r.hasMore()) {
            val h = r.peek()
            if (h < 0x80) {
                // Application header: token-text name + text value.
                r.text(); if (r.hasMore()) r.text()
                continue
            }
            r.octet()
            when (h) {
                PduHeaders.MESSAGE_TYPE -> pdu.messageType = r.octet()
                PduHeaders.TRANSACTION_ID -> pdu.transactionId = r.text()
                PduHeaders.MMS_VERSION -> pdu.version = r.octet()
                PduHeaders.FROM -> {
                    val len = r.valueLength()
                    val end = r.pos + len
                    if (len > 0 && r.octet() == PduHeaders.ADDRESS_PRESENT_TOKEN && r.pos < end) {
                        pdu.from = r.encodedString()
                    }
                    r.pos = end
                }
                PduHeaders.TO -> pdu.to += r.encodedString()
                PduHeaders.CC -> pdu.cc += r.encodedString()
                PduHeaders.BCC -> r.encodedString()
                PduHeaders.SUBJECT -> pdu.subject = r.encodedString()
                PduHeaders.CONTENT_LOCATION -> pdu.contentLocation = r.text()
                PduHeaders.MESSAGE_SIZE -> pdu.messageSize = r.integer()
                PduHeaders.DATE -> pdu.date = r.longInt()
                PduHeaders.MESSAGE_ID -> pdu.messageId = r.text()
                PduHeaders.RESPONSE_STATUS -> pdu.responseStatus = r.octet()
                PduHeaders.STATUS -> pdu.status = r.octet()
                PduHeaders.RETRIEVE_STATUS -> pdu.retrieveStatus = r.octet()
                PduHeaders.MESSAGE_CLASS -> {
                    if (r.peek() >= 0x80) pdu.messageClass = r.octet() else r.text()
                }
                PduHeaders.EXPIRY -> {
                    val len = r.valueLength()
                    val end = r.pos + len
                    if (len > 0) {
                        val token = r.octet()
                        val v = r.longInt()
                        pdu.expiry = if (token == PduHeaders.RELATIVE_TOKEN) System.currentTimeMillis() / 1000 + v else v
                    }
                    r.pos = end
                }
                PduHeaders.CONTENT_TYPE -> {
                    val ct = r.contentType()
                    pdu.contentType = ct.mime
                    if (ct.mime.contains("multipart")) {
                        parseMultipart(r, pdu.parts)
                    } else if (r.hasMore()) {
                        // Single-part body (rare): treat the remainder as one part.
                        pdu.parts += MmsPart(ct.mime, r.bytes(r.remaining), charset = ct.params["charset"]?.toIntOrNull() ?: 0)
                    }
                    return pdu // Content-Type is always the last header.
                }
                else -> r.skipValue()
            }
        }
        return pdu
    }

    private fun parseMultipart(r: PduReader, out: MutableList<MmsPart>) {
        if (!r.hasMore()) return
        val count = r.uintvar().toInt()
        repeat(count) {
            if (!r.hasMore()) return
            val headersLen = r.uintvar().toInt()
            val dataLen = r.uintvar().toInt()
            val headersEnd = r.pos + headersLen
            val ct = r.contentType()
            var cid: String? = null
            var cl: String? = null
            while (r.pos < headersEnd) {
                val h = r.peek()
                if (h >= 0x80) {
                    r.octet()
                    when (h) {
                        PduHeaders.PART_CONTENT_ID -> cid = r.text()
                        PduHeaders.PART_CONTENT_LOCATION -> cl = r.text()
                        else -> r.skipValue()
                    }
                } else {
                    val name = r.text().lowercase()
                    val value = if (r.pos < headersEnd) r.text() else ""
                    when (name) {
                        "content-id" -> cid = value
                        "content-location" -> cl = value
                    }
                }
            }
            r.pos = headersEnd
            val data = r.bytes(dataLen)
            if (ct.mime.contains("multipart")) {
                // Nested multipart (e.g. multipart/alternative): flatten.
                try {
                    parseMultipart(PduReader(data), out)
                } catch (_: Exception) {
                }
            } else {
                out += MmsPart(
                    mime = ct.mime,
                    data = data,
                    contentId = cid?.trim(),
                    contentLocation = cl,
                    name = ct.params["name"] ?: ct.params["filename"],
                    charset = ct.params["charset"]?.toIntOrNull() ?: 0,
                )
            }
        }
    }
}

object MmsPduComposer {
    private fun address(a: String): String = if (a.contains('@')) a else "${a.filter { it.isDigit() || it == '+' }}/TYPE=PLMN"

    fun sendReq(
        transactionId: String,
        recipients: List<String>,
        subject: String?,
        parts: List<MmsPart>,
        deliveryReport: Boolean,
        expirySeconds: Long = 7 * 24 * 3600L,
    ): ByteArray {
        val w = PduWriter()
        w.octet(PduHeaders.MESSAGE_TYPE).octet(PduType.SEND_REQ)
        w.octet(PduHeaders.TRANSACTION_ID).text(transactionId)
        w.octet(PduHeaders.MMS_VERSION).octet(PduHeaders.MMS_VERSION_1_2)
        w.octet(PduHeaders.DATE).longInt(System.currentTimeMillis() / 1000)
        w.octet(PduHeaders.FROM).valueLength(1).octet(PduHeaders.INSERT_ADDRESS_TOKEN)
        for (r in recipients) w.octet(PduHeaders.TO).encodedString(address(r))
        if (!subject.isNullOrBlank()) w.octet(PduHeaders.SUBJECT).encodedString(subject)
        w.octet(PduHeaders.MESSAGE_CLASS).octet(PduHeaders.CLASS_PERSONAL)
        val exp = PduWriter().octet(PduHeaders.RELATIVE_TOKEN).longInt(expirySeconds).bytes()
        w.octet(PduHeaders.EXPIRY).valueLength(exp.size).raw(exp)
        w.octet(PduHeaders.PRIORITY).octet(PduHeaders.PRIORITY_NORMAL)
        w.octet(PduHeaders.DELIVERY_REPORT).octet(if (deliveryReport) PduHeaders.VALUE_YES else PduHeaders.VALUE_NO)
        w.octet(PduHeaders.READ_REPORT).octet(PduHeaders.VALUE_NO)

        val smil = parts.firstOrNull { it.isSmil }
        w.octet(PduHeaders.CONTENT_TYPE)
        if (smil != null) {
            w.contentType(MIME_MULTIPART_RELATED) {
                octet(PduHeaders.P_TYPE).text(MIME_SMIL)
                octet(PduHeaders.P_START_DEP).text(smil.contentId ?: "<smil>")
            }
        } else {
            w.contentType(MIME_MULTIPART_MIXED)
        }
        writeBody(w, if (smil != null) listOf(smil) + parts.filter { it !== smil } else parts)
        return w.bytes()
    }

    private fun writeBody(w: PduWriter, parts: List<MmsPart>) {
        w.uintvar(parts.size.toLong())
        for (p in parts) {
            val h = PduWriter()
            h.contentType(p.mime) {
                if (p.isText || p.isSmil) octet(PduHeaders.P_CHARSET).integer(PduCharset.UTF8.toLong())
                if (p.name != null) octet(PduHeaders.P_NAME_DEP).text(p.name)
            }
            p.contentId?.let { h.octet(PduHeaders.PART_CONTENT_ID).quoted(it) }
            p.contentLocation?.let { h.octet(PduHeaders.PART_CONTENT_LOCATION).text(it) }
            val hb = h.bytes()
            w.uintvar(hb.size.toLong())
            w.uintvar(p.data.size.toLong())
            w.raw(hb)
            w.raw(p.data)
        }
    }

    fun notifyRespInd(transactionId: String, status: Int = PduHeaders.STATUS_RETRIEVED): ByteArray =
        PduWriter()
            .octet(PduHeaders.MESSAGE_TYPE).octet(PduType.NOTIFYRESP_IND)
            .octet(PduHeaders.TRANSACTION_ID).text(transactionId)
            .octet(PduHeaders.MMS_VERSION).octet(PduHeaders.MMS_VERSION_1_2)
            .octet(PduHeaders.STATUS).octet(status)
            .octet(PduHeaders.REPORT_ALLOWED).octet(PduHeaders.VALUE_NO)
            .bytes()

    fun acknowledgeInd(transactionId: String): ByteArray =
        PduWriter()
            .octet(PduHeaders.MESSAGE_TYPE).octet(PduType.ACKNOWLEDGE_IND)
            .octet(PduHeaders.TRANSACTION_ID).text(transactionId)
            .octet(PduHeaders.MMS_VERSION).octet(PduHeaders.MMS_VERSION_1_2)
            .octet(PduHeaders.REPORT_ALLOWED).octet(PduHeaders.VALUE_NO)
            .bytes()

    /** A minimal SMIL layout: one slide per image, text on the first slide. */
    fun smil(parts: List<MmsPart>): MmsPart {
        val sb = StringBuilder()
        sb.append("<smil><head><layout><root-layout/>")
        sb.append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        sb.append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
        sb.append("</layout></head><body>")
        val media = parts.filter { !it.isText && !it.isSmil }
        val text = parts.firstOrNull { it.isText }
        val slides = maxOf(media.size, 1)
        for (i in 0 until slides) {
            sb.append("<par dur=\"5000ms\">")
            media.getOrNull(i)?.let { m ->
                val tag = when {
                    m.mime.startsWith("image/") -> "img"
                    m.mime.startsWith("video/") -> "video"
                    m.mime.startsWith("audio/") -> "audio"
                    else -> "ref"
                }
                sb.append("<$tag src=\"${m.contentLocation}\" region=\"Image\"/>")
            }
            if (i == 0 && text != null) sb.append("<text src=\"${text.contentLocation}\" region=\"Text\"/>")
            sb.append("</par>")
        }
        sb.append("</body></smil>")
        return MmsPart(MIME_SMIL, sb.toString().toByteArray(), contentId = "<smil>", contentLocation = "smil.xml", charset = PduCharset.UTF8)
    }
}
