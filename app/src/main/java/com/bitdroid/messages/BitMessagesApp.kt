package com.bitdroid.messages

import android.app.Application
import com.bitdroid.messages.boot.DirectBootQueue
import com.bitdroid.messages.notify.Notifier
import kotlinx.coroutines.launch

class BitMessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.setupBaseChannels(this)
        appScope.launch { DirectBootQueue.flush(this@BitMessagesApp) }
    }
}
