package com.bitdroid.messages.ui

import android.app.Application
import android.app.role.RoleManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bitdroid.messages.MainActivity
import com.bitdroid.messages.Sender
import com.bitdroid.messages.appScope
import com.bitdroid.messages.data.Blocked
import com.bitdroid.messages.data.Contact
import com.bitdroid.messages.data.Contacts
import com.bitdroid.messages.data.Conversation
import com.bitdroid.messages.data.Message
import com.bitdroid.messages.data.Prefs
import com.bitdroid.messages.data.SearchHit
import com.bitdroid.messages.data.SimInfo
import com.bitdroid.messages.data.Sims
import com.bitdroid.messages.data.Store
import com.bitdroid.messages.mms.MmsSender
import com.bitdroid.messages.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object List : Screen
    data class Thread(val threadId: Long) : Screen
    data object NewChat : Screen
    data object Blocked : Screen
    data object Settings : Screen
}

data class ThreadState(
    val threadId: Long = -1,
    val recipients: kotlin.collections.List<Contact> = emptyList(),
    val messages: kotlin.collections.List<Message> = emptyList(),
    val loaded: Boolean = false,
    val subId: Int = -1,
)

/** Something shared into the app (text and/or images) waiting for a conversation. */
data class Draft(val text: String = "", val images: kotlin.collections.List<Uri> = emptyList())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val stack = mutableStateListOf<Screen>(Screen.List)
    val current: Screen get() = stack.last()

    private val _isDefault = MutableStateFlow(checkDefault())
    val isDefault: StateFlow<Boolean> = _isDefault

    private val _conversations = MutableStateFlow<kotlin.collections.List<Conversation>>(emptyList())
    val conversations: StateFlow<kotlin.collections.List<Conversation>> = _conversations
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _hits = MutableStateFlow<kotlin.collections.List<SearchHit>>(emptyList())
    val hits: StateFlow<kotlin.collections.List<SearchHit>> = _hits

    private val _thread = MutableStateFlow(ThreadState())
    val thread: StateFlow<ThreadState> = _thread

    private val _sims = MutableStateFlow<kotlin.collections.List<SimInfo>>(emptyList())
    val sims: StateFlow<kotlin.collections.List<SimInfo>> = _sims

    private val _blocked = MutableStateFlow<kotlin.collections.List<String>>(emptyList())
    val blocked: StateFlow<kotlin.collections.List<String>> = _blocked

    /** Unsent text per thread, and a shared-in draft for the next opened thread. */
    val drafts = mutableStateMapOf<Long, Draft>()
    var pendingShare: Draft? = null

    private val handler = Handler(Looper.getMainLooper())
    private var refreshJob: Job? = null
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = scheduleRefresh()
    }
    private var observing = false

    private fun checkDefault(): Boolean =
        ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true

    fun onResume() {
        _isDefault.value = checkDefault()
        if (_isDefault.value && !observing) {
            try {
                ctx.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
                ctx.contentResolver.registerContentObserver(Uri.parse("content://sms/"), true, observer)
                ctx.contentResolver.registerContentObserver(Uri.parse("content://mms/"), true, observer)
                observing = true
            } catch (_: Exception) {
            }
        }
        Contacts.clearCache()
        refresh()
    }

    override fun onCleared() {
        if (observing) ctx.contentResolver.unregisterContentObserver(observer)
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(200)
            refresh()
        }
    }

    fun refresh() {
        if (!_isDefault.value) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try { Store.conversations(ctx) } catch (_: Exception) { emptyList() }
            }
            _conversations.value = list
            _loaded.value = true
            _sims.value = withContext(Dispatchers.IO) { Sims.active(ctx) }
            if (_query.value.isNotBlank()) runSearch(_query.value)
            val t = _thread.value
            if (t.threadId >= 0 && current is Screen.Thread) loadThread(t.threadId, markRead = true)
        }
    }

    // ------------------------------------------------------------- navigation

    fun push(s: Screen) {
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        val leaving = stack.removeAt(stack.lastIndex)
        if (leaving is Screen.Thread) {
            MainActivity.visibleThread = -1
            _thread.value = ThreadState()
        }
        val now = current
        if (now is Screen.Thread) openThread(now.threadId, push = false)
        return true
    }

    fun openThread(threadId: Long, push: Boolean = true) {
        if (push) {
            // Don't stack the same thread twice (e.g. from a notification).
            stack.removeAll { it is Screen.Thread || it is Screen.NewChat }
            stack.add(Screen.Thread(threadId))
        }
        pendingShare?.let { share ->
            val old = drafts[threadId] ?: Draft()
            drafts[threadId] = Draft(
                text = listOf(old.text, share.text).filter { it.isNotBlank() }.joinToString(" "),
                images = old.images + share.images,
            )
            pendingShare = null
        }
        MainActivity.visibleThread = threadId
        _thread.value = ThreadState(threadId = threadId)
        loadThread(threadId, markRead = true)
    }

    fun openAddresses(addresses: kotlin.collections.List<String>, body: String?) {
        if (addresses.isEmpty()) {
            if (!body.isNullOrBlank()) pendingShare = Draft(text = body)
            push(Screen.NewChat)
            return
        }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { Store.threadIdFor(ctx, addresses) }
            if (!body.isNullOrBlank()) pendingShare = Draft(text = body)
            openThread(id)
        }
    }

    private fun loadThread(threadId: Long, markRead: Boolean) {
        viewModelScope.launch {
            val (recipients, msgs, sub) = withContext(Dispatchers.IO) {
                val addrs = Store.threadAddresses(ctx, threadId)
                val r = addrs.map { Contacts.lookup(ctx, it) }
                val m = try { Store.messages(ctx, threadId) } catch (_: Exception) { emptyList() }
                if (markRead) {
                    Store.markThreadRead(ctx, threadId)
                    Notifier.cancel(ctx, threadId)
                }
                Triple(r, m, Sims.resolve(ctx, threadId, Store.lastIncomingSub(ctx, threadId)))
            }
            if (_thread.value.threadId != threadId) return@launch
            _thread.value = ThreadState(threadId, recipients, msgs, true, sub)
            // Kick off any MMS that still need downloading.
            msgs.filter { it.status == com.bitdroid.messages.data.MessageStatus.PENDING_DOWNLOAD }
                .forEach { m -> appScope.launch { MmsSender.download(ctx, m.id) } }
        }
    }

    // ---------------------------------------------------------------- actions

    fun setQuery(q: String) {
        _query.value = q
        runSearch(q)
    }

    private var searchJob: Job? = null
    private fun runSearch(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _hits.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(150)
            val list = _conversations.value
            _hits.value = withContext(Dispatchers.IO) {
                try { Store.search(ctx, q, list) } catch (_: Exception) { emptyList() }
            }
        }
    }

    fun send(text: String, images: kotlin.collections.List<Uri>) {
        val t = _thread.value
        if (t.threadId < 0) return
        drafts.remove(t.threadId)
        val addresses = t.recipients.map { it.address }
        val sub = t.subId.takeIf { it >= 0 }
        appScope.launch { Sender.sendToThread(ctx, t.threadId, addresses, text, images, sub) }
    }

    fun chooseSim(subId: Int) {
        val t = _thread.value
        if (t.threadId < 0) return
        Prefs.setThreadSim(ctx, t.threadId, subId)
        _thread.value = t.copy(subId = subId)
    }

    fun retry(m: Message) {
        val sub = _thread.value.subId.takeIf { it >= 0 }
        appScope.launch { Sender.retry(ctx, m, sub) }
    }

    fun download(m: Message) {
        appScope.launch { MmsSender.download(ctx, m.id) }
        scheduleRefresh()
    }

    fun deleteMessage(m: Message) {
        appScope.launch { Store.deleteMessage(ctx, m) }
    }

    fun deleteThreads(ids: Collection<Long>) {
        val list = ids.toList()
        _conversations.value = _conversations.value.filterNot { it.threadId in list }
        appScope.launch {
            list.forEach {
                Store.deleteThread(ctx, it)
                Notifier.deleteChannel(ctx, it)
            }
        }
        if (current is Screen.Thread && (current as Screen.Thread).threadId in list) back()
    }

    fun markRead(ids: Collection<Long>) {
        val list = ids.toList()
        appScope.launch {
            list.forEach {
                Store.markThreadRead(ctx, it)
                Notifier.cancel(ctx, it)
            }
        }
    }

    fun block(addresses: Collection<String>) {
        appScope.launch {
            addresses.forEach { Blocked.block(ctx, it) }
            _blocked.value = Blocked.list(ctx)
        }
    }

    fun unblock(address: String) {
        appScope.launch {
            Blocked.unblock(ctx, address)
            _blocked.value = Blocked.list(ctx)
        }
    }

    fun loadBlocked() {
        viewModelScope.launch { _blocked.value = withContext(Dispatchers.IO) { Blocked.list(ctx) } }
    }

    fun isBlocked(address: String): Boolean = _blocked.value.any { com.bitdroid.messages.data.Phone.same(it, address) }

    fun saveDraft(threadId: Long, text: String, images: kotlin.collections.List<Uri>) {
        if (text.isBlank() && images.isEmpty()) drafts.remove(threadId) else drafts[threadId] = Draft(text, images)
    }
}
