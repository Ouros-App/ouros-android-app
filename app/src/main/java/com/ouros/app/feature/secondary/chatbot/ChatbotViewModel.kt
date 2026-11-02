package com.ouros.app.feature.secondary.chatbot

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.chatbot.ChatRequest
import com.ouros.app.data.chatbot.ChatbotNetwork
import com.ouros.app.data.chatbot.ChatbotRepository
import com.ouros.app.data.chatbot.ChatbotResult
import com.ouros.app.data.chatbot.ChatResponse
import com.ouros.app.data.chatbot.ChatVisualization
import com.ouros.app.data.chatbot.EnergyRegistry
import com.ouros.app.data.chatbot.MeasurementImportPreview
import com.ouros.app.data.chatbot.MeasurementImportRequest
import com.ouros.app.data.chatbot.WaterRegistry
import com.ouros.app.data.local.OfflineSyncRegistry
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

/**
 * Estado e ações de apresentação de chatbot. Executa operações assíncronas e publica resultados para a interface, mantendo o estado fora dos componentes visuais.
 */
class ChatbotViewModel internal constructor(
    application: Application,
    @Suppress("UNUSED_PARAMETER") savedState: SavedStateHandle,
    private val repository: ChatbotRepository,
    private val isOnline: () -> Boolean = OfflineSyncRegistry::isOnline
) : AndroidViewModel(application) {
    constructor(application: Application, savedState: SavedStateHandle) :
        this(application, savedState, ChatbotNetwork.repository(application.applicationContext))

    val state = MutableLiveData(ChatState(threadId = repository.currentThreadId()))

    fun loadConversation(force: Boolean = false) {
        if (state.value!!.historyLoading || (state.value!!.historyLoaded && !force)) return
        update { copy(historyLoading = true, error = null) }
        viewModelScope.launch {
            val available = when (val health = repository.checkHealth()) {
                is ChatbotResult.Success -> health.data
                is ChatbotResult.HttpFailure, is ChatbotResult.Failure -> false
            }
            update { copy(serviceAvailable = available) }
            when (val history = repository.loadHistory(HISTORY_PAGE_SIZE)) {
                is ChatbotResult.Success -> {
                    val body = history.data
                    body.threadId.takeIf(String::isNotBlank)?.let(repository::saveThreadId)
                    update {
                        copy(
                            threadId = body.threadId,
                            messages = body.messages.mapNotNull { item ->
                                when (item.role.lowercase()) {
                                    "user" -> ChatLine(item.content, true, item.createdAt)
                                    "assistant" -> ChatLine(item.content, false, item.createdAt, item.visualizations.orEmpty())
                                    else -> null
                                }
                            },
                            historyLoading = false,
                            historyLoaded = true
                        )
                    }
                }
                is ChatbotResult.HttpFailure, is ChatbotResult.Failure -> {
                    update { copy(historyLoading = false, historyLoaded = true) }
                }
            }
        }
    }

    fun setAttachment(uri: Uri?) = update { copy(selectedAttachment = uri) }
    fun discardAttachment() = setAttachment(null)

    fun send(message: String, attachment: Uri? = state.value?.selectedAttachment, appendMessage: Boolean = true) {
        val text = message.trim().ifEmpty { if (attachment != null) "Analise o arquivo anexado." else "" }
        if (!isOnline() || text.isEmpty() || state.value!!.sending || state.value!!.preview != null) return
        update {
            copy(
                messages = if (appendMessage) messages + ChatLine(text, true) else messages,
                sending = true,
                error = null,
                selectedAttachment = attachment
            )
        }
        viewModelScope.launch {
            try {
                val result = if (attachment == null) {
                    repository.sendMessage(
                        ChatRequest(
                            message = text,
                            threadId = state.value!!.threadId,
                            userId = repository.currentOwnerId()?.toString()
                        )
                    )
                } else {
                    repository.sendAttachment(
                        text.toRequestBody("text/plain".toMediaTypeOrNull()),
                        state.value!!.threadId.toRequestBody("text/plain".toMediaTypeOrNull()),
                        attachmentPart(attachment)
                    )
                }
                when (result) {
                    is ChatbotResult.Success -> {
                        result.data.threadId?.takeIf(String::isNotBlank)?.let(repository::saveThreadId)
                        update { completeChatSend(result.data).copy(serviceAvailable = true) }
                    }
                    is ChatbotResult.HttpFailure -> {
                        if (!state.value!!.serviceAvailable && result.code >= 500) {
                            update { completeChatSend(ChatResponse(message = mockFallback(text))) }
                        } else {
                            update { copy(sending = false, error = "Não foi possível enviar a mensagem (HTTP ${result.code}).") }
                        }
                    }
                    is ChatbotResult.Failure -> {
                        if (!state.value!!.serviceAvailable) {
                            update { completeChatSend(ChatResponse(message = mockFallback(text))) }
                        } else {
                            update { copy(sending = false, error = result.error.localizedMessage ?: "Falha ao enviar. Tente novamente.") }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!state.value!!.serviceAvailable) {
                    update { completeChatSend(ChatResponse(message = mockFallback(text))) }
                } else {
                    update { copy(sending = false, error = error.localizedMessage ?: "Falha ao enviar. Tente novamente.") }
                }
            }
        }
    }

    fun confirmImport() {
        val preview = state.value!!.preview ?: return
        if (state.value!!.importing || state.value!!.imported || state.value!!.importAttemptFailed) return
        update { copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                // O backend recebe apenas medições completas; campos opcionais incompletos ficam fora da importação.
                val request = MeasurementImportRequest(
                    waterRegistries = preview.records.filter { it.kind.equals("water", true) }
                        .mapNotNull { record ->
                            val start = record.startHydrometer?.takeIf(String::isNotBlank)
                            val end = record.endHydrometer?.takeIf(String::isNotBlank)
                            if (start != null && end != null) {
                                WaterRegistry(record.registrationDate, start, end)
                            } else {
                                null
                            }
                        },
                    energyRegistries = preview.records.filter { it.kind.equals("energy", true) }
                        .mapNotNull { record ->
                            record.energyConsumption?.takeIf(String::isNotBlank)?.let {
                                EnergyRegistry(record.registrationDate, it)
                            }
                        }
                )
                if (request.waterRegistries.isEmpty() && request.energyRegistries.isEmpty()) {
                    throw IllegalStateException("Não há registros válidos para importar.")
                }
                val result = repository.importMeasurements(request)
                if (result !is ChatbotResult.Success) {
                    val errorMessage = when (result) {
                        is ChatbotResult.HttpFailure -> "Não foi possível salvar os registros (HTTP ${result.code})."
                        is ChatbotResult.Failure -> result.error.localizedMessage ?: "Não foi possível salvar os registros."
                        is ChatbotResult.Success -> ""
                    }
                    throw IOException(errorMessage)
                }
                update {
                    copy(
                        importing = false,
                        imported = true,
                        importAttemptFailed = false,
                        preview = null,
                        error = null,
                        messages = messages + ChatLine(
                            "Importação concluída: ${request.waterRegistries.size} registros de água e ${request.energyRegistries.size} de energia.",
                            false
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                update {
                    copy(
                        importing = false,
                        importAttemptFailed = true,
                        error = "Não foi possível confirmar se a importação foi concluída. Confira os registros da fazenda antes de tentar novamente."
                    )
                }
            }
        }
    }

    fun cancelPreview() {
        if (state.value!!.importing || state.value!!.imported) return
        val message = if (state.value!!.importAttemptFailed) {
            "Prévia encerrada. Confira os registros da fazenda antes de reenviar o arquivo."
        } else "Importação cancelada. Nenhum registro foi salvo."
        update { copy(preview = null, error = null, importAttemptFailed = false, messages = messages + ChatLine(message, false)) }
    }

    fun retry() {
        val current = state.value!!
        if (current.preview != null) confirmImport()
        else send(current.messages.lastOrNull { it.user }?.text.orEmpty(), current.selectedAttachment, appendMessage = false)
    }

    private fun attachmentPart(uri: Uri): MultipartBody.Part {
        val resolver = getApplication<Application>().contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "arquivo"
        val body = object : RequestBody() {
            override fun contentType() = resolver.getType(uri)?.toMediaTypeOrNull()
            override fun contentLength() = -1L
            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri) ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
                input.use { it.copyTo(sink.outputStream()) }
            }
        }
        return MultipartBody.Part.createFormData("attachment", name, body)
    }

    private inline fun update(transform: ChatState.() -> ChatState) {
        state.value = state.value?.transform()
    }

    private fun mockFallback(message: String): String = when {
        message.contains("ranking", ignoreCase = true) ->
            "O Midas está temporariamente indisponível. Consulte a tela de Ranking para ver sua posição atual."
        else -> "O Midas está temporariamente indisponível. Sua mensagem não foi enviada ao assistente; tente novamente quando o serviço voltar."
    }

    private companion object { const val HISTORY_PAGE_SIZE = 20 }
}

data class ChatState(
    val threadId: String,
    val messages: List<ChatLine> = emptyList(),
    val sending: Boolean = false,
    val importing: Boolean = false,
    val imported: Boolean = false,
    val importAttemptFailed: Boolean = false,
    val selectedAttachment: Uri? = null,
    val preview: MeasurementImportPreview? = null,
    val error: String? = null,
    val historyLoading: Boolean = false,
    val historyLoaded: Boolean = false,
    val serviceAvailable: Boolean = false
)

data class ChatLine(
    val text: String,
    val user: Boolean,
    val createdAt: String? = null,
    val visualizations: List<ChatVisualization> = emptyList()
)

internal fun ChatState.completeChatSend(response: ChatResponse): ChatState = copy(
    messages = messages + ChatLine(
        response.message ?: response.response ?: "Recebi sua mensagem.",
        false,
        visualizations = response.visualizations.orEmpty()
    ),
    threadId = response.threadId ?: threadId,
    sending = false,
    preview = response.preview,
    imported = false,
    importAttemptFailed = false,
    selectedAttachment = null,
    error = null
)
