package com.ouros.app.data.record

import com.ouros.app.core.BrazilianMoney
import com.ouros.app.data.local.OfflineStore
import com.ouros.app.data.local.OfflineSynchronizer
import com.ouros.app.data.local.OfflineSyncRegistry
import com.ouros.app.data.session.FarmSessionStore
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
    private val draftStore: RecordDraftStore,
    private val offlineStore: OfflineStore? = null,
    private val manageDraft: Boolean = true,
    private val farmSessionStore: FarmSessionStore? = null
) : OfflineSynchronizer {

    init {
        if (offlineStore != null) OfflineSyncRegistry.register(this)
    }

    suspend fun load(farmId: Long): RecordResult<RecordHubData> {
        val result = safe {
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
            ).also { offlineStore?.cache(cacheKey(farmId), it) }
        }
        }
        return if (result is RecordResult.Error && result.canRetry) {
            offlineStore?.cached<RecordHubData>(cacheKey(farmId))?.let { RecordResult.Success(it) } ?: result
        } else result
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
        ).also(::saveDraft)
    }

    suspend fun submit(farmId: Long, original: RecordDraft): RecordResult<Unit> {
        val result = submitRemote(farmId, original)
        if (result is RecordResult.Error && offlineStore != null && !OfflineSyncRegistry.isOnline()) {
            offlineStore.enqueueReplacing(
                AREA,
                SUBMIT,
                "$farmId:${original.waterDate}:${original.energyDate}",
                PendingRecordSubmission(farmId, original)
            )
            updateCachedRecord(farmId, original)
            clearDraft()
            return RecordResult.Success(Unit)
        }
        return result
    }

    private suspend fun submitRemote(farmId: Long, original: RecordDraft): RecordResult<Unit> = safe {
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

            draft = draft.copy(waterRecordId = created.id).also(::saveDraft)
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

            draft = draft.copy(energyRecordId = created.id).also(::saveDraft)
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
                ).also(::saveDraft)
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
                ).also(::saveDraft)
            }
        }

        clearDraft()
        Unit
    }

    override suspend fun synchronize() {
        val store = offlineStore ?: return
        for (operation in store.pending(AREA)) {
            if (operation.operation != SUBMIT) continue
            val pending = store.decode(operation.payload, PendingRecordSubmission::class.java)
            val activeFarmId = farmSessionStore?.farmId()
            if (activeFarmId != null && activeFarmId != pending.farmId) continue
            val remoteRepository = RecordRepository(api, draftStore, manageDraft = false)
            when (val result = remoteRepository.submit(pending.farmId, pending.draft)) {
                is RecordResult.Success -> {
                    store.complete(operation.id)
                    when (val fresh = remoteRepository.load(pending.farmId)) {
                        is RecordResult.Success -> store.cache(cacheKey(pending.farmId), fresh.data)
                        else -> Unit
                    }
                }
                is RecordResult.AuthenticationRequired -> break
                is RecordResult.Error -> {
                    if (result.canRetry) {
                        store.markAttempt(operation.id)
                        break
                    } else {
                        store.complete(operation.id)
                    }
                }
            }
        }
    }

    private fun updateCachedRecord(farmId: Long, draft: RecordDraft) {
        val store = offlineStore ?: return
        val cached = store.cached<RecordHubData>(cacheKey(farmId)) ?: return
        runCatching {
            val temporaryId = -System.currentTimeMillis()
            val water = WaterRegistryResponse(
                id = draft.waterRecordId ?: temporaryId,
                registrationDate = draft.waterDate,
                startHydrometer = draft.hydrometerStart.decimal(),
                endHydrometer = draft.hydrometerEnd.decimal(),
                farmId = farmId
            )
            val energy = EnergyRegistryResponse(
                id = draft.energyRecordId ?: temporaryId - 1,
                registrationDate = draft.energyDate,
                energyConsumption = draft.energyKwh.decimal(),
                farmId = farmId
            )
            val updated = cached.copy(
                water = cached.water.filterNot { it.registrationDate == draft.waterDate } + water,
                energy = cached.energy.filterNot { it.registrationDate == draft.energyDate } + energy,
                chickenLeft = cached.chickenLeft.filterNot { it.exitDate == draft.waterDate } +
                    draft.chickensLeft.toInt().takeIf { it > 0 }?.let {
                        ChickenLeftResponse(
                            id = draft.chickenLeftRecordId ?: temporaryId - 2,
                            chickensCount = it,
                            exitDate = draft.waterDate,
                            farmId = farmId
                        )
                    }.let { listOfNotNull(it) },
                lots = cached.lots.map {
                    if (it.id == draft.lotId.toLong()) {
                        it.copy(
                            receivedChickens = draft.chickensEntered.toInt(),
                            cost = draft.lotCost.money().toDouble()
                        )
                    } else it
                }
            )
            store.cache(cacheKey(farmId), updated)
        }
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
        ).also(::saveDraft)
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

    private fun saveDraft(draft: RecordDraft) {
        if (manageDraft) draftStore.save(draft)
    }

    private fun clearDraft() {
        if (manageDraft) draftStore.clear()
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

    private data class PendingRecordSubmission(val farmId: Long, val draft: RecordDraft)

    private fun cacheKey(farmId: Long) = "records:$farmId"

    private companion object {
        const val AREA = "records"
        const val SUBMIT = "submit"
    }
}
