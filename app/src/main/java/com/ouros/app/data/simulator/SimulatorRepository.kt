package com.ouros.app.data.simulator

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response

sealed interface SimulatorResult<out T> {
    data class Success<T>(val data: T) : SimulatorResult<T>
    data class Error(val message: String, val canRetry: Boolean = true) : SimulatorResult<Nothing>
    data object AuthenticationRequired : SimulatorResult<Nothing>
}

class SimulatorRepository(private val api: SimulatorApi) {
    suspend fun loadInitialData(farmId: Long): SimulatorResult<SimulatorInitialData> = safeRequest {
        coroutineScope {
            val ranking = async { api.getFarmRanking(farmId).requiredBody() }
            val farm = async { api.getFarm(farmId).requiredBody() }
            SimulatorInitialData(
                currentCgi = ranking.await().score,
                chickens = farm.await().chickensNow ?: 0
            )
        }
    }

    suspend fun simulate(farmId: Long, targetCgi: Double): SimulatorResult<SimulatorRankingSimulationResponse> =
        safeRequest { api.simulateRanking(farmId, targetCgi).requiredBody() }

    private suspend fun <T> safeRequest(block: suspend () -> T): SimulatorResult<T> {
        return try {
            SimulatorResult.Success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: SimulatorHttpException) {
            when (error.statusCode) {
                401 -> SimulatorResult.AuthenticationRequired
                400, 422 -> SimulatorResult.Error("Confira os valores informados.", false)
                403 -> SimulatorResult.Error("O simulador está disponível somente para produtores rurais.", false)
                404 -> SimulatorResult.Error("Não foi possível encontrar os dados desta granja.", false)
                429 -> SimulatorResult.Error("Muitas tentativas. Aguarde e tente novamente.")
                in 500..599 -> SimulatorResult.Error("O serviço do simulador está indisponível. Tente novamente.")
                else -> SimulatorResult.Error("Não foi possível carregar o simulador.")
            }
        } catch (_: IOException) {
            SimulatorResult.Error("Sem conexão. Verifique sua internet.")
        } catch (_: Exception) {
            SimulatorResult.Error("Ocorreu um erro inesperado.")
        }
    }

    private fun <T> Response<T>.requiredBody(): T {
        if (!isSuccessful) throw SimulatorHttpException(code())
        return body() ?: throw SimulatorHttpException(code())
    }

    private class SimulatorHttpException(val statusCode: Int) : RuntimeException()
}