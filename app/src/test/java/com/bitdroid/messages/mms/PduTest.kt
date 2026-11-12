package com.bitdroid.messages.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PduTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII) + 0

    @Test
    fun uintvar() {
        assertArrayEquals(bytes(0x00), PduWriter.encodeUintvar(0))
        assertArrayEquals(bytes(0x7F), PduWriter.encodeUintvar(127))
        assertArrayEquals(bytes(0x81, 0x00), PduWriter.encodeUintvar(128))
        assertArrayEquals(bytes(0x83, 0xFF, 0x7F), PduWriter.encodeUintvar(65535))
        assertEquals(65535L, PduReader(bytes(0x83, 0xFF, 0x7F)).uintvar())
    }

    @Test
    fun parsesNotificationInd() {
        val pdu = bytes(0x8C, 0x82) +
            bytes(0x98) + ascii("tx-123") +
            bytes(0x8D, 0x92) +
            bytes(0x89, 1 + 23) + bytes(0x80) + ascii("+15551234567/TYPE=PLMN") +
            bytes(0x8A, 0x80) +
            bytes(0x8E, 0x02, 0x10, 0x00) +
            bytes(0x88, 0x05, 0x81, 0x03, 0x09, 0x3A, 0x80) +
            bytes(0x83) + ascii("http://mmsc.example/abc")
        val p = MmsPduParser.parse(pdu)
        assertEquals(PduType.NOTIFICATION_IND, p.messageType)
        assertEquals("tx-123", p.transactionId)
        assertEquals("+15551234567/TYPE=PLMN", p.from)
        assertEquals(4096L, p.messageSize)
        assertEquals("http://mmsc.example/abc", p.contentLocation)
        assertTrue(p.expiry > 0)
    }

    @Test
    fun sendReqRoundTrip() {
        val image = ByteArray(5000) { (it * 7).toByte() }
        val text = "Hey 👋 here's the pic — café"
        val parts = arrayListOf(
            MmsPart("image/jpeg", image, "<image0>", "image0.jpg", "image0.jpg"),
            MmsPart(MIME_TEXT, text.toByteArray(), "<text0>", "text0.txt", charset = PduCharset.UTF8),
        )
        parts.add(0, MmsPduComposer.smil(parts))
        val wire = MmsPduComposer.sendReq("T1", listOf("+15551234567", "(555) 765-4321"), "Sübject", parts, true)

        val p = MmsPduParser.parse(wire)
        assertEquals(PduType.SEND_REQ, p.messageType)
        assertEquals("T1", p.transactionId)
        assertEquals(listOf("+15551234567/TYPE=PLMN", "5557654321/TYPE=PLMN"), p.to)
        assertEquals("Sübject", p.subject)
        assertEquals(MIME_MULTIPART_RELATED, p.contentType)
        assertEquals(3, p.parts.size)
        assertEquals(MIME_SMIL, p.parts[0].mime)
        assertEquals("<smil>", p.parts[0].contentId)
        assertEquals("image/jpeg", p.parts[1].mime)
        assertArrayEquals(image, p.parts[1].data)
        assertEquals("image0.jpg", p.parts[1].name)
        assertEquals("image0.jpg", p.parts[1].contentLocation)
        assertEquals(MIME_TEXT, p.parts[2].mime)
        assertEquals(PduCharset.UTF8, p.parts[2].charset)
        assertEquals(text, p.parts[2].text())
    }

    @Test
    fun parsesSendConf() {
        val pdu = bytes(0x8C, 0x81, 0x98) + ascii("T1") + bytes(0x8D, 0x92, 0x92, 0x80, 0x8B) + ascii("msg-42")
        val p = MmsPduParser.parse(pdu)
        assertEquals(PduType.SEND_CONF, p.messageType)
        assertEquals(PduHeaders.RESPONSE_OK, p.responseStatus)
        assertEquals("msg-42", p.messageId)
    }

    @Test
    fun parsesRetrieveConfWithTextualContentTypesAndCc() {
        // multipart/mixed given as well-known short int, a text part with a textual
        // content-type and an application header, plus To + Cc for a group thread.
        val textData = "group hi".toByteArray()
        val partHeaders = bytes(0x83) + bytes(0xC0, 0x22) + ascii("<t>") + ascii("X-Custom") + ascii("1")
        val body = bytes(0x01) +
            PduWriter.encodeUintvar(partHeaders.size.toLong()) +
            PduWriter.encodeUintvar(textData.size.toLong()) +
            partHeaders + textData
        val pdu = bytes(0x8C, 0x84, 0x8D, 0x92) +
            bytes(0x89, 0x0E, 0x80) + ascii("+15550000001") +
            bytes(0x97) + ascii("+15550000002/TYPE=PLMN") +
            bytes(0x82) + ascii("+15550000003/TYPE=PLMN") +
            bytes(0x85, 0x04, 0x65, 0x00, 0x00, 0x00) +
            bytes(0x84, 0xA3) + body
        val p = MmsPduParser.parse(pdu)
        assertEquals(PduType.RETRIEVE_CONF, p.messageType)
        assertEquals("+15550000001", p.from)
        assertEquals(listOf("+15550000002/TYPE=PLMN"), p.to)
        assertEquals(listOf("+15550000003/TYPE=PLMN"), p.cc)
        assertEquals(MIME_MULTIPART_MIXED, p.contentType)
        assertEquals(1, p.parts.size)
        assertEquals("<t>", p.parts[0].contentId)
        assertEquals("group hi", p.parts[0].text())
    }

    @Test
    fun longValueLengthForBigParams() {
        val longName = "x".repeat(60) + ".jpg"
        val w = PduWriter().contentType("image/jpeg") { octet(PduHeaders.P_NAME_DEP).text(longName) }.bytes()
        assertEquals(31, w[0].toInt())
        val ct = PduReader(w).contentType()
        assertEquals("image/jpeg", ct.mime)
        assertEquals(longName, ct.params["name"])
    }
}
