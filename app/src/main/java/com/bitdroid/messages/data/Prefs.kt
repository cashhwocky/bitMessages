package com.bitdroid.messages.data

import android.content.Context
import android.content.SharedPreferences

/**
 * All preferences live in device-protected storage so receivers can use them
 * before the first unlock after a reboot.
 */
object Prefs {
    private const val FILE = "bitmessages"

    fun get(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun threadSim(context: Context, threadId: Long): Int =
        get(context).getInt("sim_$threadId", -1)

    fun setThreadSim(context: Context, threadId: Long, subId: Int) {
        get(context).edit().putInt("sim_$threadId", subId).apply()
    }

    fun deliveryReports(context: Context): Boolean =
        get(context).getBoolean("delivery_reports", true)

    fun setDeliveryReports(context: Context, on: Boolean) {
        get(context).edit().putBoolean("delivery_reports", on).apply()
    }

    /** Group texts go as MMS so everyone sees replies (like every modern messaging app). */
    fun groupMms(context: Context): Boolean =
        get(context).getBoolean("group_mms", true)

    fun setGroupMms(context: Context, on: Boolean) {
        get(context).edit().putBoolean("group_mms", on).apply()
    }

    fun nextId(context: Context): Int {
        val p = get(context)
        synchronized(this) {
            val n = p.getInt("next_id", 1000) + 1
            p.edit().putInt("next_id", if (n > 1_000_000_000) 1000 else n).commit()
            return n
        }
    }
}
