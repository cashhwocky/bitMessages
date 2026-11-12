package com.bitdroid.messages.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bitdroid.messages.MainActivity
import com.bitdroid.messages.R
import com.bitdroid.messages.data.Contact
import com.bitdroid.messages.data.Contacts
import com.bitdroid.messages.data.Phone
import com.bitdroid.messages.data.Store

object Notifier {
    private const val GROUP_CONVERSATIONS = "conversations"
    private const val CHANNEL_LOCKED = "before_unlock"
    private const val CHANNEL_FAILED = "send_failed"
    const val TAG_LOCKED = "locked"
    const val TAG_FAILED = "failed"
    const val KEY_REPLY = "reply"
    private const val ACCENT = 0xFFA8C7FA.toInt()

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun canPost(context: Context) =
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun setupBaseChannels(context: Context) {
        val nm = nm(context)
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP_CONVERSATIONS, "Conversations"))
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LOCKED, "Messages before unlock", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Texts that arrive after a reboot, before you first unlock"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_FAILED, "Failed to send", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** One channel per conversation, named after the people in it. */
    private fun channelFor(context: Context, threadId: Long, title: String): String {
        val id = "thread_$threadId"
        val nm = nm(context)
        val existing = nm.getNotificationChannel(id)
        if (existing == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, title, NotificationManager.IMPORTANCE_HIGH).apply {
                    group = GROUP_CONVERSATIONS
                    description = "Messages with $title"
                    enableVibration(true)
                },
            )
        } else if (existing.name != title) {
            existing.name = title
            nm.createNotificationChannel(existing)
        }
        return id
    }

    private fun avatar(context: Context, c: Contact): IconCompat? {
        val uri = c.photoUri ?: return null
        return try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.setTargetSize(192, 192)
            }
            IconCompat.createWithAdaptiveBitmap(Bitmap.createScaledBitmap(bmp, 192, 192, true))
        } catch (_: Exception) {
            null
        }
    }

    private fun person(context: Context, c: Contact): Person =
        Person.Builder()
            .setName(c.name ?: Phone.format(context, c.address))
            .setKey(c.address)
            .apply { avatar(context, c)?.let(::setIcon) }
            .apply { c.lookupUri?.let { setUri(it.toString()) } }
            .build()

    private fun openThreadIntent(context: Context, threadId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, threadId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_THREAD
                putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Posts (or clears) the notification for all unread messages in [threadId]. */
    fun notifyThread(context: Context, threadId: Long) {
        if (!canPost(context)) return
        if (MainActivity.visibleThread == threadId) {
            Store.markThreadRead(context, threadId)
            return
        }
        val unread = Store.unreadIn(context, threadId)
        if (unread.isEmpty()) {
            cancel(context, threadId)
            return
        }
        val addresses = Store.threadAddresses(context, threadId)
        val contacts = addresses.map { Contacts.lookup(context, it) }
        val title = when {
            contacts.isEmpty() -> "Message"
            contacts.size == 1 -> contacts[0].name ?: Phone.format(context, contacts[0].address)
            else -> contacts.joinToString(", ") { it.name?.substringBefore(' ') ?: it.address }
        }
        val isGroup = contacts.size > 1
        val channel = channelFor(context, threadId, title)
        val me = Person.Builder().setName("You").setKey("self").build()
        val people = HashMap<String, Person>()
        fun personFor(address: String): Person {
            val c = contacts.firstOrNull { Phone.same(it.address, address) } ?: Contacts.lookup(context, address)
            return people.getOrPut(c.address) { person(context, c) }
        }

        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(if (isGroup) title else null)
            .setGroupConversation(isGroup)
        for (u in unread) {
            val sender = if (u.sender.isBlank()) addresses.firstOrNull() ?: "" else u.sender
            style.addMessage(u.body, u.date, personFor(sender))
        }

        val shortcutId = "thread_$threadId"
        try {
            val shortcutPerson = contacts.firstOrNull()?.let { personFor(it.address) }
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(title)
                .setLongLived(true)
                .setIcon(shortcutPerson?.icon ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_THREAD
                    putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                })
                .apply { if (!isGroup && shortcutPerson != null) setPerson(shortcutPerson) }
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (_: Exception) {
        }

        val replyPi = PendingIntent.getBroadcast(
            context, threadId.toInt(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replyAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyPi)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
        val readPi = PendingIntent.getBroadcast(
            context, threadId.toInt(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_READ
                putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val readAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, "Mark as read", readPi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(ACCENT)
            .setStyle(style)
            .setShortcutId(shortcutId)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openThreadIntent(context, threadId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(unread.last().date)
            .setShowWhen(true)
            .setNumber(unread.size)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(threadId.toInt(), n)
        } catch (_: SecurityException) {
        }
    }

    /** Before first unlock: no contacts/provider access, so show the raw number and text. */
    fun notifyLocked(context: Context, address: String, body: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_LOCKED)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(ACCENT)
            .setContentTitle(address)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TAG_LOCKED, address.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    fun clearLocked(context: Context) {
        nm(context).activeNotifications.filter { it.tag == TAG_LOCKED }.forEach { nm(context).cancel(it.tag, it.id) }
    }

    fun notifyFailed(context: Context, threadId: Long) {
        if (!canPost(context)) return
        val um = context.getSystemService(UserManager::class.java)
        if (um != null && !um.isUserUnlocked) return
        if (MainActivity.visibleThread == threadId) return
        val title = Store.threadAddresses(context, threadId).joinToString(", ") {
            Contacts.lookup(context, it).name ?: Phone.format(context, it)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_FAILED)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(ACCENT)
            .setContentTitle("Message not sent")
            .setContentText("To $title · Tap to retry")
            .setContentIntent(openThreadIntent(context, threadId))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TAG_FAILED, threadId.toInt(), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, threadId: Long) {
        nm(context).cancel(threadId.toInt())
        nm(context).cancel(TAG_FAILED, threadId.toInt())
    }

    fun deleteChannel(context: Context, threadId: Long) {
        cancel(context, threadId)
        try {
            nm(context).deleteNotificationChannel("thread_$threadId")
        } catch (_: Exception) {
        }
    }
}
