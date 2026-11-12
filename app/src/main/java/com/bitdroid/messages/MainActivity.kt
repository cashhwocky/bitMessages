package com.bitdroid.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitdroid.messages.ui.AppViewModel
import com.bitdroid.messages.ui.Bit
import com.bitdroid.messages.ui.BitTheme
import com.bitdroid.messages.ui.BlockedScreen
import com.bitdroid.messages.ui.ConversationListScreen
import com.bitdroid.messages.ui.Draft
import com.bitdroid.messages.ui.NewChatScreen
import com.bitdroid.messages.ui.Screen
import com.bitdroid.messages.ui.SettingsScreen
import com.bitdroid.messages.ui.SetupScreen
import com.bitdroid.messages.ui.ThreadScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** True after a default-SMS request came back refused (e.g. Android's restricted settings). */
    private val roleRefused = androidx.compose.runtime.mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onResume()
        roleRefused.value = !vm.isDefault.value
        // Back as the default texting app means RCS mode is off.
        if (vm.isDefault.value) com.bitdroid.messages.data.Rcs.setEnabled(this, false)
        if (vm.isDefault.value) requestRuntimePermissions()
    }

    private val permsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            BitTheme {
                val isDefault by vm.isDefault.collectAsStateWithLifecycle()
                val monet by vm.monet.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(Bit.Black).monetGlow(monet)) {
                    if (!isDefault) {
                        if (com.bitdroid.messages.data.Rcs.enabled(this@MainActivity)) {
                            com.bitdroid.messages.ui.RcsModeScreen(onTurnOff = ::requestRole)
                        } else {
                            SetupScreen(onSetDefault = ::requestRole, blocked = roleRefused.value)
                        }
                    } else {
                        // Predictive back: the screen shrinks and follows your thumb; let go to go back.
                        var backProgress by remember { mutableFloatStateOf(0f) }
                        var backFromLeft by remember { mutableStateOf(true) }
                        PredictiveBackHandler(enabled = vm.stack.size > 1) { events ->
                            try {
                                events.collect { e ->
                                    backProgress = e.progress
                                    backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                                }
                                backProgress = 0f
                                vm.back()
                            } catch (e: CancellationException) {
                                backProgress = 0f
                                throw e
                            }
                        }
                        val target = vm.stack.last()
                        val depth = vm.stack.size
                        AnimatedContent(
                            modifier = Modifier.graphicsLayer {
                                val p = backProgress
                                val s = 1f - 0.1f * p
                                scaleX = s
                                scaleY = s
                                translationX = (if (backFromLeft) 1f else -1f) * p * 32.dp.toPx()
                                shape = RoundedCornerShape((36 * p).dp)
                                clip = p > 0f
                            },
                            targetState = target to depth,
                            transitionSpec = {
                                val forward = targetState.second >= initialState.second
                                val slide = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                                val fade = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                                if (forward) {
                                    (slideInHorizontally(slide) { it / 3 } + fadeIn(fade) + scaleIn(fade, initialScale = 0.98f)) togetherWith
                                        (slideOutHorizontally(slide) { -it / 6 } + fadeOut(fade))
                                } else {
                                    (slideInHorizontally(slide) { -it / 6 } + fadeIn(fade)) togetherWith
                                        (slideOutHorizontally(slide) { it / 3 } + fadeOut(fade))
                                }
                            },
                            label = "nav",
                        ) { (screen, _) ->
                            when (screen) {
                                Screen.List -> ConversationListScreen(vm)
                                is Screen.Thread -> ThreadScreen(vm, screen.threadId)
                                Screen.NewChat -> NewChatScreen(vm)
                                Screen.Blocked -> BlockedScreen(vm)
                                Screen.Settings -> SettingsScreen(vm, onSetDefault = ::requestRole)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        val t = vm.current
        visibleThread = if (t is Screen.Thread) t.threadId else -1
        if (vm.isDefault.value) requestRuntimePermissions()
    }

    override fun onPause() {
        super.onPause()
        visibleThread = -1
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java) ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }
    }

    private var askedPerms = false
    private fun requestRuntimePermissions() {
        if (askedPerms) return
        val wanted = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_PHONE_NUMBERS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) {
            askedPerms = true
            permsLauncher.launch(wanted.toTypedArray())
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_THREAD -> {
                val id = intent.getLongExtra(EXTRA_THREAD_ID, -1)
                if (id >= 0) vm.openThread(id)
            }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme !in setOf("sms", "smsto", "mms", "mmsto")) return
                val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: Sender.parseBody(data)
                vm.openAddresses(Sender.parseRecipients(data), body)
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val data = intent.data
                val text = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                val images = ArrayList<Uri>()
                if (intent.action == Intent.ACTION_SEND) {
                    androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(images::add)
                } else {
                    androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(images::addAll)
                }
                val recipients = data?.takeIf { it.scheme in setOf("sms", "smsto", "mms", "mmsto") }
                    ?.let(Sender::parseRecipients).orEmpty()
                vm.pendingShare = Draft(text ?: "", images)
                if (recipients.isNotEmpty()) vm.openAddresses(recipients, null) else vm.openAddresses(emptyList(), null)
            }
        }
    }

    /** Very faint Material You (Monet) light behind the black, when turned on in Settings. */
    private fun Modifier.monetGlow(on: Boolean): Modifier {
        if (!on) return this
        val dyn = androidx.compose.material3.dynamicDarkColorScheme(this@MainActivity)
        val a = dyn.primary
        val b = dyn.tertiary
        return this.drawBehind {
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(a.copy(alpha = 0.16f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, 0f),
                    radius = size.width * 1.15f,
                ),
            )
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(b.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    radius = size.width * 1.1f,
                ),
            )
        }
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.bitdroid.messages.OPEN_THREAD"
        const val EXTRA_THREAD_ID = "thread_id"

        /** Thread currently on screen; incoming texts there are marked read instead of notifying. */
        @Volatile
        var visibleThread: Long = -1
    }
}
