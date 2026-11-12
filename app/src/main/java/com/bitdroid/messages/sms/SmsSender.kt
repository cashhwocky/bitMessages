package com.bitdroid.messages.sms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms
import com.bitdroid.messages.data.Prefs
import com.bitdroid.messages.data.Sims
import com.bitdroid.messages.data.Store

object SmsSender {
    const val ACTION_SENT = "com.bitdroid.messages.SMS_SENT"
    const val ACTION_DELIVERED = "com.bitdroid.messages.SMS_DELIVERED"
    const val EXTRA_ID = "id"

    /** Stores one outgoing SMS in the outbox and sends it. Returns the row id. */
    fun send(context: Context, address: String, body: String, threadId: Long, subId: Int): Long {
        val wantDelivery = Prefs.deliveryReports(context)
        val uri = Store.insertOutgoingSms(context, address, body, threadId, subId, wantDelivery) ?: return -1
        val id = ContentUris.parseId(uri)
        transmit(context, id, address, body, subId, wantDelivery)
        return id
    }

    fun resend(context: Context, id: Long, subIdOverride: Int? = null) {
        val row = Store.smsRow(context, id) ?: return
        val subId = subIdOverride ?: row.subId
        val wantDelivery = Prefs.deliveryReports(context)
        Store.updateSms(context, id, ContentValues().apply {
            put(Sms.TYPE, Sms.MESSAGE_TYPE_OUTBOX)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.STATUS, if (wantDelivery) Sms.STATUS_PENDING else Sms.STATUS_NONE)
            put(Sms.ERROR_CODE, 0)
            put(Sms.SUBSCRIPTION_ID, subId)
        })
        transmit(context, id, row.address, row.body, subId, wantDelivery)
    }

    private fun transmit(context: Context, id: Long, address: String, body: String, subId: Int, wantDelivery: Boolean) {
        val sms = Sims.smsManager(context, subId)
        val parts = sms.divideMessage(body).ifEmpty { arrayListOf(body) }
        Prefs.get(context).edit()
            .putInt("sms_left_$id", parts.size)
            .remove("sms_fail_$id")
            .commit()
        val sent = ArrayList<PendingIntent>()
        val delivered = ArrayList<PendingIntent?>()
        for (i in parts.indices) {
            sent += PendingIntent.getBroadcast(
                context, Prefs.nextId(context),
                Intent(context, SmsStatusReceiver::class.java).apply {
                    action = ACTION_SENT
                    putExtra(EXTRA_ID, id)
                },
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
            // Only the last part asks for a delivery report.
            delivered += if (wantDelivery && i == parts.lastIndex) {
                PendingIntent.getBroadcast(
                    context, Prefs.nextId(context),
                    Intent(context, SmsStatusReceiver::class.java).apply {
                        action = ACTION_DELIVERED
                        putExtra(EXTRA_ID, id)
                    },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
                )
            } else {
                null
            }
        }
        try {
            if (parts.size == 1) {
                sms.sendTextMessage(address, null, parts[0], sent[0], delivered[0])
            } else {
                sms.sendMultipartTextMessage(address, null, parts, sent, delivered)
            }
        } catch (e: Exception) {
            android.util.Log.e("bitMessages", "send failed", e)
            Store.updateSms(context, id, ContentValues().apply { put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED) })
        }
    }
}
