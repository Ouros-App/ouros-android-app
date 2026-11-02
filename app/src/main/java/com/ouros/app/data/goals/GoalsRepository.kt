package com.ouros.app.data.goals

import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.data.local.OfflineStore
import com.ouros.app.data.local.OfflineSynchronizer
import com.ouros.app.data.local.OfflineSyncRegistry
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface GoalsResult<out T> {
    data class Success<T>(val data: T) : GoalsResult<T>
    data class Error(val message: String, val canRetry: Boolean) : GoalsResult<Nothing>
    data object AuthenticationRequired : GoalsResult<Nothing>
}

/**
 * Coordena leitura e alteração de metas entre API e fila offline, convertendo respostas e estados remotos para o modelo usado pela tela.
 */
class GoalsRepository(
    private val api: GoalsApi,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val farmSessionStore: FarmSessionStore,
    private val offlineStore: OfflineStore? = null
) : OfflineSynchronizer {

    init {
        if (offlineStore != null) OfflineSyncRegistry.register(this)
    }

    suspend fun load(): GoalsResult<GoalsPayload> {
        val result = loadRemote()
        if (result is GoalsResult.Success) offlineStore?.cache(CACHE_KEY, result.data)
        return if (result is GoalsResult.Error && result.canRetry) {
            offlineStore?.cached<GoalsPayload>(CACHE_KEY)?.let { GoalsResult.Success(it) } ?: result
        } else result
    }

    private suspend fun loadRemote(): GoalsResult<GoalsPayload> = safeRequest<GoalsPayload> {
        when (val personalCall = authenticatedCall { api.getIndividualGoals(it) }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                val personalResponse = personalCall.response
                val personalGoals = personalResponse.body()

                if (!personalResponse.isSuccessful || personalGoals == null) {
                    return@safeRequest responseError(
                        personalResponse.code(),
                        "Não foi possível carregar as metas pessoais."
                    )
                }

                when (val stateCall = authenticatedCall { api.getStateGoals(it) }) {
                    ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
                    ApiCallResult.TemporaryFailure -> temporaryError()
                    is ApiCallResult.ResponseResult -> {
                        val stateResponse = stateCall.response
                        val stateGoals = stateResponse.body()

                        if (!stateResponse.isSuccessful || stateGoals == null) {
                            return@safeRequest responseError(
                                stateResponse.code(),
                                "Não foi possível carregar as metas estaduais."
                            )
                        }

                        GoalsResult.Success(
                            GoalsPayload(
                                personalGoals = personalGoals,
                                stateGoals = stateGoals
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun updateStatus(
        origin: GoalOrigin,
        goal: GoalResponse,
        completed: Boolean
    ): GoalsResult<GoalResponse> {
        val result = updateStatusRemote(origin, goal, completed)
        if (result is GoalsResult.Success) updateCachedGoal(origin, result.data)
        if (result is GoalsResult.Error && offlineStore != null && !OfflineSyncRegistry.isOnline()) {
            val updated = goal.copy(
                status = if (completed) GoalResponse.COMPLETED else GoalResponse.PENDING
            )
            offlineStore.enqueueReplacing(
                area(),
                UPDATE_STATUS,
                "${origin.name}:${goal.id}",
                PendingGoalOperation(origin = origin, goal = updated)
            )
            updateCachedGoal(origin, updated)
            return GoalsResult.Success(updated)
        }
        return result
    }

    private suspend fun updateStatusRemote(
        origin: GoalOrigin,
        goal: GoalResponse,
        completed: Boolean
    ): GoalsResult<GoalResponse> = safeRequest<GoalResponse> {
        val newStatus = if (completed) GoalResponse.COMPLETED else GoalResponse.PENDING

        val result = when (origin) {
            GoalOrigin.PERSONAL -> authenticatedCall {
                api.updateIndividualGoal(
                    id = goal.id,
                    authorization = it,
                    request = UpdateIndividualGoalRequest(status = newStatus)
                )
            }

            GoalOrigin.STATE -> authenticatedCall {
                api.updateStateGoal(
                    id = goal.id,
                    authorization = it,
                    request = UpdateGoalStatusRequest(status = newStatus)
                )
            }
        }

        when (result) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                val response = result.response

                if (response.isSuccessful) {
                    GoalsResult.Success(
                        response.body() ?: goal.copy(status = newStatus)
                    )
                } else {
                    responseError(
                        response.code(),
                        "Não foi possível atualizar a meta."
                    )
                }
            }
        }
    }

    suspend fun create(
        title: String,
        description: String?,
        type: String = "Outro",
        targetValue: Double = 1.0
    ): GoalsResult<GoalResponse> {
        val result = createRemote(title, description, type, targetValue)
        if (result is GoalsResult.Success) addCachedGoal(result.data)
        if (result is GoalsResult.Error && offlineStore != null && !OfflineSyncRegistry.isOnline()) {
            val localGoal = GoalResponse(
                id = -System.currentTimeMillis(),
                title = title.trim(),
                description = description?.trim()?.takeIf(String::isNotEmpty),
                type = type.trim().ifEmpty { "Outro" },
                targetValue = if (targetValue > 0.0) targetValue else 1.0,
                farmId = farmSessionStore.farmId()
            )
            offlineStore.enqueue(
                area(),
                CREATE,
                localGoal.id.toString(),
                PendingGoalOperation(origin = GoalOrigin.PERSONAL, goal = localGoal)
            )
            addCachedGoal(localGoal)
            return GoalsResult.Success(localGoal)
        }
        return result
    }

    private suspend fun createRemote(
        title: String,
        description: String?,
        type: String,
        targetValue: Double
    ): GoalsResult<GoalResponse> = safeRequest<GoalResponse> {
        val farmId = farmSessionStore.farmId()
            ?: return@safeRequest GoalsResult.Error(
                message = "Não foi possível identificar a granja. Tente novamente.",
                canRetry = true
            )

        val request = CreateIndividualGoalRequest(
            title = title.trim(),
            description = description?.trim()?.takeIf(String::isNotEmpty),
            farmId = farmId,
            type = type.trim().ifEmpty { "Outro" },
            status = GoalResponse.PENDING,
            targetValue = if (targetValue > 0.0) targetValue else 1.0
        )

        when (val result = authenticatedCall {
            api.createIndividualGoal(it, request)
        }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> readGoal(
                result.response,
                "Não foi possível adicionar a meta."
            )
        }
    }

    suspend fun updateDetails(
        goal: GoalResponse,
        title: String,
        description: String
    ): GoalsResult<GoalResponse> {
        val result = updateDetailsRemote(goal, title, description)
        if (result is GoalsResult.Success) updateCachedGoal(GoalOrigin.PERSONAL, result.data)
        if (result is GoalsResult.Error && offlineStore != null && !OfflineSyncRegistry.isOnline()) {
            val updated = goal.copy(title = title.trim(), description = description.trim())
            offlineStore.enqueueReplacing(
                area(),
                UPDATE_DETAILS,
                goal.id.toString(),
                PendingGoalOperation(origin = GoalOrigin.PERSONAL, goal = updated)
            )
            updateCachedGoal(GoalOrigin.PERSONAL, updated)
            return GoalsResult.Success(updated)
        }
        return result
    }

    private suspend fun updateDetailsRemote(
        goal: GoalResponse,
        title: String,
        description: String
    ): GoalsResult<GoalResponse> = safeRequest<GoalResponse> {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val request = UpdateIndividualGoalRequest(
            title = cleanTitle,
            description = cleanDescription
        )

        when (val result = authenticatedCall {
            api.updateIndividualGoal(
                id = goal.id,
                authorization = it,
                request = request
            )
        }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                val response = result.response

                if (response.isSuccessful) {
                    GoalsResult.Success(
                        response.body() ?: goal.copy(
                            title = cleanTitle,
                            description = cleanDescription
                        )
                    )
                } else {
                    responseError(
                        response.code(),
                        "Não foi possível salvar a meta."
                    )
                }
            }
        }
    }

    suspend fun delete(id: Long): GoalsResult<Unit> {
        val result = deleteRemote(id)
        if (result is GoalsResult.Success) removeCachedGoal(id)
        if (result is GoalsResult.Error && offlineStore != null && !OfflineSyncRegistry.isOnline()) {
            offlineStore.enqueueReplacing(
                area(),
                DELETE,
                id.toString(),
                PendingGoalOperation(
                    origin = GoalOrigin.PERSONAL,
                    goal = GoalResponse(id = id, title = "", description = null)
                )
            )
            removeCachedGoal(id)
            return GoalsResult.Success(Unit)
        }
        return result
    }

    private suspend fun deleteRemote(id: Long): GoalsResult<Unit> = safeRequest<Unit> {
        when (val result = authenticatedCall {
            api.deleteIndividualGoal(id, it)
        }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                if (result.response.isSuccessful) {
                    GoalsResult.Success(Unit)
                } else {
                    responseError(
                        result.response.code(),
                        "Não foi possível excluir a meta."
                    )
                }
            }
        }
    }

    override suspend fun synchronize() {
        val store = offlineStore ?: return
        val remote = GoalsRepository(api, authRepository, sessionManager, farmSessionStore)
        val area = area()
        for (operation in store.pending(area)) {
            val pending = store.decode(operation.payload, PendingGoalOperation::class.java)
            val originalId = pending.goal.id
            val remoteId = if (originalId < 0) store.mappedId(area, originalId) else originalId
            val result: GoalsResult<*> = when (operation.operation) {
                CREATE -> remote.createRemote(
                    pending.goal.title,
                    pending.goal.description,
                    pending.goal.type,
                    pending.goal.targetValue
                )
                UPDATE_STATUS -> {
                    if (remoteId == null) break
                    val resolved = pending.goal.copy(id = remoteId)
                    remote.updateStatusRemote(pending.origin, resolved, resolved.isCompleted)
                }
                UPDATE_DETAILS -> {
                    if (remoteId == null) break
                    val resolved = pending.goal.copy(id = remoteId)
                    remote.updateDetailsRemote(
                        resolved,
                        resolved.title,
                        resolved.description.orEmpty()
                    )
                }
                DELETE -> {
                    if (remoteId == null) break
                    remote.deleteRemote(remoteId)
                }
                else -> GoalsResult.Error("Operação offline desconhecida.", false)
            }

            when (result) {
                is GoalsResult.Success -> {
                    if (operation.operation == CREATE) {
                        val created = result.data as GoalResponse
                        store.saveMapping(area, originalId, created.id)
                    }
                    store.complete(operation.id)
                }
                GoalsResult.AuthenticationRequired -> break
                is GoalsResult.Error -> {
                    if (result.canRetry) {
                        store.markAttempt(operation.id)
                        break
                    } else {
                        store.complete(operation.id)
                    }
                }
            }
        }

        when (val refreshed = remote.loadRemote()) {
            is GoalsResult.Success -> store.cache(CACHE_KEY, refreshed.data)
            else -> Unit
        }
    }

    private fun updateCachedGoal(origin: GoalOrigin, goal: GoalResponse) {
        val store = offlineStore ?: return
        val payload = store.cached<GoalsPayload>(CACHE_KEY) ?: return
        store.cache(
            CACHE_KEY,
            when (origin) {
                GoalOrigin.PERSONAL -> payload.copy(
                    personalGoals = payload.personalGoals.map { if (it.id == goal.id) goal else it }
                )
                GoalOrigin.STATE -> payload.copy(
                    stateGoals = payload.stateGoals.map { if (it.id == goal.id) goal else it }
                )
            }
        )
    }

    private fun addCachedGoal(goal: GoalResponse) {
        val store = offlineStore ?: return
        val payload = store.cached<GoalsPayload>(CACHE_KEY) ?: GoalsPayload(emptyList(), emptyList())
        store.cache(CACHE_KEY, payload.copy(personalGoals = payload.personalGoals + goal))
    }

    private fun removeCachedGoal(id: Long) {
        val store = offlineStore ?: return
        val payload = store.cached<GoalsPayload>(CACHE_KEY) ?: return
        store.cache(
            CACHE_KEY,
            payload.copy(personalGoals = payload.personalGoals.filterNot { it.id == id })
        )
    }

    private fun readGoal(
        response: Response<GoalResponse>,
        fallback: String
    ): GoalsResult<GoalResponse> {
        val goal = response.body()

        return if (response.isSuccessful && goal != null) {
            GoalsResult.Success(goal)
        } else {
            responseError(response.code(), fallback)
        }
    }

    private fun responseError(code: Int, fallback: String): GoalsResult.Error {
        return when (code) {
            400, 422 -> GoalsResult.Error(
                "Os dados enviados são inválidos.",
                false
            )

            403 -> GoalsResult.Error(
                "Você não possui permissão para realizar esta ação.",
                false
            )

            404 -> GoalsResult.Error(
                "A meta não foi encontrada.",
                false
            )

            429 -> GoalsResult.Error(
                "Muitas tentativas. Aguarde e tente novamente.",
                true
            )

            in 500..599 -> GoalsResult.Error(
                "O serviço está indisponível. Tente novamente.",
                true
            )

            else -> GoalsResult.Error(fallback, true)
        }
    }

    private fun temporaryError() = GoalsResult.Error(
        message = "Não foi possível validar sua sessão. Verifique sua conexão e tente novamente.",
        canRetry = true
    )

    private suspend fun <T> authenticatedCall(
        request: suspend (String) -> Response<T>
    ): ApiCallResult<T> {
        when (val authorization = ensureAuthorization()) {
            AuthorizationResult.AuthenticationRequired ->
                return ApiCallResult.AuthenticationRequired

            AuthorizationResult.TemporaryFailure ->
                return ApiCallResult.TemporaryFailure

            is AuthorizationResult.Ready -> {
                val response = request(authorization.header)

                if (response.code() != 401) {
                    return ApiCallResult.ResponseResult(response)
                }
            }
        }

        return when (val authorization = refreshAuthorization()) {
            AuthorizationResult.AuthenticationRequired ->
                ApiCallResult.AuthenticationRequired

            AuthorizationResult.TemporaryFailure ->
                ApiCallResult.TemporaryFailure

            is AuthorizationResult.Ready -> {
                val response = request(authorization.header)

                if (response.code() == 401) {
                    authRepository.clearSession()
                    ApiCallResult.AuthenticationRequired
                } else {
                    ApiCallResult.ResponseResult(response)
                }
            }
        }
    }

    private suspend fun ensureAuthorization(): AuthorizationResult {
        val token = sessionManager.accessToken()

        return if (token.isNullOrBlank()) {
            refreshAuthorization()
        } else {
            AuthorizationResult.Ready("Bearer $token")
        }
    }

    private suspend fun refreshAuthorization(): AuthorizationResult {
        return when (val result = authRepository.refreshSession()) {
            is AuthResult.Success -> {
                val token = sessionManager.accessToken()

                if (token.isNullOrBlank()) {
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.Ready("Bearer $token")
                }
            }

            is AuthResult.Error -> {
                if (
                    result.statusCode == 401 ||
                    result.message == NO_SAVED_SESSION_MESSAGE
                ) {
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.TemporaryFailure
                }
            }
        }
    }

    private suspend fun <T> safeRequest(
        block: suspend () -> GoalsResult<T>
    ): GoalsResult<T> {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            GoalsResult.Error(
                "Sem conexão. Verifique sua internet.",
                true
            )
        } catch (_: Exception) {
            GoalsResult.Error(
                "Ocorreu um erro inesperado.",
                true
            )
        }
    }

    private sealed interface AuthorizationResult {
        data class Ready(val header: String) : AuthorizationResult
        data object AuthenticationRequired : AuthorizationResult
        data object TemporaryFailure : AuthorizationResult
    }

    private sealed interface ApiCallResult<out T> {
        data class ResponseResult<T>(
            val response: Response<T>
        ) : ApiCallResult<T>

        data object AuthenticationRequired : ApiCallResult<Nothing>
        data object TemporaryFailure : ApiCallResult<Nothing>
    }

    private companion object {
        const val NO_SAVED_SESSION_MESSAGE = "Nenhuma sessão salva."
        const val AREA_PREFIX = "goals"
        const val CREATE = "create"
        const val UPDATE_STATUS = "update_status"
        const val UPDATE_DETAILS = "update_details"
        const val DELETE = "delete"
    }
    private data class PendingGoalOperation(
        val origin: GoalOrigin,
        val goal: GoalResponse
    )

    private fun area() = "$AREA_PREFIX:${farmSessionStore.farmId() ?: "unknown"}"

    private val CACHE_KEY: String
        get() = "${area()}:cache"
}
