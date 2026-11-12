package com.bitdroid.messages.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** MMS message types (X-Mms-Message-Type values). */
object PduType {
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86
    const val READ_REC_IND = 0x87
    const val READ_ORIG_IND = 0x88
}

/**
 * Header field codes with the high bit set, as they appear on the wire.
 * FROM/TO/CC/BCC double as the address "type" column in content://mms/#/addr.
 */
object PduHeaders {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val DELIVERY_TIME = 0x87
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPORT = 0x90
    const val REPORT_ALLOWED = 0x91
    const val RESPONSE_STATUS = 0x92
    const val RESPONSE_TEXT = 0x93
    const val SENDER_VISIBILITY = 0x94
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98
    const val RETRIEVE_STATUS = 0x99
    const val RETRIEVE_TEXT = 0x9A

    const val VALUE_YES = 0x80
    const val VALUE_NO = 0x81
    const val MMS_VERSION_1_2 = 0x92 // 1.2 as short-integer
    const val CLASS_PERSONAL = 0x80
    const val PRIORITY_NORMAL = 0x81
    const val RESPONSE_OK = 0x80
    const val STATUS_RETRIEVED = 0x81
    const val STATUS_DEFERRED = 0x83
    const val INSERT_ADDRESS_TOKEN = 0x81
    const val ADDRESS_PRESENT_TOKEN = 0x80
    const val RELATIVE_TOKEN = 0x81

    // Multipart entry headers
    const val PART_CONTENT_LOCATION = 0x8E
    const val PART_CONTENT_ID = 0xC0
    const val PART_CONTENT_DISPOSITION = 0xAE

    // Content-type parameters (with high bit)
    const val P_CHARSET = 0x81
    const val P_TYPE_V11 = 0x83
    const val P_NAME_DEP = 0x85
    const val P_FILENAME_DEP = 0x86
    const val P_TYPE = 0x89
    const val P_START_DEP = 0x8A
    const val P_NAME = 0x97
    const val P_FILENAME = 0x98
    const val P_START = 0x99
}

object PduCharset {
    const val ANY = 0
    const val US_ASCII = 3
    const val ISO_8859_1 = 4
    const val SHIFT_JIS = 17
    const val UTF8 = 106
    const val GBK = 113
    const val UCS2 = 1000
    const val UTF16 = 1015
    const val GB2312 = 2025
    const val BIG5 = 2026

    fun name(code: Int): String = when (code) {
        US_ASCII -> "US-ASCII"
        ISO_8859_1 -> "ISO-8859-1"
        SHIFT_JIS -> "Shift_JIS"
        GBK -> "GBK"
        UCS2 -> "UTF-16BE"
        UTF16 -> "UTF-16"
        GB2312 -> "GB2312"
        BIG5 -> "Big5"
        else -> "UTF-8"
    }

    fun decode(bytes: ByteArray, code: Int): String = try {
        String(bytes, Charset.forName(name(code)))
    } catch (_: Exception) {
        String(bytes, Charsets.UTF_8)
    }
}

/** WSP well-known content types (WAP-230 table 40), index = code. */
internal val WELL_KNOWN_MIME = arrayOf(
    "*/*", "text/*", "text/html", "text/plain", "text/x-hdml", "text/x-ttml", "text/x-vCalendar",
    "text/x-vCard", "text/vnd.wap.wml", "text/vnd.wap.wmlscript", "text/vnd.wap.wta-event",
    "multipart/*", "multipart/mixed", "multipart/form-data", "multipart/byterantes", "multipart/alternative",
    "application/*", "application/java-vm", "application/x-www-form-urlencoded", "application/x-hdmlc",
    "application/vnd.wap.wmlc", "application/vnd.wap.wmlscriptc", "application/vnd.wap.wta-eventc",
    "application/vnd.wap.uaprof", "application/vnd.wap.wtls-ca-certificate",
    "application/vnd.wap.wtls-user-certificate", "application/x-x509-ca-cert", "application/x-x509-user-cert",
    "image/*", "image/gif", "image/jpeg", "image/tiff", "image/png", "image/vnd.wap.wbmp",
    "application/vnd.wap.multipart.*", "application/vnd.wap.multipart.mixed",
    "application/vnd.wap.multipart.form-data", "application/vnd.wap.multipart.byteranges",
    "application/vnd.wap.multipart.alternative", "application/xml", "text/xml", "application/vnd.wap.wbxml",
    "application/x-x968-cross-cert", "application/x-x968-ca-cert", "application/x-x968-user-cert",
    "text/vnd.wap.si", "application/vnd.wap.sic", "text/vnd.wap.sl", "application/vnd.wap.slc",
    "text/vnd.wap.co", "application/vnd.wap.coc", "application/vnd.wap.multipart.related",
    "application/vnd.wap.sia", "text/vnd.wap.connectivity-xml", "application/vnd.wap.connectivity-wbxml",
    "application/pkcs7-mime", "application/vnd.wap.hashed-certificate", "application/vnd.wap.signed-certificate",
    "application/vnd.wap.cert-response", "application/xhtml+xml", "application/wml+xml", "text/css",
    "application/vnd.wap.mms-message",
)

