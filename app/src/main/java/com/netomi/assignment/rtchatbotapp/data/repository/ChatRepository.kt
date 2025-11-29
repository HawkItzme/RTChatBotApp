package com.netomi.assignment.rtchatbotapp.data.repository

import com.netomi.assignment.rtchatbotapp.data.model.ChatThreadEntity
import com.netomi.assignment.rtchatbotapp.data.model.QueuedMessageEntity
import com.netomi.assignment.rtchatbotapp.data.source.local.ChatThreadDao
import com.netomi.assignment.rtchatbotapp.data.source.local.QueuedMessageDao
import com.netomi.assignment.rtchatbotapp.data.source.remote.SocketClient
import com.netomi.assignment.rtchatbotapp.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val socketClient: SocketClient,
    private val queuedMessageDao: QueuedMessageDao,
    private val chatThreadDao: ChatThreadDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val incomingFlow: SharedFlow<String> = socketClient.incoming

    val connectionState = socketClient.connectionState

    fun observeThreads(): Flow<List<ChatThreadEntity>> = chatThreadDao.observeAllThreads()

    suspend fun upsertThread(thread: ChatThreadEntity) = withContext(dispatcher) {
        chatThreadDao.upsert(thread)
    }

    suspend fun markThreadRead(threadId: String) = withContext(dispatcher) {
        chatThreadDao.markRead(threadId)
    }

    suspend fun getThread(threadId: String) = withContext(dispatcher) {
        chatThreadDao.getThread(threadId)
    }

    suspend fun sendMessageRaw(id: String, text: String): Boolean = withContext(dispatcher) {
        val payload = try {
            JSONObject().apply {
                put("id", id)
                put("text", text)
            }.toString()
        } catch (e: Exception) {
            // fallback to plain text if JSON creation fails
            text
        }

        try {
            socketClient.send(payload)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun queueMessage(entity: QueuedMessageEntity) = withContext(dispatcher) {
        queuedMessageDao.insert(entity)
    }

    suspend fun getQueuedMessages() = withContext(dispatcher) {
        queuedMessageDao.getAll()
    }

    suspend fun deleteQueuedMessage(id: String) = withContext(dispatcher) {
        queuedMessageDao.delete(id)
    }

    suspend fun resendAllQueued(): Int = withContext(dispatcher) {
        val queued = queuedMessageDao.getAll()
        var successCount = 0
        for (q in queued) {
            val ok = socketClient.send(q.text)
            if (ok) {
                queuedMessageDao.delete(q.id)
                successCount++
            }
            // else keep it for later
        }
        successCount
    }

    fun start() {
        socketClient.connect()
    }

    fun stop() {
        socketClient.disconnect()
    }

    fun close() {
        socketClient.close()
    }
}