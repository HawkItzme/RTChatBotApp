package com.netomi.assignment.rtchatbotapp.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netomi.assignment.rtchatbotapp.data.model.Message
import com.netomi.assignment.rtchatbotapp.data.model.MessageStatus
import com.netomi.assignment.rtchatbotapp.data.model.QueuedMessageEntity
import com.netomi.assignment.rtchatbotapp.data.repository.ChatRepository
import com.netomi.assignment.rtchatbotapp.data.source.remote.ConnectionState
import com.netomi.assignment.rtchatbotapp.util.NetworkObserver
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

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // dev toggle to simulate failure (not persisted)
    private val _simulateFailure = MutableStateFlow(false)
    val simulateFailure: StateFlow<Boolean> = _simulateFailure.asStateFlow()

    // Prevent overlapping resend jobs
    private val resendMutex = Mutex()

    private var incomingJob: Job? = null
    private var connectionCollectorJob: Job? = null
    private var networkCollectorJob: Job? = null

    private val recentIncomingHashes =
        ConcurrentLinkedQueue<Pair<Int, Long>>() // Pair(hash, epochMs)
    private val RECENT_DEDUPE_WINDOW_MS = TimeUnit.SECONDS.toMillis(5) // 5s window
    private fun cleanupRecentHashes() {
        val cutoff = System.currentTimeMillis() - RECENT_DEDUPE_WINDOW_MS
        while (recentIncomingHashes.peek()?.second ?: 0L < cutoff) {
            recentIncomingHashes.poll() ?: break
        }
    }


    init {

        // start socket
       chatRepository.start()

        incomingJob = viewModelScope.launch {
            chatRepository.incomingFlow.collect { raw ->
                // Try parse JSON with { id, text } shape
                var incomingId: String? = null
                var incomingText: String = raw
                var incomingTimestamp = System.currentTimeMillis()

                try {
                    val obj = JSONObject(raw)
                    if (obj.has("id")) incomingId = obj.optString("id", null)
                    if (obj.has("text")) incomingText = obj.optString("text", raw)
                    if (obj.has("timestamp")) incomingTimestamp = obj.optLong("timestamp", incomingTimestamp)
                } catch (e: JSONException) {
                    // not JSON; treat as plain text
                    incomingId = null
                    incomingText = raw
                }

                // 1) If id present -> dedupe/ack by id
                if (!incomingId.isNullOrBlank()) {
                    val currentMessages = _uiState.value.messages
                    val existing = currentMessages.find { it.id == incomingId }

                    if (existing != null) {
                        // This is an ack/echo for our own message — update status instead of adding duplicate
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == incomingId) msg.copy(status = MessageStatus.SENT) else msg
                            })
                        }
                        // done for this event
                        return@collect
                    } else {
                        // not found locally -> treat as a genuine remote message with provided id
                        val message = Message(
                            id = incomingId,
                            text = incomingText,
                            timestamp = incomingTimestamp,
                            isFromUser = false,
                            status = MessageStatus.SENT
                        )
                        _uiState.update { it.copy(messages = it.messages + message) }
                        return@collect
                    }
                }

                // 2) No id -> fallback dedupe by content + recent window
                // compute simple hash for text; keep small in-memory queue of recent hashes
                val hash = incomingText.hashCode()
                cleanupRecentHashes()

                // if same text was received recently, ignore
                val foundRecent = recentIncomingHashes.any { it.first == hash }
                if (foundRecent) {
                    // duplicate within window — ignore
                    return@collect
                } else {
                    // record to recent queue
                    recentIncomingHashes.offer(hash to System.currentTimeMillis())
                    // add as new remote message
                    val message = Message(
                        id = UUID.randomUUID().toString(),
                        text = incomingText,
                        timestamp = incomingTimestamp,
                        isFromUser = false,
                        status = MessageStatus.SENT
                    )
                    _uiState.update { it.copy(messages = it.messages + message) }
                }
            }
        }

        connectionCollectorJob = viewModelScope.launch {
            var previousState = chatRepository.connectionState.value
            chatRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state.name) }

                if (previousState != ConnectionState.CONNECTED && state == ConnectionState.CONNECTED) {
                    // Launch resend in its own coroutine but ensure only one resend runs at a time
                    viewModelScope.launch {
                        attemptResendQueuedSafely()
                    }
                }
                previousState = state
            }
        }

        // network observer

        networkCollectorJob = viewModelScope.launch {
            networkObserver.start()
            networkObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
                if (online) {
                    chatRepository.start()
                }else{
                    //Offline
                }

            }
        }
    }

    private suspend fun attemptResendQueuedSafely() {
        resendMutex.withLock {
            withContext(Dispatchers.IO) {
                val timedOut = withTimeoutOrNull(30_000L) {
                    try {
                        val queued = chatRepository.getQueuedMessages()
                        if (queued.isEmpty()) return@withTimeoutOrNull true

                        var resentCount = 0
                        for (q in queued) {
                            val ok = try {
                                chatRepository.sendMessageRaw(q.id, q.text)
                            } catch (e: Exception) {
                                false
                            }

                            if (ok) {
                                // remove from DB
                                chatRepository.deleteQueuedMessage(q.id)
                                _uiState.update { state ->
                                    state.copy(messages = state.messages.map { msg ->
                                        if (msg.id == q.id) msg.copy(status = MessageStatus.SENT) else msg
                                    })
                                }
                                resentCount++
                            } else {
                                // leave in DB for next attempt; don't spam
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
                if (timedOut == null) {
                    _events.send(UiEvent.ShowSnackbar("Resend timed out; will retry later"))
                }
            }
        }
    }

    fun toggleSimulateFailure() {
        _simulateFailure.update { !it }

        viewModelScope.launch {
            _events.send(UiEvent.ShowSnackbar("SimulateFailure = ${_simulateFailure.value}"))

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
                    _events.send(UiEvent.ShowSnackbar("Resend attempt failed; will retry later"))
                }
                if (!becameConnected) {
                    _events.send(UiEvent.ShowSnackbar("Socket didn't connect within timeout; queued messages will be retried when connection is available"))
                }
            }
        }
    }


    fun sendUserMessage(text: String) {
        if (text.isBlank()) return
        val id = UUID.randomUUID().toString()
        val message = Message(id = id, text = text, isFromUser = true, status = MessageStatus.SENT)

        _uiState.update { it.copy(messages = it.messages + message) }

        viewModelScope.launch {
            val simulate = _simulateFailure.value
            val success = if (simulate) {
                false
            } else{
                try {
                    chatRepository.sendMessageRaw(id, text)
                } catch (e: Exception) {
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
                    state.copy(messages = state.messages.map { if (it.id == id) it.copy(status = MessageStatus.PENDING) else it })
                }

                _events.send(UiEvent.ShowSnackbar("Message queued (offline)"))

            } else {
               // _events.send(UiEvent.ShowSnackbar("Message sent"))
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