const val MIME_MULTIPART_RELATED = "application/vnd.wap.multipart.related"
const val MIME_MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"
const val MIME_SMIL = "application/smil"
const val MIME_TEXT = "text/plain"

// ============================================================================ writer

class PduWriter {
    private val out = ByteArrayOutputStream()

    fun bytes(): ByteArray = out.toByteArray()
    val size: Int get() = out.size()

    fun octet(v: Int) = apply { out.write(v and 0xFF) }
    fun raw(b: ByteArray) = apply { out.write(b, 0, b.size) }

    fun shortInt(v: Int) = octet((v and 0x7F) or 0x80)

    fun longInt(v: Long) = apply {
        var n = v
        val tmp = ArrayList<Int>()
        do {
            tmp.add((n and 0xFF).toInt())
            n = n ushr 8
        } while (n != 0L)
        octet(tmp.size)
        for (i in tmp.indices.reversed()) octet(tmp[i])
    }

    /** Integer-Value: short-integer if it fits, otherwise long-integer. */
    fun integer(v: Long) = if (v in 0..127) shortInt(v.toInt()) else longInt(v)

    fun uintvar(v: Long) = apply { raw(encodeUintvar(v)) }

    fun valueLength(len: Int) = apply {
        if (len < 31) octet(len) else {
            octet(31)
            uintvar(len.toLong())
        }
    }

