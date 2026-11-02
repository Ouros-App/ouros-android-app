package com.ouros.app.data.chatbot

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AuthenticatedNetwork

/**
 * Contrato Retrofit de chatbot. Declara rotas, parâmetros e formatos HTTP esperados; a interpretação das respostas fica no repositório.
 */
interface ChatbotApi {
    @POST("v1/chat")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>

    @GET("v1/chat/{thread_id}/history")
    suspend fun getHistory(
        @Path("thread_id") threadId: String,
        @Query("user_id") userId: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("before") before: String? = null
    ): Response<HistoryResponse>

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    // Attachments keep using the existing multipart contract; text-only chat follows the JSON API.
    @Multipart
    @POST("v1/chat")
    suspend fun chatWithAttachment(
        @Part("message") message: RequestBody,
        @Part("thread_id") threadId: RequestBody,
        @Part attachment: MultipartBody.Part
    ): Response<ChatResponse>

    @POST("measurement-imports")
    suspend fun importMeasurements(@Body request: MeasurementImportRequest): Response<MeasurementImportResponse>
}

object ChatbotNetwork {
    private fun api(baseUrl: String): ChatbotApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(AuthenticatedNetwork.client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ChatbotApi::class.java)

    val aiApi: ChatbotApi by lazy { api(BuildConfig.AI_API_BASE_URL) }
    val springApi: ChatbotApi by lazy { api(BuildConfig.OUROS_API_BASE_URL) }

    fun repository(context: android.content.Context) =
        ChatbotRepository(context.applicationContext, aiApi, springApi)
}

data class ChatRequest(
    val message: String,
    @SerializedName("thread_id") val threadId: String? = null,
    @SerializedName("user_id") val userId: String? = null
)

data class ChatResponse(
    @SerializedName("thread_id") val threadId: String? = null,
    val message: String? = null,
    val response: String? = null,
    val agents: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val visualizations: List<ChatVisualization>? = null,
    @SerializedName("measurement_import_preview") val preview: MeasurementImportPreview? = null
)

data class HistoryResponse(
    @SerializedName("thread_id") val threadId: String,
    val messages: List<HistoryMessage>,
    @SerializedName("next_cursor") val nextCursor: String? = null
)

data class HistoryMessage(
    val role: String,
    val content: String,
    val visualizations: List<ChatVisualization>? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ChatVisualization(
    val type: String,
    val title: String,
    val charts: List<ChatVisualizationChart> = emptyList()
)

data class ChatVisualizationChart(
    val id: String,
    val title: String,
    @SerializedName("render_as") val renderAs: String,
    val html: String
)

data class HealthResponse(val status: String)

data class MeasurementImportPreview(
    val filename: String,
    @SerializedName("requires_confirmation") val requiresConfirmation: Boolean,
    val records: List<MeasurementImportRecord> = emptyList(),
    val issues: List<MeasurementImportIssue> = emptyList()
)

data class MeasurementImportRecord(
    val kind: String,
    @SerializedName("source_reference") val sourceReference: String,
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("start_hydrometer") val startHydrometer: String? = null,
    @SerializedName("end_hydrometer") val endHydrometer: String? = null,
    @SerializedName("energy_consumption") val energyConsumption: String? = null
)

data class MeasurementImportIssue(
    @SerializedName("source_reference") val sourceReference: String,
    val message: String
)

data class MeasurementImportRequest(
    @SerializedName("water_registries") val waterRegistries: List<WaterRegistry>,
    @SerializedName("energy_registries") val energyRegistries: List<EnergyRegistry>
)

data class WaterRegistry(
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("start_hydrometer") val startHydrometer: String,
    @SerializedName("end_hydrometer") val endHydrometer: String
)

data class EnergyRegistry(
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("energy_consumption") val energyConsumption: String
)

data class MeasurementImportResponse(
    @SerializedName("water_registries") val waterRegistries: List<Any> = emptyList(),
    @SerializedName("energy_registries") val energyRegistries: List<Any> = emptyList()
)
