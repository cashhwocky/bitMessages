package com.bitdroid.messages.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Base64
import com.bitdroid.messages.mms.MmsIncoming
import com.bitdroid.messages.notify.Notifier
import com.bitdroid.messages.runAsync
import com.bitdroid.messages.sms.Incoming
import org.json.JSONObject
import java.io.File

/**
 * Texts that arrive before the first unlock can't go into the Telephony provider
 * (it lives in credential-encrypted storage). We keep them in device-protected
 * storage and import them once the user unlocks.
 */
object DirectBootQueue {
    private val lock = Any()

    private fun file(context: Context) =
        File(context.createDeviceProtectedStorageContext().filesDir, "pending_messages.jsonl")

    private fun append(context: Context, obj: JSONObject) = synchronized(lock) {
        file(context).appendText(obj.toString() + "\n")
    }

    fun addSms(context: Context, sms: Incoming.Sms) = append(context, JSONObject().apply {
        put("kind", "sms")
        put("address", sms.address)
        put("body", sms.body)
        put("dateSent", sms.dateSent)
        put("received", sms.received)
        put("subId", sms.subId)
        put("sc", sms.serviceCenter ?: "")
        put("protocol", sms.protocol)
        put("replyPath", sms.replyPath)
    })

    fun addMms(context: Context, pdu: ByteArray, subId: Int) = append(context, JSONObject().apply {
        put("kind", "mms")
        put("pdu", Base64.encodeToString(pdu, Base64.NO_WRAP))
        put("subId", subId)
    })

    /** Imports everything queued. Safe to call any time; does nothing while locked. */
    fun flush(context: Context) {
        val um = context.getSystemService(UserManager::class.java)
        if (um != null && !um.isUserUnlocked) return
        val lines = synchronized(lock) {
            val f = file(context)
            if (!f.exists()) return
            val l = f.readLines()
            f.delete()
            l
        }
        if (lines.isEmpty()) return
        Notifier.clearLocked(context)
        for (line in lines) {
            if (line.isBlank()) continue
            try {
                val o = JSONObject(line)
                when (o.getString("kind")) {
                    "sms" -> Incoming.handleSms(
                        context,
                        Incoming.Sms(
                            address = o.getString("address"),
                            body = o.getString("body"),
                            dateSent = o.getLong("dateSent"),
                            received = o.getLong("received"),
                            subId = o.getInt("subId"),
                            serviceCenter = o.optString("sc").ifBlank { null },
                            protocol = o.optInt("protocol"),
                            replyPath = o.optBoolean("replyPath"),
                        ),
                    )
                    "mms" -> MmsIncoming.handleWapPush(
                        context, Base64.decode(o.getString("pdu"), Base64.NO_WRAP), o.getInt("subId"),
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("bitMessages", "bad queued message", e)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> Notifier.setupBaseChannels(context)
            Intent.ACTION_BOOT_COMPLETED -> runAsync {
                Notifier.setupBaseChannels(context)
                DirectBootQueue.flush(context)
            }
        }
    }
}