    fun text(s: String) = apply {
        val b = s.toByteArray(Charsets.UTF_8)
        if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) octet(0x7F)
        raw(b)
        octet(0)
    }

    fun quoted(s: String) = apply {
        octet(0x22)
        raw(s.toByteArray(Charsets.UTF_8))
        octet(0)
    }

    /** Encoded-string-value: plain text if ASCII, otherwise tagged as UTF-8. */
    fun encodedString(s: String) = apply {
        if (s.all { it.code in 0x20..0x7E }) {
            text(s)
        } else {
            val inner = PduWriter().shortInt(PduCharset.UTF8).text(s).bytes()
            valueLength(inner.size)
            raw(inner)
        }
    }

    /** Content-type-value in general form with optional parameters. */
    fun contentType(mime: String, params: PduWriter.() -> Unit = {}) = apply {
        val inner = PduWriter()
        val code = WELL_KNOWN_MIME.indexOf(mime)
        if (code >= 0) inner.shortInt(code) else inner.text(mime)
        inner.params()
        val b = inner.bytes()
        valueLength(b.size)
        raw(b)
    }

    companion object {
        fun encodeUintvar(value: Long): ByteArray {
            var v = value
            val tmp = ArrayList<Int>()
            tmp.add((v and 0x7F).toInt())
            v = v ushr 7
            while (v != 0L) {
                tmp.add(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            return ByteArray(tmp.size) { i -> tmp[tmp.size - 1 - i].toByte() }
        }
    }
}

// ============================================================================ reader

class PduFormatException(msg: String) : Exception(msg)

class PduReader(private val data: ByteArray, var pos: Int = 0, private val limit: Int = data.size) {
    val remaining: Int get() = limit - pos
    fun hasMore() = pos < limit

    fun peek(): Int {
        if (pos >= limit) throw PduFormatException("unexpected end")
        return data[pos].toInt() and 0xFF
    }

    fun octet(): Int {
        val v = peek()
        pos++
        return v
    }

    fun bytes(n: Int): ByteArray {
        if (n < 0 || pos + n > limit) throw PduFormatException("bad length $n")
        val b = data.copyOfRange(pos, pos + n)
        pos += n
        return b
    }

    fun uintvar(): Long {
        var v = 0L
        var count = 0
        while (true) {
            val b = octet()
            v = (v shl 7) or (b and 0x7F).toLong()
            if (b and 0x80 == 0) return v
            if (++count > 5) throw PduFormatException("uintvar too long")
        }
    }

    fun valueLength(): Int {
        val b = octet()
        return when {
            b < 31 -> b
            b == 31 -> uintvar().toInt()
            else -> throw PduFormatException("not a value-length: $b")
        }
    }

    fun textBytes(): ByteArray {
        var start = pos
        if (start < limit && ((data[start].toInt() and 0xFF) == 0x7F || (data[start].toInt() and 0xFF) == 0x22)) start++
        var end = start
        while (end < limit && data[end].toInt() != 0) end++
        val b = data.copyOfRange(start, end)
        pos = if (end < limit) end + 1 else limit
        return b
    }

    fun text(): String = String(textBytes(), Charsets.UTF_8)

    fun longInt(): Long {
        val len = octet()
        if (len > 8) throw PduFormatException("long-integer too long")
        var v = 0L
        repeat(len) { v = (v shl 8) or octet().toLong() }
        return v
    }

    fun integer(): Long = if (peek() >= 0x80) (octet() and 0x7F).toLong() else longInt()

    fun encodedString(): String {
        val b = peek()
        if (b <= 31) {
            val len = valueLength()
            val end = pos + len
            var charset = PduCharset.UTF8
            if (pos < end) {
                charset = if (peek() == 0x80) { octet(); PduCharset.UTF8 } else integer().toInt()
            }
            val sub = PduReader(data, pos, minOf(end, limit))
            val s = PduCharset.decode(sub.textBytes(), charset)
            pos = end
            return s
        }
        return PduCharset.decode(textBytes(), PduCharset.UTF8)
    }

    /** Skips any WSP field value using the generic first-octet rules. */
    fun skipValue() {
        val b = peek()
        when {
            b <= 30 -> { octet(); pos += b }
            b == 31 -> { octet(); pos += uintvar().toInt() }
            b in 32..127 -> textBytes()
            else -> octet()
        }
        if (pos > limit) pos = limit
    }

    data class ContentType(val mime: String, val params: Map<String, String>)

    fun contentType(): ContentType {
        val b = peek()
        if (b >= 0x80) return ContentType(wellKnown(octet() and 0x7F), emptyMap())
        if (b > 31) return ContentType(text().lowercase(), emptyMap())
        val len = valueLength()
        val end = pos + len
        val mime = when {
            peek() >= 0x80 -> wellKnown(octet() and 0x7F)
            peek() > 31 -> text().lowercase()
            else -> wellKnown(integer().toInt())
        }
        val params = HashMap<String, String>()
        while (pos < end) {
            try {
                readParam(params)
            } catch (_: Exception) {
                break
            }
        }
        pos = end
        return ContentType(mime, params)
    }

    private fun readParam(params: MutableMap<String, String>) {
        val b = peek()
        if (b >= 0x80) {
            when (octet()) {
                PduHeaders.P_CHARSET -> {
                    params["charset"] = if (peek() == 0x80) { octet(); "0" } else integer().toString()
                }
                PduHeaders.P_TYPE, PduHeaders.P_TYPE_V11 -> {
                    params["type"] = if (peek() >= 0x80) wellKnown(octet() and 0x7F)
                    else if (peek() <= 30) wellKnown(integer().toInt()) else text()
                }
                PduHeaders.P_START, PduHeaders.P_START_DEP -> params["start"] = text()
                PduHeaders.P_NAME, PduHeaders.P_NAME_DEP -> params["name"] = text()
                PduHeaders.P_FILENAME, PduHeaders.P_FILENAME_DEP -> params["filename"] = text()
                else -> skipValue()
            }
        } else {
            val name = text().lowercase()
            val v = peek()
            if (v in 32..127) params[name] = text() else if (v >= 0x80 || v <= 30) params[name] = integer().toString() else skipValue()
        }
    }

    companion object {
        fun wellKnown(code: Int): String = WELL_KNOWN_MIME.getOrNull(code) ?: "application/octet-stream"
    }
}
