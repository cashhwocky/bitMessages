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

    // ---------------------------------------------------------------- pins

    const val MAX_PINS = 6

    fun pins(context: Context): List<Long> =
        (get(context).getString("pins", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }

    fun setPins(context: Context, ids: List<Long>) {
        get(context).edit().putString("pins", ids.distinct().take(MAX_PINS).joinToString(",")).apply()
    }

    /** Pins [threadId]; returns false when all 6 slots are taken. */
    fun pin(context: Context, threadId: Long): Boolean {
        val cur = pins(context)
        if (threadId in cur) return true
        if (cur.size >= MAX_PINS) return false
        setPins(context, cur + threadId)
        return true
    }

    fun unpin(context: Context, threadId: Long) = setPins(context, pins(context) - threadId)

    // --------------------------------------------------------- swipe actions

    enum class Swipe { NONE, READ, DELETE }

    fun swipeLeft(context: Context): Swipe =
        runCatching { Swipe.valueOf(get(context).getString("swipe_left", "NONE")!!) }.getOrDefault(Swipe.NONE)

    fun swipeRight(context: Context): Swipe =
        runCatching { Swipe.valueOf(get(context).getString("swipe_right", "NONE")!!) }.getOrDefault(Swipe.NONE)

    fun setSwipeLeft(context: Context, s: Swipe) = get(context).edit().putString("swipe_left", s.name).apply()
    fun setSwipeRight(context: Context, s: Swipe) = get(context).edit().putString("swipe_right", s.name).apply()

    // ------------------------------------------------------------- look

    enum class ListStyle { PANEL, CARDS }

    fun listStyle(context: Context): ListStyle =
        runCatching { ListStyle.valueOf(get(context).getString("list_style", "PANEL")!!) }.getOrDefault(ListStyle.PANEL)

    fun setListStyle(context: Context, s: ListStyle) = get(context).edit().putString("list_style", s.name).apply()

    /** Faint Material You (Monet) glow behind the black. Off by default. */
    fun monetBackground(context: Context): Boolean = get(context).getBoolean("monet_bg", false)
    fun setMonetBackground(context: Context, on: Boolean) = get(context).edit().putBoolean("monet_bg", on).apply()

    fun nextId(context: Context): Int {
        val p = get(context)
        synchronized(this) {
            val n = p.getInt("next_id", 1000) + 1
            p.edit().putInt("next_id", if (n > 1_000_000_000) 1000 else n).commit()
            return n
        }
    }
}
