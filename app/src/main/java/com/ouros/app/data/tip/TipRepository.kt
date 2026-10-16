package com.ouros.app.data.tip

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TipRepository(
    private val api: TipApi = TipNetwork.api,
    private val enableMockFallback: Boolean = true
) {

    suspend fun getTipById(id: Long): Result<TipDetailResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.getTipById(id)
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val body = response?.body()
            if (response != null && response.isSuccessful && body != null) {
                Result.success(body)
            } else if (enableMockFallback) {
                Result.success(
                    TipDetailResponse(
                        id = id,
                        title = "Como diminuir a conta de água",
                        tip = """
                            O manejo hídrico eficiente em galpões de frango de corte pode reduzir o consumo de água em até 50% sem comprometer o ganho de peso e o bem-estar das aves.
                            
                            1. Monitoramento e Detecção de Vazamentos
                            Instale hidrômetros setoriais por galpão e realize inspeções diárias nas linhas de bebedouro nipple. Um único bico vazando pode desperdiçar até 100 litros de água por semana e estragar a cama do aviário.
                            
                            2. Modernização do Sistema de Dessedentação
                            Regule a altura dos bebedouros conforme a idade e estatura das aves. Mantenha a coluna de água na pressão correta recomendada pelo fabricante para evitar respingos.
                            
                            3. Otimização da Limpeza e Resfriamento
                            Utilize lavadoras de alta pressão e bicos reguláveis para higienização entre lotes. Na climatização evaporativa (cooling), faça a manutenção de placas e descarte de água programado para evitar depósitos minerais.
                            
                            4. Fontes Alternativas e Reutilização
                            Avalie a implantação de cisternas para captação de água da chuva nos telhados dos galpões, aproveitando essa água para nebulização externa e lavagem externa.
                        """.trimIndent(),
                        idFarm = 1L,
                        categories = listOf("Manejo", "Sustentabilidade", "Economia"),
                        totalReviews = 14,
                        averageRating = 4.8
                    )
                )
            } else {
                Result.failure(Exception("Falha ao buscar detalhes da dica: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun createReview(
        tipId: Long,
        score: Int,
        comment: String? = null
    ): Result<ReviewResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.createReview(
                    tipId = tipId,
                    request = ReviewRequest(score = score, comment = comment)
                )
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val body = response?.body()
            if (response != null && response.isSuccessful && body != null) {
                Result.success(body)
            } else if (enableMockFallback) {
                Result.success(
                    ReviewResponse(
                        id = System.currentTimeMillis(),
                        idTip = tipId,
                        idFarmOwner = 1L,
                        score = score,
                        comment = comment,
                        createdAt = "Hoje"
                    )
                )
            } else {
                Result.failure(Exception("Falha ao registrar avaliação: HTTP ${response?.code()}"))
            }
        }
    }

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(Exception("Sem conexão com o servidor. Verifique sua internet."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
