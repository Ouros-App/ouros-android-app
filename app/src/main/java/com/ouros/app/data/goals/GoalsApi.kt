package com.ouros.app.data.goals

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface GoalsApi {
    @GET("individual-goals")
    suspend fun getIndividualGoals(
        @Header("Authorization") authorization: String
    ): Response<List<GoalResponse>>

    @GET("state-goals")
    suspend fun getStateGoals(
        @Header("Authorization") authorization: String
    ): Response<List<GoalResponse>>

    @PATCH("individual-goals/{id}")
    suspend fun updateIndividualGoal(
        @Path("id") id: Long,
        @Header("Authorization") authorization: String,
        @Body request: UpdateIndividualGoalRequest
    ): Response<GoalResponse>

    @PATCH("state-goals/{id}")
    suspend fun updateStateGoal(
        @Path("id") id: Long,
        @Header("Authorization") authorization: String,
        @Body request: UpdateGoalStatusRequest
    ): Response<GoalResponse>

    @POST("individual-goals")
    suspend fun createIndividualGoal(
        @Header("Authorization") authorization: String,
        @Body request: CreateIndividualGoalRequest
    ): Response<GoalResponse>

    @DELETE("individual-goals/{id}")
    suspend fun deleteIndividualGoal(
        @Path("id") id: Long,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}