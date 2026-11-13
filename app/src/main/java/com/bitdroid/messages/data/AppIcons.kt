package com.bitdroid.messages.data

import android.content.Context
import android.content.pm.PackageManager

/** Launcher icon choices; each is an activity-alias in the manifest. */
object AppIcons {
    data class Choice(val key: String, val label: String, val alias: String, val bg: Long, val fgDark: Boolean, val art: Int? = null)

    val all = listOf(
        Choice("you", "BUBBLES", ".IconYou", 0xFFDCD4F6, true, com.bitdroid.messages.R.drawable.ic_launcher_foreground_you),
        Choice("white", "WHITE", ".IconWhite", 0xFFFFFFFF, true),
        Choice("black", "AMOLED", ".IconBlack", 0xFF000000, false),
        Choice("blue", "BLUE", ".IconBlue", 0xFFA8C7FA, true),
        Choice("lilac", "LILAC", ".IconLilac", 0xFFEADDFF, true),
    )

    fun current(context: Context): String = Prefs.get(context).getString("app_icon", "you") ?: "you"

    fun set(context: Context, key: String) {
        val pm = context.packageManager
        val chosen = all.firstOrNull { it.key == key } ?: return
        // Enable the new one first so the app never has zero launcher entries.
        for (c in listOf(chosen) + all.filter { it != chosen }) {
            val cn = android.content.ComponentName(context, "com.bitdroid.messages" + c.alias)
            pm.setComponentEnabledSetting(
                cn,
                if (c == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Prefs.get(context).edit().putString("app_icon", key).apply()
    }
}
