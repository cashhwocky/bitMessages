package com.bitdroid.messages.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony

/**
 * RCS (beta). Android gives RCS only to Google Messages, so bitMessages can't speak RCS itself.
 * "RCS mode" hands the default-texting role to Google Messages (which signs in to Google's RCS
 * service) and hands it back when turned off. Messages live in the shared system store either way.
 */
object Rcs {
    const val GOOGLE_MESSAGES = "com.google.android.apps.messaging"
    const val CARRIER_SERVICES = "com.google.android.ims"
    const val PLAY_STORE = "com.android.vending"

    fun enabled(context: Context) = Prefs.get(context).getBoolean("rcs_mode", false)
    fun setEnabled(context: Context, on: Boolean) = Prefs.get(context).edit().putBoolean("rcs_mode", on).apply()

    fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun googleMessagesIsDefault(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == GOOGLE_MESSAGES

    fun openGoogleMessages(context: Context): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(GOOGLE_MESSAGES) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openStore(context: Context, pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openPlayStore(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PLAY_STORE)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openAppInfo(context: Context, pkg: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openMobileNetwork(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Launcher icon choices; each is an activity-alias in the manifest. */
object AppIcons {
    data class Choice(val key: String, val label: String, val alias: String, val bg: Long, val fgDark: Boolean)

    val all = listOf(
        Choice("white", "WHITE", ".IconWhite", 0xFFFFFFFF, true),
        Choice("black", "AMOLED", ".IconBlack", 0xFF000000, false),
        Choice("blue", "BLUE", ".IconBlue", 0xFFA8C7FA, true),
        Choice("lilac", "LILAC", ".IconLilac", 0xFFEADDFF, true),
    )

    fun current(context: Context): String = Prefs.get(context).getString("app_icon", "white") ?: "white"

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
