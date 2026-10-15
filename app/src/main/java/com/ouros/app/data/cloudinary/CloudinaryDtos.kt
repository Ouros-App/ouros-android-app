package com.ouros.app.data.cloudinary

import com.google.gson.annotations.SerializedName

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
