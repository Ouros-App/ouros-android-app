package com.ouros.app.feature.secondary.chatbot

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import okhttp3.OkHttpClient
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ChatbotApi {
    @Multipart
    @POST("v1/chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Part("message") message: RequestBody,
        @Part("thread_id") threadId: RequestBody,
        @Part attachment: MultipartBody.Part?
    ): Response<ChatResponse>

    @POST("measurement-imports")
    suspend fun importMeasurements(
        @Header("Authorization") authorization: String,
        @Body request: MeasurementImportRequest
    ): Response<MeasurementImportResponse>
}

object ChatbotNetwork {
    val aiApi: ChatbotApi by lazy { api("https://ms-ai-server.discloud.app/") }
    val springApi: ChatbotApi by lazy { api("https://ms-spring-api.discloud.app/") }

    private fun api(baseUrl: String): ChatbotApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .callTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ChatbotApi::class.java)
}

data class ChatResponse(
    val response: String? = null,
    val message: String? = null,
    @SerializedName("thread_id") val threadId: String? = null,
    @SerializedName("measurement_import_preview") val preview: MeasurementImportPreview? = null
)

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
