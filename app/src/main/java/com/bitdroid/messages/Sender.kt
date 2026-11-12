package com.bitdroid.messages

import android.content.Context
import android.net.Uri
import com.bitdroid.messages.data.Message
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.Prefs
import com.bitdroid.messages.data.Sims
import com.bitdroid.messages.data.Store
import com.bitdroid.messages.mms.MmsSender
import com.bitdroid.messages.sms.SmsSender
import java.net.URLDecoder

/** Decides SMS vs MMS and which SIM to use. Call off the main thread. */
object Sender {

    /** Parses sms:/smsto:/mms:/mmsto: URIs, e.g. "smsto:+1555,+1666?body=hi". */
    fun parseRecipients(uri: Uri): List<String> {
        val ssp = uri.schemeSpecificPart ?: return emptyList()
        return ssp.substringBefore('?')
            .split(',', ';')
            .map { Phone.clean(URLDecoder.decode(it, "UTF-8")) }
            .filter { it.isNotBlank() }
    }

    fun parseBody(uri: Uri): String? {
        val q = uri.schemeSpecificPart?.substringAfter('?', "") ?: return null
        return q.split('&').firstOrNull { it.startsWith("body=") }?.substringAfter("body=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }

    fun sendTo(context: Context, recipients: List<String>, text: String, images: List<Uri>, subId: Int?): Long {
        val threadId = Store.threadIdFor(context, recipients)
        sendToThread(context, threadId, recipients, text, images, subId)
        return threadId
    }

    fun sendToThread(context: Context, threadId: Long, recipientsHint: List<String>?, text: String, images: List<Uri>, subId: Int?) {
        val recipients = recipientsHint?.takeIf { it.isNotEmpty() } ?: Store.threadAddresses(context, threadId)
        if (recipients.isEmpty() || (text.isBlank() && images.isEmpty())) return
        val sub = subId ?: Sims.resolve(context, threadId, Store.lastIncomingSub(context, threadId))
        val group = recipients.size > 1
        val email = recipients.any { Phone.isEmail(it) }
        when {
            images.isNotEmpty() || email || (group && Prefs.groupMms(context)) ->
                MmsSender.send(context, threadId, recipients, text, images, sub)
            group -> recipients.forEach { r ->
                SmsSender.send(context, r, text, Store.threadIdFor(context, listOf(r)), sub)
            }
            else -> SmsSender.send(context, recipients[0], text, threadId, sub)
        }
    }

    fun retry(context: Context, m: Message, subId: Int?) {
        if (m.isMms) MmsSender.resend(context, m.id, subId) else SmsSender.resend(context, m.id, subId)
    }
}
