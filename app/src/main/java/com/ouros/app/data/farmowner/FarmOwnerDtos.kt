package com.ouros.app.data.farmowner

import com.google.gson.annotations.SerializedName

data class FarmOwnerResponse(
    val id: Long?,
    val name: String?,

    @SerializedName("document_number")
    val documentNumber: String?,

    val email: String?,
    val telephone: String?,

    @SerializedName("id_farm")
    val farmId: Long?,

    @SerializedName("first_access")
    val firstAccess: Boolean?,

    @SerializedName("foto_url")
    val photoUrl: String?
)

data class FarmOwnerUpdateRequest(
    val name: String? = null,

    @SerializedName("document_number")
    val documentNumber: String? = null,

    val email: String? = null,
    val telephone: String? = null,
    val password: String? = null,

    @SerializedName("first_access")
    val firstAccess: Boolean? = null,

    @SerializedName("foto_url")
    val photoUrl: String? = null,

    @SerializedName("id_farm")
    val farmId: Long? = null,

    val latitude: Double? = null,
    val longitude: Double? = null
)