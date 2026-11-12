package com.bitdroid.messages.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/** Uses the system block list (shared with the dialer), which the default SMS app may read and write. */
object Blocked {
    fun canBlock(context: Context): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    } catch (_: Exception) {
        false
    }

    fun isBlocked(context: Context, address: String): Boolean = try {
        BlockedNumberContract.isBlocked(context, Phone.clean(address))
    } catch (_: Exception) {
        false
    }

    fun block(context: Context, address: String) {
        try {
            val v = ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, Phone.clean(address)) }
            context.contentResolver.insert(BlockedNumbers.CONTENT_URI, v)
        } catch (_: Exception) {
        }
    }

    fun unblock(context: Context, address: String) {
        try {
            BlockedNumberContract.unblock(context, Phone.clean(address))
        } catch (_: Exception) {
        }
    }

    fun list(context: Context): List<String> {
        val out = ArrayList<String>()
        try {
            context.contentResolver.query(
                BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(out::add) }
        } catch (_: Exception) {
        }
        return out
    }
}
