package com.ouros.app.data.notification

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {

    @POST("notifications/device-token")
    suspend fun registerDeviceToken(
        @Body request: DeviceTokenRequest
    ): Response<Unit>

    @DELETE("notifications/device-token/{token}")
    suspend fun deleteDeviceToken(
        @Path("token") token: String
    ): Response<Unit>

    @GET("notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<NotificationPageResponse>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountResponse>

    @PATCH("notifications/{id}/read")
    suspend fun markAsRead(
        @Path("id") id: Long
    ): Response<Unit>

    @PATCH("notifications/read-all")
    suspend fun markAllAsRead(): Response<Unit>
}
