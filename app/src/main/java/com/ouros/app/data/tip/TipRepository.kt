package com.ouros.app.data.tip

import com.ouros.app.data.local.OfflineStore
import com.ouros.app.data.local.OfflineSynchronizer
import com.ouros.app.data.local.OfflineSyncRegistry
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TipRepository(
    private val api: TipApi = TipNetwork.api,
    private val enableMockFallback: Boolean = true,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val offlineStore: OfflineStore? = null,
    private val farmSessionStore: FarmSessionStore? = null
) : OfflineSynchronizer {

    init {
        if (offlineStore != null) OfflineSyncRegistry.register(this)
    }

    suspend fun getTipById(id: Long): Result<TipDetailResponse> = withContext(ioDispatcher) {
        safeCall {
            val response = try {
                api.getTipById(id)
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val body = response?.body()
            val cached = offlineStore?.cached<TipDetailResponse>(tipCacheKey(id))
            if (response != null && response.isSuccessful && body != null) {
                offlineStore?.cache(tipCacheKey(id), body)
                Result.success(body)
            } else if (cached != null) {
                Result.success(cached)
            } else if (enableMockFallback) {
                Result.success(
                    TipDetailResponse(
                        id = id,
                        title = "Como diminuir a conta de água",
                        tip = """
                            Para diminuir a conta de água de uma granja, o segredo está em implementar um plano de gerenciamento hídrico focado no controle de perdas, modernização de equipamentos e reutilização, o que pode reduzir o consumo de água em até 50%. Em atividades agropecuárias (como avicultura e suinocultura), a água é usada principalmente para a dessedentação dos animais, limpeza das instalações e sistemas de resfriamento térmico.

                            1. Monitoramento e Detecção de Vazamentos
                            • Instalação de hidrômetros: Coloque medidores em diferentes setores da granja (galpões, área de lavagem, administrativo). Isso ajuda a identificar o consumo exato de cada processo e a notar picos anômalos que indicam vazamentos

                            • Manutenção preventiva: Um único bico nipple com defeito ou uma boia desregulada pode desperdiçar milhares de litros por mês. Faça vistorias semanais nas tubulações e conexões.

                            2. Modernização do Sistema de Dessedentação (Bebedouros)
                            • Regulagem de altura e pressão: Ajuste a altura dos bebedouros constantemente de acordo com a idade e o crescimento dos animais. Se o bebedouro estiver muito baixo ou com pressão inadequada, as aves ou suínos derramam mais água do que consomem.
                            • Uso de bicos Nipple de alta qualidade: Substitua calhas ou bebedouros pendulares antigos por sistemas nipple modernos, que liberam água apenas quando o animal aciona o pino, reduzindo drasticamente o desperdício por respingo.

                            3. Otimização da Limpeza e Resfriamento
                            • Lavagem com alta pressão e baixa vazão: Na hora de higienizar os galpões, utilize lavadoras de alta pressão com bicos e boquilhas apropriados. Muitas granjas usam mangueiras comuns que gastam muita água sem a pressão necessária para remover a sujeira com rapidez.
                            • Automação do sistema de nebulização: Monitore os sistemas de resfriamento por placas evaporativas (cooling) ou nebulizadores. Eles devem ser acionados por sensores automáticos baseados na temperatura e umidade relativa do ar, evitando que fiquem ligados além do necessário.

                            4. Fontes Alternativas e Reutilização
                            • Captação de água da chuva: Instale calhas nas grandes coberturas dos galpões para direcionar a água da chuva para cisternas ou tanques de armazenamento. Essa água, após tratamento básico, é ideal para a lavagem de pisos, instalações e maquinários.
                            • Sistemas de tratamento de efluentes para reúso: Implementar a reciclagem da água de lavagem através de tanques de decantação ou sistemas biológicos pode recuperar até 70% da água utilizada na produção, gerando grande alívio financeiro.
                        """.trimIndent(),
                        idFarm = 1L,
                        categories = listOf("Manejo", "Sustentabilidade", "Economia"),
                        totalReviews = 14,
                        averageRating = 3.5
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
    ): Result<ReviewResponse> = withContext(ioDispatcher) {
        safeCall {
            val validComment = comment?.takeIf { it.isNotBlank() } ?: "Avaliação enviada pelo aplicativo"
            val response = try {
                api.createReview(
                    tipId = tipId,
                    request = ReviewRequest(score = score, comment = validComment)
                )
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val body = response?.body()
            if (response != null && response.isSuccessful && body != null) {
                Result.success(body)
            } else if (offlineStore != null && response == null) {
                val pending = PendingReview(tipId, score, validComment)
                offlineStore.enqueue(area(), CREATE_REVIEW, tipId.toString(), pending)
                Result.success(pending.optimisticResponse())
            } else if (enableMockFallback) {
                Result.success(
                    ReviewResponse(
                        id = System.currentTimeMillis(),
                        idTip = tipId,
                        idFarmOwner = 1L,
                        score = score,
                        comment = validComment,
                        createdAt = "Hoje"
                    )
                )
            } else {
                Result.failure(Exception("Falha ao registrar avaliação: HTTP ${response?.code()}"))
            }
        }
    }

    override suspend fun synchronize() = withContext(ioDispatcher) {
        val store = offlineStore ?: return@withContext
        for (operation in store.pending(area())) {
            if (operation.operation != CREATE_REVIEW) continue
            val pending = store.decode(operation.payload, PendingReview::class.java)
            try {
                val response = api.createReview(
                    pending.tipId,
                    ReviewRequest(pending.score, pending.comment)
                )
                when {
                    response.isSuccessful -> store.complete(operation.id)
                    response.code() in 400..499 && response.code() != 429 ->
                        store.complete(operation.id)
                    else -> {
                        store.markAttempt(operation.id)
                        break
                    }
                }
            } catch (_: IOException) {
                store.markAttempt(operation.id)
                break
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

    private data class PendingReview(val tipId: Long, val score: Int, val comment: String) {
        fun optimisticResponse() = ReviewResponse(
            id = -System.currentTimeMillis(),
            idTip = tipId,
            score = score,
            comment = comment,
            createdAt = "Pendente de sincronização"
        )
    }

    private fun tipCacheKey(id: Long) = "tip:$id"

    private companion object {
        const val AREA_PREFIX = "tips"
        const val CREATE_REVIEW = "create_review"
    }

    private fun area() = "$AREA_PREFIX:${farmSessionStore?.farmId() ?: "unknown"}"
}
