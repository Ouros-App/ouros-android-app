package com.ouros.app.data.record

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
            val water = async { api.getWater(farmId).requiredBody() }
            val energy = async { api.getEnergy(farmId).requiredBody() }
            val chicken = async { api.getChickenLeft(farmId).requiredBody() }
            val lots = async { api.getLots(farmId).requiredBody() }
            val farm = async { api.getFarm(farmId).requiredBody() }

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
            lotCost = lot?.cost?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty(),
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
        var draft = original
        val lotId = draft.lotId.toLong()
        val lotCost = draft.lotCost.money()
        val waterStart = draft.hydrometerStart.decimal()
        val waterEnd = draft.hydrometerEnd.decimal()
        val energy = draft.energyKwh.decimal()
        val entered = draft.chickensEntered.toInt()
        val left = draft.chickensLeft.toInt()
        val current = draft.chickensCurrent.toInt()

        api.updateLot(
            lotId,
            LotUpdateRequest(
                receivedChickens = entered,
                cost = lotCost.toDouble()
            )
        ).requireSuccess()

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
            ).requiredBody()
            draft = draft.copy(waterRecordId = created.id).also(draftStore::save)
        } else {
            api.updateWater(waterId, waterBody).requireSuccess()
        }

        val energyBody = EnergyRegistryUpdate(
            registrationDate = draft.energyDate,
            energyConsumption = energy
        )
        val energyId = draft.energyRecordId
        if (energyId == null) {
            val created = api.createEnergy(
                EnergyRegistryRequest(
                    registrationDate = draft.energyDate,
                    energyConsumption = energy,
                    farmId = farmId
                )
            ).requiredBody()
            draft = draft.copy(energyRecordId = created.id).also(draftStore::save)
        } else {
            api.updateEnergy(energyId, energyBody).requireSuccess()
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
                ).requiredBody()
                draft = draft.copy(chickenLeftRecordId = created.id).also(draftStore::save)
            }

            chickenId != null && left > 0 -> {
                api.updateChickenLeft(
                    chickenId,
                    ChickenLeftUpdate(
                        chickensCount = left,
                        exitDate = draft.waterDate
                    )
                ).requireSuccess()
            }

            chickenId != null -> {
                api.deleteChickenLeft(chickenId).requireSuccess()
                draft = draft.copy(chickenLeftRecordId = null).also(draftStore::save)
            }
        }

        api.updateFarm(
            farmId,
            FarmUpdateRequest(chickensNow = current)
        ).requireSuccess()

        draftStore.clear()
        Unit
    }

    private suspend fun <T> safe(block: suspend () -> T): RecordResult<T> {
        return try {
            RecordResult.Success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: RecordHttpException) {
            when (error.code) {
                401 -> RecordResult.AuthenticationRequired
                400, 422 -> RecordResult.Error("Confira os dados informados.", false)
                403 -> RecordResult.Error("Seu usuário não possui permissão para registrar dados.", false)
                404 -> RecordResult.Error("Fazenda, lote ou registro não encontrado.", false)
                409 -> RecordResult.Error("Os dados entram em conflito com um registro existente.", false)
                429 -> RecordResult.Error("Muitas tentativas. Aguarde e tente novamente.")
                else -> RecordResult.Error("O serviço está indisponível. Tente novamente.")
            }
        } catch (_: IOException) {
            RecordResult.Error("Sem conexão. Seus dados continuam salvos neste aparelho.")
        } catch (_: Exception) {
            RecordResult.Error("Ocorreu um erro inesperado. Seus dados continuam salvos.")
        }
    }

    private fun String.decimal(): BigDecimal {
        return trim().replace(',', '.').toBigDecimal()
    }

    private fun String.money(): BigDecimal {
        val cleaned = filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        val normalized = if (cleaned.contains(',')) {
            cleaned.replace(".", "").replace(',', '.')
        } else {
            cleaned
        }
        return normalized.toBigDecimal()
    }

    private fun <T> Response<T>.requiredBody(): T {
        if (!isSuccessful) throw RecordHttpException(code())
        return body() ?: throw RecordHttpException(code())
    }

    private fun Response<*>.requireSuccess() {
        if (!isSuccessful) throw RecordHttpException(code())
    }

    private class RecordHttpException(val code: Int) : RuntimeException()
}