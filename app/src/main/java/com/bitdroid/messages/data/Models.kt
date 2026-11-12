package com.bitdroid.messages.data

import android.net.Uri

data class Contact(
    val address: String,
    val name: String?,
    val photoUri: Uri?,
    val lookupUri: Uri?,
) {
    val displayName: String get() = name ?: address
}

data class Conversation(
    val threadId: Long,
    val recipients: List<Contact>,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
    val hasError: Boolean,
) {
    val isGroup: Boolean get() = recipients.size > 1
    val title: String
        get() = when {
            recipients.isEmpty() -> "Unknown"
            recipients.size == 1 -> recipients[0].displayName
            else -> recipients.joinToString(", ") { it.name?.substringBefore(' ') ?: it.address }
        }
}

enum class MessageStatus { RECEIVED, SENDING, SENT, DELIVERED, FAILED, PENDING_DOWNLOAD, DOWNLOADING, DOWNLOAD_FAILED }

data class Attachment(
    val uri: Uri,
    val mimeType: String,
    val name: String?,
)

data class Message(
    /** "s<id>" for SMS, "m<id>" for MMS; unique across both tables. */
    val key: String,
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val status: MessageStatus,
    val subId: Int,
    val attachments: List<Attachment> = emptyList(),
    val subject: String? = null,
)

data class SimInfo(
    val subId: Int,
    val slotIndex: Int,
    val label: String,
    val number: String?,
    val color: Int,
)

data class SearchHit(
    val threadId: Long,
    val title: String,
    val body: String,
    val date: Long,
    val contact: Contact?,
)
