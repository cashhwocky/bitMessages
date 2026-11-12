package com.bitdroid.messages

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide background scope for work that must outlive a screen. */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    appScope.launch {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.e("bitMessages", "receiver failed", t)
        } finally {
            pending.finish()
        }
    }
}
