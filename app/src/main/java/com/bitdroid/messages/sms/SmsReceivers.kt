package com.bitdroid.messages.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.UserManager
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import com.bitdroid.messages.Sender
import com.bitdroid.messages.appScope
import com.bitdroid.messages.boot.DirectBootQueue
import com.bitdroid.messages.data.Blocked
import com.bitdroid.messages.data.Prefs
import com.bitdroid.messages.data.Store
import com.bitdroid.messages.notify.Notifier
import com.bitdroid.messages.runAsync
import kotlinx.coroutines.launch

/** SMS_DELIVER: incoming text for the default SMS app. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (msgs.isEmpty()) return
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", SubscriptionManager.getDefaultSmsSubscriptionId()),
        )
        val first = msgs[0]
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        val incoming = Incoming.Sms(
            address = address,
            body = body,
            dateSent = first.timestampMillis,
            received = System.currentTimeMillis(),
            subId = subId,
            serviceCenter = first.serviceCenterAddress,
            protocol = first.protocolIdentifier,
            replyPath = first.isReplyPathPresent,
        )
        runAsync { Incoming.handleSms(context, incoming) }
    }
}

object Incoming {
    data class Sms(
        val address: String, val body: String, val dateSent: Long, val received: Long, val subId: Int,
        val serviceCenter: String?, val protocol: Int, val replyPath: Boolean,
    )

    fun handleSms(context: Context, sms: Sms) {
        val um = context.getSystemService(UserManager::class.java)
        if (um != null && !um.isUserUnlocked) {
            DirectBootQueue.addSms(context, sms)
            Notifier.notifyLocked(context, sms.address, sms.body)
            return
        }
        if (Blocked.isBlocked(context, sms.address)) return
        val uri = Store.insertIncomingSms(
            context, sms.address, sms.body, sms.dateSent, sms.received, sms.subId,
            sms.serviceCenter, sms.protocol, sms.replyPath,
        ) ?: return
        val thread = Store.threadOfSms(context, ContentUris.parseId(uri))
        if (thread >= 0) Notifier.notifyThread(context, thread)
    }
}

/** Sent / delivered results for the PendingIntents created by [SmsSender]. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SmsSender.EXTRA_ID, -1)
        if (id < 0) return
        val code = resultCode
        runAsync {
            when (intent.action) {
                SmsSender.ACTION_SENT -> onSent(context, id, code, intent.getIntExtra("errorCode", 0))
                SmsSender.ACTION_DELIVERED -> onDelivered(context, id, intent)
            }
        }
    }

    private fun onSent(context: Context, id: Long, code: Int, errorCode: Int) {
        val prefs = Prefs.get(context)
        val left: Int
        val failed: Boolean
        synchronized(SmsStatusReceiver::class.java) {
            if (code != Activity.RESULT_OK) prefs.edit().putBoolean("sms_fail_$id", true).putInt("sms_err_$id", if (errorCode != 0) errorCode else code).commit()
            left = prefs.getInt("sms_left_$id", 1) - 1
            prefs.edit().putInt("sms_left_$id", left).commit()
            failed = prefs.getBoolean("sms_fail_$id", false)
        }
        if (left > 0) return
        val err = prefs.getInt("sms_err_$id", 0)
        prefs.edit().remove("sms_left_$id").remove("sms_fail_$id").remove("sms_err_$id").apply()
        Store.updateSms(context, id, ContentValues().apply {
            put(Sms.TYPE, if (failed) Sms.MESSAGE_TYPE_FAILED else Sms.MESSAGE_TYPE_SENT)
            if (failed) put(Sms.ERROR_CODE, err)
            if (failed) put(Sms.STATUS, Sms.STATUS_NONE)
        })
        if (failed) {
            val thread = Store.threadOfSms(context, id)
            if (thread >= 0) Notifier.notifyFailed(context, thread)
        }
    }

    private fun onDelivered(context: Context, id: Long, intent: Intent) {
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val format = intent.getStringExtra("format") ?: "3gpp"
        val msg = SmsMessage.createFromPdu(pdu, format) ?: return
        val raw = msg.status
        val status = if (format == "3gpp2") {
            val errorClass = (raw shr 24) and 0x03
            val statusCode = (raw shr 16) and 0x3f
            when (errorClass) {
                0 -> if (statusCode == 0x02) Sms.STATUS_COMPLETE else Sms.STATUS_PENDING
                2 -> Sms.STATUS_PENDING
                else -> Sms.STATUS_FAILED
            }
        } else {
            when {
                raw >= 0x40 -> Sms.STATUS_FAILED
                raw >= 0x20 -> Sms.STATUS_PENDING
                else -> Sms.STATUS_COMPLETE
            }
        }
        Store.updateSms(context, id, ContentValues().apply {
            put(Sms.STATUS, status)
            put(Sms.DATE_SENT, System.currentTimeMillis())
        })
    }
}

/** "Respond via message" from the incoming-call screen. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val data = intent?.data
        if (intent?.action == "android.intent.action.RESPOND_VIA_MESSAGE" && !text.isNullOrBlank() && data != null) {
            val recipients = Sender.parseRecipients(data)
            if (recipients.isNotEmpty()) {
                val ctx = applicationContext
                appScope.launch {
                    try {
                        Sender.sendTo(ctx, recipients, text, emptyList(), null)
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
