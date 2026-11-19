package com.bitdroid.messages.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from this app's GitHub releases.
 *
 * CI uploads update.json (versionCode, versionName, apk) next to the release APK. We compare its
 * versionCode with ours, download the APK, and hand it to PackageInstaller. Updates only install
 * when the new APK is signed with the same key as the installed app.
 */
object Updater {
    private const val REPO = "cashhwocky/bitMessages"
    private const val LATEST = "https://github.com/$REPO/releases/latest/download"
    private const val PREFS = "updater"
    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    data class Info(val versionCode: Long, val versionName: String, val apk: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: Info) : State
        data class Downloading(val info: Info, val progress: Float) : State
        data object Installing : State
        data class Error(val message: String, val info: Info? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun installedCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun installedName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Checks at most every few hours; returns the update if one is waiting. */
    suspend fun checkIfDue(context: Context): Info? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getLong("last_check", 0L)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return null
        p.edit().putLong("last_check", System.currentTimeMillis()).apply()
        return check(context)
    }

    suspend fun check(context: Context): Info? {
        _state.value = State.Checking
        return try {
            val info = withContext(Dispatchers.IO) {
                val json = JSONObject(get("$LATEST/update.json").use { it.readBytes().decodeToString() })
                Info(json.getLong("versionCode"), json.optString("versionName"), json.getString("apk"))
            }
            if (info.versionCode > installedCode(context)) {
                _state.value = State.Available(info)
                info
            } else {
                _state.value = State.UpToDate
                null
            }
        } catch (e: Exception) {
            _state.value = State.Error("Couldn't check for updates. Are you online?")
            null
        }
    }

    /** Downloads and installs [info]. Asks for the "install apps" permission first if needed. */
    suspend fun install(context: Context, info: Info) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = State.Error("Allow installing updates for this app, then tap Install again.", info)
            return
        }
        try {
            val file = File(context.cacheDir, "update.apk")
            _state.value = State.Downloading(info, 0f)
            withContext(Dispatchers.IO) {
                val conn = open("$LATEST/${info.apk}")
                val total = conn.contentLengthLong.takeIf { it > 0 }
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total != null) _state.value = State.Downloading(info, done.toFloat() / total)
                        }
                    }
                }
            }
            _state.value = State.Installing
            withContext(Dispatchers.IO) { commit(context, file) }
        } catch (e: Exception) {
            _state.value = State.Error("Download failed. Try again.", info)
        }
    }

    private fun commit(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
    }

    internal fun onResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.UpToDate
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Error("Update cancelled.")
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = State.Error("This update is signed with a different key than the installed app. Install it once with adb, then updates work from here.")
            else -> _state.value = State.Error(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed.")
        }
    }

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        repeat(5) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "bitMessages-updater")
            }
            val code = c.responseCode
            if (code in 300..399) {
                u = URL(u, c.getHeaderField("Location"))
                c.disconnect()
            } else if (code == 200) {
                return c
            } else {
                c.disconnect()
                throw java.io.IOException("HTTP $code")
            }
        }
        throw java.io.IOException("Too many redirects")
    }

    private fun get(url: String) = open(url).inputStream
}

/** Receives PackageInstaller results. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onResult(context, intent)
}
