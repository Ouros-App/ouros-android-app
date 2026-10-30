package com.ouros.app.data.record

import com.ouros.app.core.BrazilianMoney
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response

sealed interface RecordResult<out T> {
    data class Success<T>(val data: T) : RecordResult<T>
    data class Error(val message: String, val canRetry: Boolean = true) : RecordResult<Nothing>
    data object AuthenticationRequired : RecordResult<Nothing>
}

class RecordRepository(
    private val api: RecordAPI,
    private val draftStore: RecordDraftStore
) {
    suspend fun load(farmId: Long): RecordResult<RecordHubData> = safe {
        coroutineScope {
            val water = async { api.getWater(farmId).requiredBody("carregar os registros de água") }
            val energy = async { api.getEnergy(farmId).requiredBody("carregar os registros de energia") }
            val chicken = async { api.getChickenLeft(farmId).requiredBody("carregar as saídas de galinhas") }
            val lots = async { api.getLots(farmId).requiredBody("carregar os lotes") }
            val farm = async { api.getFarm(farmId).requiredBody("carregar a fazenda") }

            RecordHubData(
                water = water.await(),
                energy = energy.await(),
                chickenLeft = chicken.await(),
                lots = lots.await(),
                farm = farm.await()
            )
        }
    }

    fun prepareEdit(item: RecordHistoryItem, hub: RecordHubData): RecordDraft {
        val isoDate = item.date.toString()
        val water = if (item.metric == RecordMetric.WATER) {
            hub.water.firstOrNull { it.id == item.id }
        } else {
            hub.water.firstOrNull { it.registrationDate == isoDate }
        }
        val energy = if (item.metric == RecordMetric.ENERGY) {
            hub.energy.firstOrNull { it.id == item.id }
        } else {
            hub.energy.firstOrNull { it.registrationDate == isoDate }
        }
        val chicken = hub.chickenLeft.firstOrNull { it.exitDate == isoDate }
        val lot = hub.lots
            .filter { it.deliveryDate.isNullOrBlank() }
            .maxByOrNull { it.id }
            ?: hub.lots.maxByOrNull { it.id }

        return RecordDraft(
            mode = RecordDraftMode.EDIT,
            step = 1,
            lotId = lot?.id?.toString().orEmpty(),
            lotCost = lot?.cost?.let {
                BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
            }.orEmpty(),
            waterDate = water?.registrationDate ?: isoDate,
            hydrometerStart = water?.startHydrometer?.toPlainString().orEmpty(),
            hydrometerEnd = water?.endHydrometer?.toPlainString().orEmpty(),
            energyDate = energy?.registrationDate ?: isoDate,
            energyKwh = energy?.energyConsumption?.toPlainString().orEmpty(),
            chickensEntered = lot?.receivedChickens?.toString().orEmpty(),
            chickensLeft = chicken?.chickensCount?.toString() ?: "0",
            chickensCurrent = hub.farm.chickensNow?.toString().orEmpty(),
            waterRecordId = water?.id,
            energyRecordId = energy?.id,
            chickenLeftRecordId = chicken?.id
        ).also(draftStore::save)
    }

    suspend fun submit(farmId: Long, original: RecordDraft): RecordResult<Unit> = safe {
        var draft = resolveExistingRecords(farmId, original)
        val lotId = draft.lotId.toLong()
        val lotCost = draft.lotCost.money()
        val waterStart = draft.hydrometerStart.decimal()
        val waterEnd = draft.hydrometerEnd.decimal()
        val energyConsumption = draft.energyKwh.decimal()
        val entered = draft.chickensEntered.toInt()
        val left = draft.chickensLeft.toInt()

        api.updateLot(
            lotId,
            LotUpdateRequest(
                receivedChickens = entered,
                cost = lotCost.toDouble()
            )
        ).requireSuccess("atualizar o lote")

        val waterBody = WaterRegistryUpdate(
            registrationDate = draft.waterDate,
            startHydrometer = waterStart,
            endHydrometer = waterEnd
        )
        val waterId = draft.waterRecordId

        if (waterId == null) {
            val created = api.createWater(
                WaterRegistryRequest(
                    registrationDate = draft.waterDate,
                    startHydrometer = waterStart,
                    endHydrometer = waterEnd,
                    farmId = farmId
                )
            ).requiredBody("registrar a água")

            draft = draft.copy(waterRecordId = created.id).also(draftStore::save)
        } else {
            api.updateWater(waterId, waterBody)
                .requireSuccess("atualizar o registro de água")
        }

        val energyBody = EnergyRegistryUpdate(
            registrationDate = draft.energyDate,
            energyConsumption = energyConsumption
        )
        val energyId = draft.energyRecordId

        if (energyId == null) {
            val created = api.createEnergy(
                EnergyRegistryRequest(
                    registrationDate = draft.energyDate,
                    energyConsumption = energyConsumption,
                    farmId = farmId
                )
            ).requiredBody("registrar a energia")

            draft = draft.copy(energyRecordId = created.id).also(draftStore::save)
        } else {
            api.updateEnergy(energyId, energyBody)
                .requireSuccess("atualizar o registro de energia")
        }

        val chickenId = draft.chickenLeftRecordId

        when {
            chickenId == null && left > 0 -> {
                val created = api.createChickenLeft(
                    ChickenLeftRequest(
                        chickensCount = left,
                        exitDate = draft.waterDate,
                        farmId = farmId
                    )
                ).requiredBody("registrar a saída de galinhas")

                draft = draft.copy(
                    chickenLeftRecordId = created.id
                ).also(draftStore::save)
            }

            chickenId != null && left > 0 -> {
                api.updateChickenLeft(
                    chickenId,
                    ChickenLeftUpdate(
                        chickensCount = left,
                        exitDate = draft.waterDate
                    )
                ).requireSuccess("atualizar a saída de galinhas")
            }

            chickenId != null -> {
                api.deleteChickenLeft(chickenId)
                    .requireSuccess("remover a saída de galinhas")

                draft = draft.copy(
                    chickenLeftRecordId = null
                ).also(draftStore::save)
            }
        }

        draftStore.clear()
        Unit
    }

    private suspend fun resolveExistingRecords(
        farmId: Long,
        original: RecordDraft
    ): RecordDraft = coroutineScope {
        val waterRequest = async {
            original.waterRecordId ?: api.getWater(farmId)
                .requiredBody("verificar os registros de água")
                .firstOrNull { it.registrationDate == original.waterDate }
                ?.id
        }

        val energyRequest = async {
            original.energyRecordId ?: api.getEnergy(farmId)
                .requiredBody("verificar os registros de energia")
                .firstOrNull { it.registrationDate == original.energyDate }
                ?.id
        }

        val chickenRequest = async {
            original.chickenLeftRecordId ?: api.getChickenLeft(farmId)
                .requiredBody("verificar as saídas de galinhas")
                .firstOrNull { it.exitDate == original.waterDate }
                ?.id
        }

        original.copy(
            waterRecordId = waterRequest.await(),
            energyRecordId = energyRequest.await(),
            chickenLeftRecordId = chickenRequest.await()
        ).also(draftStore::save)
    }

    private suspend fun <T> safe(block: suspend () -> T): RecordResult<T> {
        return try {
            RecordResult.Success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: RecordHttpException) {
            when (error.code) {
                401 -> RecordResult.AuthenticationRequired
                400, 422 -> RecordResult.Error(
                    "Confira os dados ao tentar ${error.operation}.",
                    false
                )
                403 -> RecordResult.Error(
                    "Seu usuário não tem permissão para ${error.operation}.",
                    false
                )
                404 -> RecordResult.Error(
                    "Não foi possível ${error.operation}: item não encontrado.",
                    false
                )
                409 -> RecordResult.Error(
                    "Já existe um registro para esses dados. Recarregue o histórico.",
                    false
                )
                429 -> RecordResult.Error(
                    "Muitas tentativas. Aguarde e tente novamente."
                )
                else -> RecordResult.Error(
                    "Não foi possível ${error.operation}. Tente novamente."
                )
            }
        } catch (_: IOException) {
            RecordResult.Error(
                "Sem conexão. Seus dados continuam salvos neste aparelho."
            )
        } catch (_: Exception) {
            RecordResult.Error(
                "Ocorreu um erro inesperado. Seus dados continuam salvos."
            )
        }
    }

    private fun String.decimal(): BigDecimal {
        return trim().replace(',', '.').toBigDecimal()
    }

    private fun String.money(): BigDecimal {
        return BrazilianMoney.parse(this)
            ?: throw IllegalArgumentException("Valor monetário inválido.")
    }

    private fun <T> Response<T>.requiredBody(operation: String): T {
        if (!isSuccessful) {
            throw RecordHttpException(code(), operation)
        }
        return body() ?: throw RecordHttpException(code(), operation)
    }

    private fun Response<*>.requireSuccess(operation: String) {
        if (!isSuccessful) {
            throw RecordHttpException(code(), operation)
        }
    }

    private class RecordHttpException(
        val code: Int,
        val operation: String
    ) : RuntimeException()
}