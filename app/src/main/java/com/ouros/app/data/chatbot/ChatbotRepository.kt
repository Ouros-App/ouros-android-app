package com.ouros.app.data.chatbot

import android.content.Context
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

sealed interface ChatbotResult<out T> {
    data class Success<T>(val data: T) : ChatbotResult<T>
    data class HttpFailure(val code: Int) : ChatbotResult<Nothing>
    data class Failure(val error: Exception) : ChatbotResult<Nothing>
}

class ChatbotRepository(
    context: Context,
    private val aiApi: ChatbotApi,
    private val springApi: ChatbotApi
) {
    private val farmSession = com.ouros.app.data.session.FarmSessionStore(context.applicationContext)
    private val sessionStore = ChatbotSessionStore(context.applicationContext)

    fun currentOwnerId(): Long? = farmSession.ownerId()

    fun currentThreadId(): String = sessionStore.threadId(currentOwnerId())

    fun saveThreadId(threadId: String) {
        sessionStore.saveThreadId(currentOwnerId(), threadId)
    }

    suspend fun checkHealth(): ChatbotResult<Boolean> = try {
        val response = aiApi.healthCheck()
        if (!response.isSuccessful) {
            ChatbotResult.HttpFailure(response.code())
        } else {
            ChatbotResult.Success(response.body()?.status.equals("ok", ignoreCase = true))
        }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        ChatbotResult.Failure(error)
    }

    suspend fun loadHistory(limit: Int = 20): ChatbotResult<HistoryResponse> = request {
        aiApi.getHistory(
            threadId = currentThreadId(),
            userId = currentOwnerId()?.toString(),
            limit = limit
        )
    }

    suspend fun sendMessage(chatRequest: ChatRequest): ChatbotResult<ChatResponse> =
        request { aiApi.sendMessage(chatRequest) }

    suspend fun sendAttachment(
        message: RequestBody,
        threadId: RequestBody,
        attachment: MultipartBody.Part
    ): ChatbotResult<ChatResponse> = request {
        aiApi.chatWithAttachment(message, threadId, attachment)
    }

    suspend fun importMeasurements(
        importRequest: MeasurementImportRequest
    ): ChatbotResult<MeasurementImportResponse> = request {
        springApi.importMeasurements(importRequest)
    }

    private suspend fun <T> request(call: suspend () -> Response<T>): ChatbotResult<T> = try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ChatbotResult.Success(body)
        } else {
            ChatbotResult.HttpFailure(response.code())
        }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        ChatbotResult.Failure(error)
    }
}
