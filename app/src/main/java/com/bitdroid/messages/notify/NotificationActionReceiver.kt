package com.bitdroid.messages.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.bitdroid.messages.MainActivity
import com.bitdroid.messages.Sender
import com.bitdroid.messages.data.Store
import com.bitdroid.messages.runAsync

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MainActivity.EXTRA_THREAD_ID, -1)
        if (threadId < 0) return
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()
                if (text.isNullOrBlank()) return
                runAsync {
                    Sender.sendToThread(context, threadId, null, text, emptyList(), null)
                    Store.markThreadRead(context, threadId)
                    Notifier.cancel(context, threadId)
                }
            }
            ACTION_MARK_READ -> runAsync {
                Store.markThreadRead(context, threadId)
                Notifier.cancel(context, threadId)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.bitdroid.messages.REPLY"
        const val ACTION_MARK_READ = "com.bitdroid.messages.MARK_READ"
    }
}
