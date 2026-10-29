package com.ouros.app.feature.secondary.chatbot

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SecureRefreshTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

class ChatbotViewModel internal constructor(
    application: Application,
    private val savedState: SavedStateHandle,
    private val aiApi: ChatbotApi,
    private val springApi: ChatbotApi
) : AndroidViewModel(application) {
    constructor(application: Application, savedState: SavedStateHandle) :
        this(application, savedState, ChatbotNetwork.aiApi, ChatbotNetwork.springApi)

    val state = MutableLiveData(ChatState(threadId = savedState.get<String>(THREAD_ID) ?: UUID.randomUUID().toString()))
    private val session = AppSession.sessionManager
    private val authRepository = AuthRepository(
        AuthNetwork.api,
        SecureRefreshTokenStore(application.applicationContext),
        session
    )

    init {
        savedState[THREAD_ID] = state.value!!.threadId
    }

    fun setAttachment(uri: Uri?) {
        update { copy(selectedAttachment = uri) }
    }

    fun discardAttachment() = setAttachment(null)

    fun send(
        message: String,
        attachment: Uri? = state.value?.selectedAttachment,
        appendMessage: Boolean = true
    ) {
        val text = message.trim().ifEmpty { if (attachment != null) "Analise o arquivo anexado." else "" }
        if (text.isEmpty() || state.value!!.sending || state.value!!.preview != null) return
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
                val filePart = attachment?.let { uri ->
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "arquivo"
                    val body = object : RequestBody() {
                        override fun contentType() = resolver.getType(uri)?.toMediaTypeOrNull()
                        override fun contentLength() = -1L
                        override fun writeTo(sink: BufferedSink) {
                            val input = resolver.openInputStream(uri)
                                ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
                            input.use { it.copyTo(sink.outputStream()) }
                        }
                    }
                    MultipartBody.Part.createFormData("attachment", name, body)
                }
                val response = authorized { authorization ->
                    aiApi.chat(
                        authorization,
                        text.toRequestBody("text/plain".toMediaTypeOrNull()),
                        state.value!!.threadId.toRequestBody("text/plain".toMediaTypeOrNull()),
                        filePart
                    )
                }
                val body = response.body()
                if (!response.isSuccessful || body == null) throw IOException("Não foi possível enviar a mensagem (HTTP ${response.code()}).")
                update { completeChatSend(body) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update { copy(sending = false, error = error.localizedMessage ?: "Falha ao enviar. Tente novamente.") }
            }
        }
    }

    fun confirmImport() {
        val preview = state.value!!.preview ?: return
        if (
            state.value!!.importing
            || state.value!!.imported
            || state.value!!.importAttemptFailed
        ) return
        update { copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                val request = MeasurementImportRequest(
                    waterRegistries = preview.records
                        .filter { it.kind.equals("water", true) }
                        .filter {
                            !it.startHydrometer.isNullOrBlank() &&
                                !it.endHydrometer.isNullOrBlank()
                        }
                        .map {
                            WaterRegistry(
                                it.registrationDate,
                                it.startHydrometer!!,
                                it.endHydrometer!!
                            )
                        },
                    energyRegistries = preview.records
                        .filter { it.kind.equals("energy", true) }
                        .filter { !it.energyConsumption.isNullOrBlank() }
                        .map {
                            EnergyRegistry(
                                it.registrationDate,
                                it.energyConsumption!!
                            )
                        }
                )
                if (request.waterRegistries.isEmpty() && request.energyRegistries.isEmpty()) {
                    throw IllegalStateException("Não há registros válidos para importar.")
                }
                val response = authorized { authorization ->
                    springApi.importMeasurements(authorization, request)
                }
                if (!response.isSuccessful || response.body() == null) {
                    throw IOException(
                        "Não foi possível salvar os registros (HTTP ${response.code()})."
                    )
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
        } else {
            "Importação cancelada. Nenhum registro foi salvo."
        }
        update {
            copy(
                preview = null,
                error = null,
                importAttemptFailed = false,
                messages = messages + ChatLine(message, false)
            )
        }
    }

    fun retry() {
        val current = state.value!!
        if (current.preview != null) confirmImport()
        else send(
            current.messages.lastOrNull { it.user }?.text.orEmpty(),
            current.selectedAttachment,
            appendMessage = false
        )
    }

    private inline fun update(transform: ChatState.() -> ChatState) {
        state.value = state.value?.transform()
    }

    private suspend fun <T> authorized(call: suspend (String) -> retrofit2.Response<T>): retrofit2.Response<T> {
        var token = session.accessToken()
        if (token.isNullOrBlank()) {
            val refresh = authRepository.refreshSession()
            if (refresh !is AuthResult.Success) throw IOException("Sua sessão expirou. Entre novamente para continuar.")
            token = session.accessToken()
        }
        if (token.isNullOrBlank()) throw IOException("Sua sessão expirou. Entre novamente para continuar.")
        var response = call("Bearer $token")
        if (response.code() == 401) {
            val refresh = authRepository.refreshSession()
            if (refresh !is AuthResult.Success) throw IOException("Sua sessão expirou. Entre novamente para continuar.")
            token = session.accessToken() ?: throw IOException("Sua sessão expirou. Entre novamente para continuar.")
            response = call("Bearer $token")
        }
        return response
    }

    companion object { private const val THREAD_ID = "chat_thread_id" }
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
    val error: String? = null
)
data class ChatLine(val text: String, val user: Boolean)

internal fun ChatState.completeChatSend(response: ChatResponse): ChatState = copy(
    messages = messages + ChatLine(
        response.message ?: response.response ?: "Recebi sua mensagem.",
        false
    ),
    sending = false,
    preview = response.preview,
    imported = false,
    importAttemptFailed = false,
    selectedAttachment = null,
    error = null
)

