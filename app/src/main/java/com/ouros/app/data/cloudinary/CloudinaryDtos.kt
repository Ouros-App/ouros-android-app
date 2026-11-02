package com.ouros.app.data.cloudinary

import com.google.gson.annotations.SerializedName

/**
 * Modelos de dados de cloudinary. Os tipos abaixo espelham os campos usados no contrato da API ou no fluxo local; os nomes serializados mostram como cada propriedade é convertida para JSON.
 */
data class CloudinaryUploadResponse(
    @SerializedName("secure_url")
    val secureUrl: String? = null,

    @SerializedName("public_id")
    val publicId: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("error")
    val error: CloudinaryErrorResponse? = null
)

data class CloudinaryErrorResponse(
    @SerializedName("message")
    val message: String? = null
)
