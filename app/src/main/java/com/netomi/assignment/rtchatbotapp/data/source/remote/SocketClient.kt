package com.netomi.assignment.rtchatbotapp.data.source.remote

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class SocketClient (
    private val url: String,
    private val okHttpClient: OkHttpClient = OkHttpClient().newBuilder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    ) {

        private val scope = CoroutineScope(SupervisorJob()
                + Dispatchers.IO
                + CoroutineName("SocketClient"))

        private val _incoming = MutableSharedFlow<String>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val incoming: SharedFlow<String> = _incoming.asSharedFlow()

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        //WebSocket Client
        @Volatile private var ws: WebSocket? = null
        private val client = okHttpClient

        //Reconnection/Backoff state
        private val reconnectMutex = Mutex()
        private var reconnectAttempts = 0
        private val maxReconnectAttempts = 10
        private var closedByUser = false

    private fun safeCleanupClosedSocket() {
        try { ws = null } catch (_: Throwable) {}
    }

        private val listener = object: WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                _connectionState.tryEmit(ConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!_incoming.tryEmit(text)) {
                    //when the buffer is full,
                    //enqueue emission safely
                    //in the launch
                    scope.launch {
                        _incoming.emit(text)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.tryEmit(ConnectionState.CLOSING)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                safeCleanupClosedSocket()
                if (!closedByUser) {
                    scheduleReconnectWithBackoff()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                safeCleanupClosedSocket()
                if (!closedByUser) {
                    scheduleReconnectWithBackoff()
                }
            }
        }

        //Connect
        fun connect() {
            closedByUser = false
            if (connectionState.value == ConnectionState.CONNECTED) return
            if (ws != null) {
                try { ws = null } catch (_: Throwable) {}
            }

            try {
                val request = Request.Builder().url(url).build()
                ws = client.newWebSocket(request, listener)
                _connectionState.tryEmit(ConnectionState.CONNECTING)
            } catch (e: Exception) {
                // schedule reconnect flow if immediate connect fails
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
                if (!closedByUser) scheduleReconnectWithBackoff()
            }
        }

        //Disconnect explicitly
        fun disconnect() {
            closedByUser = true
            ws?.close(1000, "client_closing")
            ws = null
            scope.coroutineContext.cancelChildren()
            _connectionState.tryEmit(ConnectionState.DISCONNECTED)
        }

        //Close Permanently
        fun close() {
            closedByUser = true
            ws?.close(1000, "closing")
            ws = null
            scope.cancel()
            _connectionState.tryEmit(ConnectionState.DISCONNECTED)
        }

        fun send(text: String): Boolean {
            val w = ws ?: return false
            return try {
                w.send(text)
            } catch (e: Exception) {
                false
            }
        }

    fun reconnectNow() {
        reconnectAttempts = 0
        scheduleReconnectWithBackoff()
    }

    private fun scheduleReconnectWithBackoff() {
        scope.launch {
            // ensure single reconnection coroutine at a time
            if (!reconnectMutex.tryLock()) return@launch
            try {
                while (!closedByUser && reconnectAttempts < maxReconnectAttempts &&
                    connectionState.value != ConnectionState.CONNECTED) {

                    val attempt = ++reconnectAttempts
                    val baseMs = 1000.0
                    val maxBackoffMs = 60_000.0
                    val exp = baseMs * 2.0.pow((attempt - 1).coerceAtLeast(0))
                    val capped = min(exp, maxBackoffMs)
                    val delayMs = ThreadLocalRandom.current().nextLong(0L, capped.toLong() + 1L)

                    delay(delayMs)

                    if (closedByUser || connectionState.value == ConnectionState.CONNECTED) break

                    reconnectMutex.withLock {
                        if (closedByUser || connectionState.value == ConnectionState.CONNECTED) return@withLock
                        try {
                            val request = Request.Builder().url(url).build()
                            ws = client.newWebSocket(request, listener)
                            _connectionState.tryEmit(ConnectionState.CONNECTING)
                        } catch (e: Exception) {
                            // failed: loop and retry
                        }
                    }
                }
            } finally {
                // ensure the mutex is released if we early-return
                if (reconnectMutex.isLocked) try { reconnectMutex.unlock() } catch (_: Throwable) {}
            }
        }
    }
}