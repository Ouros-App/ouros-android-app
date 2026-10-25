package com.ouros.app.data.notification

import com.google.gson.annotations.SerializedName

data class DeviceTokenRequest(
    val token: String,
    val platform: String = "ANDROID"
)

data class DeviceTokenResponse(
    val message: String? = null
)

data class NotificationItemResponse(
    val id: Long,
    val title: String,
    val message: String,
    val type: String,
    @SerializedName("id_tip")
    val idTip: Long? = null,
    @SerializedName("data_payload")
    val dataPayload: String? = null,
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("read_at")
    val readAt: String? = null
)

data class NotificationPageResponse(
    val content: List<NotificationItemResponse> = emptyList(),
    @SerializedName("total_elements")
    val totalElementsSnake: Long? = null,
    val totalElements: Long = 0L,
    @SerializedName("total_pages")
    val totalPagesSnake: Int? = null,
    val totalPages: Int = 0,
    val number: Int = 0,
    @SerializedName("is_last")
    val isLast: Boolean? = null,
    val last: Boolean = false
) {
    val effectiveTotalElements: Long
        get() = totalElementsSnake ?: totalElements

    val effectiveTotalPages: Int
        get() = totalPagesSnake ?: totalPages

    val effectiveIsLast: Boolean
        get() = isLast ?: last
}

data class UnreadCountResponse(
    @SerializedName("unread_count")
    val unreadCountSnake: Long? = null,
    val unreadCount: Long = 0L
) {
    val count: Int
        get() = (unreadCountSnake ?: unreadCount).toInt()
}
