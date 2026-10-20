package com.ouros.app.data.rank

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException

sealed interface RankResult<out T> {
    data class Success<T>(val data: T) : RankResult<T>
    data class Error(val message: String, val canRetry: Boolean = true) : RankResult<Nothing>
    data object AuthenticationRequired : RankResult<Nothing>
}

class RankRepository(
    private val api: RankAPI,
    private val farmOwnerRepository: FarmOwnerRepository,
    private val farmSessionStore: FarmSessionStore
) {
    suspend fun load(): RankResult<RankPodiumResponse> {
        return try {
            when (val farmResult = resolveFarmId()) {
                is RankResult.Success -> loadPodium(farmResult.data)
                is RankResult.Error -> farmResult
                RankResult.AuthenticationRequired -> RankResult.AuthenticationRequired
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            RankResult.Error("Sem conexão. Verifique sua internet.", true)
        } catch (_: Exception) {
            RankResult.Error("Ocorreu um erro inesperado ao carregar o ranking.", true)
        }
    }

    private suspend fun resolveFarmId(): RankResult<Long> {
        farmSessionStore.farmId()?.let {
            return RankResult.Success(it)
        }

        return when (val result = farmOwnerRepository.getMe()) {
            is AuthResult.Success -> {
                val owner = result.data
                val farmId = owner.farmId ?: return RankResult.Error(
                    "Não foi possível identificar a granja vinculada ao seu perfil.",
                    false
                )
                farmSessionStore.saveAccount(owner.id, farmId)
                RankResult.Success(farmId)
            }
            is AuthResult.Error -> {
                if (result.statusCode == 401) {
                    RankResult.AuthenticationRequired
                } else {
                    RankResult.Error(result.message ?: "Não foi possível identificar sua fazenda.", result.statusCode != 403)
                }
            }
        }
    }

    private suspend fun loadPodium(farmId: Long): RankResult<RankPodiumResponse> {
        val response = api.getPodium(farmId)
        val podium = response.body()

        if (response.isSuccessful && podium != null) {
            if (podium.currentEntry == null) {
                return RankResult.Error(
                    "Não foi possível identificar sua posição no ranking.",
                    false
                )
            }

            return RankResult.Success(
                podium.copy(
                    rankingPodium = podium.rankingPodium?.sortedBy {
                        it.rankPosition
                    }
                )
            )
        }

        return when (response.code()) {
            401 -> RankResult.AuthenticationRequired
            403 -> RankResult.Error(
                "O ranking está disponível somente para produtores rurais.",
                false
            )
            404 -> RankResult.Error(
                "Ainda não existe um ranking para esta granja.",
                false
            )
            429 -> RankResult.Error(
                "Muitas tentativas. Aguarde e tente novamente.",
                true
            )
            in 500..599 -> RankResult.Error(
                "O serviço de ranking está indisponível. Tente novamente.",
                true
            )
            else -> RankResult.Error(
                "Não foi possível carregar o ranking.",
                true
            )
        }
    }
}
