package com.rtchatbotapp.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rtchatbotapp.data.model.ChatThreadEntity
import com.rtchatbotapp.data.model.Message
import com.rtchatbotapp.data.model.MessageStatus
import com.rtchatbotapp.data.model.QueuedMessageEntity
import com.rtchatbotapp.data.repository.ChatRepository
import com.rtchatbotapp.data.source.remote.ConnectionState
import com.rtchatbotapp.util.NetworkObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkObserver: NetworkObserver
) : ViewModel(){

    private val _activeThreadsInSession = MutableStateFlow<Set<String>>(emptySet())
    val activeThreadsInSession: StateFlow<Set<String>> = _activeThreadsInSession.asStateFlow()

    private fun markThreadActiveInSession(threadId: String) {
        if (threadId.isBlank()) return
        val before = _activeThreadsInSession.value
        if (threadId !in before) {
            _activeThreadsInSession.update { current -> current + threadId }
            Log.d("ChatViewModel", "markThreadActiveInSession: added thread=$threadId; now=${_activeThreadsInSession.value}")
        } else {
            Log.d("ChatViewModel", "markThreadActiveInSession: already active thread=$threadId")
        }
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()


    private val _simulateFailure = MutableStateFlow(false)
    val simulateFailure: StateFlow<Boolean> = _simulateFailure.asStateFlow()

    private var currentThreadId: String? = null

    // Prevent overlapping resend jobs
    private val resendMutex = Mutex()

    private var incomingJob: Job? = null
    private var connectionCollectorJob: Job? = null
    private var networkCollectorJob: Job? = null

    // Dedupe implementations for Websocket
    private val recentIncomingHashes =
        ConcurrentLinkedQueue<Pair<Int, Long>>() // Pair(hash, epochMs)
    private val RECENT_DEDUPE_WINDOW_MS = TimeUnit.SECONDS.toMillis(5)

    private fun cleanupRecentHashes() {
        val cutoff = System.currentTimeMillis() - RECENT_DEDUPE_WINDOW_MS
        while ((recentIncomingHashes.peek()?.second ?: 0L) < cutoff) {
            recentIncomingHashes.poll() ?: break
        }
    }


    init {
        _activeThreadsInSession.value = emptySet()
        chatRepository.start()
        observeIncomingMessages()
        observeConnection()
        observeNetwork()
    }

    private fun observeIncomingMessages(){
        incomingJob = viewModelScope.launch {
            chatRepository.incomingFlow.collect { raw ->

                var incomingId: String? = null
                var incomingText: String = raw
                var incomingTimestamp = System.currentTimeMillis()
                var incomingThreadId = "default"

                // Parsing JSON
                try {
                    val obj = JSONObject(raw)
                    if (obj.has("id")) incomingId = obj.optString("id", null)
                    if (obj.has("text")) incomingText = obj.optString("text", raw)
                    if (obj.has("timestamp")) incomingTimestamp = obj.optLong("timestamp", incomingTimestamp)
                    if (obj.has("threadId")) incomingThreadId = obj.optString("threadId", "default")
                } catch (e: JSONException) {
                    incomingId = null
                    incomingText = raw
                    incomingThreadId = "default"
                }

                Log.d("ChatViewModel", "Incoming raw: thread=$incomingThreadId id=$incomingId text=$incomingText")

                // Check ACK vs Remote to avoid unnecessary Badges due to echo
                val currentMessages = _uiState.value.messages
                val isAck = !incomingId.isNullOrBlank() && currentMessages.any { it.id == incomingId }

                if (isAck) {
                    Log.d("ChatViewModel", "Incoming is ACK for local message id=$incomingId")
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == incomingId) msg.copy(status = MessageStatus.SENT) else msg
                            }
                        )
                    }
                    try{
                        val existing = chatRepository.getThread(incomingThreadId)
                        val threadEntity = ChatThreadEntity(
                            threadId = incomingThreadId,
                            title = existing?.title ?: "Conversation",
                            lastMessageText = incomingText,
                            lastMessageAt = incomingTimestamp,
                            unreadCount = existing?.unreadCount ?: 0
                        )
                        chatRepository.upsertThread(threadEntity)
                        Log.d("ChatViewModel", "ACK thread upsert done for $incomingThreadId unread=${threadEntity.unreadCount}")
                    }catch (e: Exception){
                        Log.w("ChatViewModel", "Failed to upsert thread for ACK: ${e.message}")
                    }
                    return@collect
                }

                // Fallback Dedupe by computing a hash when there's no ID
                // This can be improved further
                cleanupRecentHashes()
                val hash = incomingText.hashCode()
                val foundRecent = recentIncomingHashes.any { it.first == hash }
                if (foundRecent) {
                    Log.d("ChatViewModel", "Dropped duplicate remote message by hash")
                    return@collect
                }
                recentIncomingHashes.offer(hash to System.currentTimeMillis())
                val message = Message(
                    id = UUID.randomUUID().toString(),
                    text = incomingText,
                    timestamp = incomingTimestamp,
                    isFromUser = false,
                    status = MessageStatus.SENT
                )
                markThreadActiveInSession(incomingThreadId)
                _uiState.update { it.copy(messages = it.messages + message) }
                Log.d("ChatViewModel", "Added remote message to UI id=${message.id}")

                try{
                    val existing = chatRepository.getThread(incomingThreadId)
                    val increment = if (currentThreadId != null && currentThreadId == incomingThreadId) 0 else 1
                    val newUnread = (existing?.unreadCount ?: 0) + increment
                    val newEntity = ChatThreadEntity(
                        threadId = incomingThreadId,
                        title = existing?.title ?: "Conversation",
                        lastMessageText = incomingText,
                        lastMessageAt = incomingTimestamp,
                        unreadCount = newUnread
                    )
                    chatRepository.upsertThread(newEntity)
                    Log.d("ChatViewModel", "Upserted thread $incomingThreadId unread=$newUnread (inc=$increment)")
                }catch (e: Exception){
                    Log.w("ChatViewModel", "Failed to upsert thread: ${e.message}")
                }
            }
        }
    }

    private fun observeConnection(){
        connectionCollectorJob = viewModelScope.launch {
            var previousState = chatRepository.connectionState.value
            chatRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state.name) }

                if (previousState != ConnectionState.CONNECTED && state == ConnectionState.CONNECTED) {
                    attemptResendQueuedSafely()
                }
            }
        }
    }

    private fun observeNetwork(){
        networkCollectorJob = viewModelScope.launch {
            networkObserver.start()
            networkObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (online) chatRepository.start()
            }
        }
    }

    fun setCurrentThread(threadId: String?) {
        currentThreadId = threadId
        if (!threadId.isNullOrBlank()) {
            markThreadRead(threadId)
        }
    }

    fun markThreadRead(threadId: String) {
        viewModelScope.launch {
            try {
                chatRepository.markThreadRead(threadId)
            } catch (e: Exception) {
                Log.w("ChatViewModel", "markThreadRead failed: ${e.message}")
            }
        }
    }

    // Queued Messages Resend attempt
    private suspend fun attemptResendQueuedSafely() {
        resendMutex.withLock {
            withContext(Dispatchers.IO) {
                val result = withTimeoutOrNull(30_000L) {
                    try {
                        val queued = chatRepository.getQueuedMessages()
                        if (queued.isEmpty()) return@withTimeoutOrNull true

                        var resentCount = 0

                        for (q in queued) {
                            val success = try {
                                chatRepository.sendMessageRaw(q.id, q.text)
                            } catch (e: Exception) {
                                false
                            }

                            if (success) {
                                chatRepository.deleteQueuedMessage(q.id)
                                _uiState.update { state ->
                                    state.copy(messages = state.messages.map { msg ->
                                        if (msg.id == q.id) msg.copy(status = MessageStatus.SENT) else msg
                                    })
                                }
                                resentCount++
                            }
                        }

                        if (resentCount > 0) {
                            _events.send(UiEvent.ShowSnackbar("Resent $resentCount queued message(s)"))
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (result == null) {
                    _events.send(UiEvent.ShowSnackbar("Resend timed out. Try Later"))
                }
            }
        }
    }

    // Simulation Failure
    fun toggleSimulateFailure() {
        _simulateFailure.update { !it }

        viewModelScope.launch {
            _events.send(UiEvent.ShowSnackbar("SimFail = ${_simulateFailure.value}"))

            if (!_simulateFailure.value) {
                try {
                    chatRepository.start()
                } catch (_: Throwable) {}

                val becameConnected = withTimeoutOrNull(10_000L) {
                    chatRepository.connectionState
                        .filter { it == ConnectionState.CONNECTED }
                        .first()
                    true
                } ?: false
                try {
                    attemptResendQueuedSafely()
                } catch (e: Exception) {
                    _events.send(UiEvent.ShowSnackbar("Resend attempt failed. Retry later"))
                }
                if (!becameConnected) {
                    _events.send(UiEvent.ShowSnackbar("Waiting for Socket reconnect..."))
                }
            }
        }
    }


    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        val threadIdToUse = currentThreadId ?: "default"

        markThreadActiveInSession(threadIdToUse)

        val id = UUID.randomUUID().toString()
        val message = Message(id = id, text = text, isFromUser = true, status = MessageStatus.SENT)

        _uiState.update { it.copy(messages = it.messages + message) }

        viewModelScope.launch {
            try {
                val existing = chatRepository.getThread(threadIdToUse)
                val threadEntity = ChatThreadEntity(
                    threadId = threadIdToUse,
                    title = existing?.title ?: "Conversation",
                    lastMessageText = text,
                    lastMessageAt = message.timestamp,
                    unreadCount = existing?.unreadCount ?: 0 // unchanged
                )
                chatRepository.upsertThread(threadEntity)
                Log.d("ChatViewModel", "Updated thread preview for send: $threadIdToUse")
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Failed to update thread preview on send: ${e.message}")
            }
        }

        viewModelScope.launch {
            val simulate = _simulateFailure.value
            val success = if (simulate) {
                false
            } else{
                try {
                    chatRepository.sendMessageRaw(id, text)
                } catch (e: Exception) {
                    Log.w("ChatViewModel", "sendMessageRaw failed: ${e.message}")
                    false
                }
            }
            if (!success) {
                val queued = QueuedMessageEntity(
                    id = id,
                    text = text,
                    timestamp = message.timestamp,
                    isFromUser = true
                )
                try {
                    chatRepository.queueMessage(queued)
                } catch (e: Exception) {
                    _events.send(UiEvent.ShowSnackbar("Failed to persist queued message"))
                }

                _uiState.update { state ->
                    state.copy(messages = state.messages.map {
                        if (it.id == id)
                            it.copy(status = MessageStatus.PENDING)
                        else
                            it })
                }
                _events.send(UiEvent.ShowSnackbar("Message queued (offline)"))
            } else {
                Log.d("ChatViewModel", "Message sent: $text")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            chatRepository.stop()
        } catch (_: Throwable) {}
        try {
            networkObserver.stop()
        } catch (_: Throwable) {}

        incomingJob?.cancel()
        connectionCollectorJob?.cancel()
        networkCollectorJob?.cancel()
    }

}