package com.bitdroid.messages.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

object Sims {
    @SuppressLint("MissingPermission")
    fun active(context: Context): List<SimInfo> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return try {
            sm.activeSubscriptionInfoList.orEmpty()
                .sortedBy { it.simSlotIndex }
                .map { info ->
                    val number = try {
                        sm.getPhoneNumber(info.subscriptionId).ifBlank { null }
                    } catch (_: Exception) {
                        null
                    }
                    SimInfo(
                        subId = info.subscriptionId,
                        slotIndex = info.simSlotIndex,
                        label = info.displayName?.toString()?.ifBlank { null }
                            ?: info.carrierName?.toString()
                            ?: "SIM ${info.simSlotIndex + 1}",
                        number = number,
                        color = info.iconTint,
                    )
                }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun defaultSmsSubId(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()

    /** Our own numbers, used to drop ourselves out of group MMS recipient lists. */
    fun ownNumbers(context: Context): List<String> = active(context).mapNotNull { it.number }

    fun smsManager(context: Context, subId: Int): SmsManager {
        val base = context.getSystemService(SmsManager::class.java)
        return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subId >= 0) {
            base.createForSubscriptionId(subId)
        } else {
            base
        }
    }

    /** Pick the SIM for a thread: user choice > SIM of last incoming message > system default. */
    fun resolve(context: Context, threadId: Long, lastIncomingSub: Int?): Int {
        val active = active(context).map { it.subId }
        val chosen = Prefs.threadSim(context, threadId)
        if (chosen in active) return chosen
        if (lastIncomingSub != null && lastIncomingSub in active) return lastIncomingSub
        val def = defaultSmsSubId()
        if (def in active) return def
        return active.firstOrNull() ?: def
    }
}
