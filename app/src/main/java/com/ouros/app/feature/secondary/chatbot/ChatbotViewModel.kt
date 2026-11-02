package com.ouros.app.feature.secondary.chatbot

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.local.OfflineSyncRegistry
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Response

class ChatbotViewModel internal constructor(
    application: Application,
    @Suppress("UNUSED_PARAMETER") savedState: SavedStateHandle,
    private val aiApi: ChatbotApi,
    private val springApi: ChatbotApi,
    private val isOnline: () -> Boolean = OfflineSyncRegistry::isOnline
) : AndroidViewModel(application) {
    constructor(application: Application, savedState: SavedStateHandle) :
        this(application, savedState, ChatbotNetwork.aiApi, ChatbotNetwork.springApi)

    private val farmSessionStore = FarmSessionStore(application.applicationContext)
    private val ownerId = farmSessionStore.ownerId()
    private val sessionStore = ChatbotSessionStore(application.applicationContext)
    val state = MutableLiveData(ChatState(threadId = sessionStore.threadId(ownerId)))

    fun loadConversation(force: Boolean = false) {
        if (state.value!!.historyLoading || (state.value!!.historyLoaded && !force)) return
        update { copy(historyLoading = true, error = null) }
        viewModelScope.launch {
            val available = try {
                val health = aiApi.healthCheck()
                health.isSuccessful && health.body()?.status.equals("ok", true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            update { copy(serviceAvailable = available) }
            try {
                val response = aiApi.getHistory(
                    threadId = state.value!!.threadId,
                    userId = ownerId?.toString(),
                    limit = HISTORY_PAGE_SIZE
                )
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    body.threadId.takeIf(String::isNotBlank)?.let {
                        sessionStore.saveThreadId(ownerId, it)
                    }
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
                } else {
                    update { copy(historyLoading = false, historyLoaded = true) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                update { copy(historyLoading = false, historyLoaded = true) }
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
                val response = if (attachment == null) {
                    aiApi.sendMessage(
                        ChatRequest(
                            message = text,
                            threadId = state.value!!.threadId,
                            userId = ownerId?.toString()
                        )
                    )
                } else {
                    aiApi.chatWithAttachment(
                        text.toRequestBody("text/plain".toMediaTypeOrNull()),
                        state.value!!.threadId.toRequestBody("text/plain".toMediaTypeOrNull()),
                        attachmentPart(attachment)
                    )
                }
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    throw ChatHttpException(response.code())
                }
                body.threadId?.takeIf(String::isNotBlank)?.let {
                    sessionStore.saveThreadId(ownerId, it)
                }
                update { completeChatSend(body).copy(serviceAvailable = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!state.value!!.serviceAvailable &&
                    (error !is ChatHttpException || error.code >= 500)
                ) {
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
                val request = MeasurementImportRequest(
                    waterRegistries = preview.records.filter { it.kind.equals("water", true) }
                        .filter { !it.startHydrometer.isNullOrBlank() && !it.endHydrometer.isNullOrBlank() }
                        .map { WaterRegistry(it.registrationDate, it.startHydrometer!!, it.endHydrometer!!) },
                    energyRegistries = preview.records.filter { it.kind.equals("energy", true) }
                        .filter { !it.energyConsumption.isNullOrBlank() }
                        .map { EnergyRegistry(it.registrationDate, it.energyConsumption!!) }
                )
                if (request.waterRegistries.isEmpty() && request.energyRegistries.isEmpty()) {
                    throw IllegalStateException("Não há registros válidos para importar.")
                }
                val response = springApi.importMeasurements(request)
                if (!response.isSuccessful || response.body() == null) {
                    throw IOException("Não foi possível salvar os registros (HTTP ${response.code()}).")
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

    private class ChatHttpException(val code: Int) :
        IOException("Não foi possível enviar a mensagem (HTTP $code).")

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
