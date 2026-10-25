package com.ouros.app.data.record

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class WaterRegistryResponse(
    val id: Long,
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("start_hydrometer") val startHydrometer: BigDecimal,
    @SerializedName("end_hydrometer") val endHydrometer: BigDecimal,
    @SerializedName("id_farm") val farmId: Long
)

data class WaterRegistryRequest(
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("start_hydrometer") val startHydrometer: BigDecimal,
    @SerializedName("end_hydrometer") val endHydrometer: BigDecimal,
    @SerializedName("id_farm") val farmId: Long
)

data class WaterRegistryUpdate(
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("start_hydrometer") val startHydrometer: BigDecimal,
    @SerializedName("end_hydrometer") val endHydrometer: BigDecimal
)

data class EnergyRegistryResponse(
    val id: Long,
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("energy_consumption") val energyConsumption: BigDecimal,
    @SerializedName("id_farm") val farmId: Long
)

data class EnergyRegistryRequest(
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("energy_consumption") val energyConsumption: BigDecimal,
    @SerializedName("id_farm") val farmId: Long
)

data class EnergyRegistryUpdate(
    @SerializedName("registration_date") val registrationDate: String,
    @SerializedName("energy_consumption") val energyConsumption: BigDecimal
)

data class ChickenLeftResponse(
    val id: Long,
    @SerializedName("chickens_count") val chickensCount: Int,
    @SerializedName("exit_date") val exitDate: String,
    @SerializedName("id_farm") val farmId: Long
)

data class ChickenLeftRequest(
    @SerializedName("chickens_count") val chickensCount: Int,
    @SerializedName("exit_date") val exitDate: String,
    @SerializedName("id_farm") val farmId: Long
)

data class ChickenLeftUpdate(
    @SerializedName("chickens_count") val chickensCount: Int,
    @SerializedName("exit_date") val exitDate: String
)

data class LotUpdateRequest(
    @SerializedName("received_chickens") val receivedChickens: Int,
    val cost: Double
)

data class LotResponse(
    val id: Long,
    @SerializedName("received_chickens") val receivedChickens: Int?,
    @SerializedName("delivered_chickens") val deliveredChickens: Int?,
    @SerializedName("delivery_date") val deliveryDate: String?,
    val losts: Int?,
    val cost: Double?,
    @SerializedName("id_farm") val farmId: Long?
)

data class FarmUpdateRequest(
    @SerializedName("chickens_now") val chickensNow: Int
)

data class FarmResponse(
    val id: Long,
    @SerializedName("chickens_now") val chickensNow: Int?
)

enum class RecordMetric {
    WATER,
    ENERGY
}

data class RecordHistoryItem(
    val id: Long,
    val metric: RecordMetric,
    val date: LocalDate,
    val value: BigDecimal,
    val startHydrometer: BigDecimal? = null,
    val endHydrometer: BigDecimal? = null
) {
    val editable: Boolean
        get() = !date.isAfter(LocalDate.now()) && !date.isBefore(LocalDate.now().minusDays(7))

    fun formattedValue(): String = when (metric) {
        RecordMetric.WATER -> String.format(Locale("pt", "BR"), "%,.0f L", value)
        RecordMetric.ENERGY -> String.format(Locale("pt", "BR"), "%,.0f kWh", value)
    }

    fun monthYear(): String {
        val month = date.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar {
            it.titlecase(Locale("pt", "BR"))
        }
        return "$month ${date.year}"
    }
}

data class RecordHubData(
    val water: List<WaterRegistryResponse>,
    val energy: List<EnergyRegistryResponse>,
    val chickenLeft: List<ChickenLeftResponse>,
    val lots: List<LotResponse>,
    val farm: FarmResponse
) {
    fun waterItems() = water.sortedByDescending { it.registrationDate }.map {
        RecordHistoryItem(
            id = it.id,
            metric = RecordMetric.WATER,
            date = LocalDate.parse(it.registrationDate),
            value = (it.endHydrometer - it.startHydrometer).max(BigDecimal.ZERO) * BigDecimal(1000),
            startHydrometer = it.startHydrometer,
            endHydrometer = it.endHydrometer
        )
    }

    fun energyItems() = energy.sortedByDescending { it.registrationDate }.map {
        RecordHistoryItem(
            id = it.id,
            metric = RecordMetric.ENERGY,
            date = LocalDate.parse(it.registrationDate),
            value = it.energyConsumption
        )
    }
}

enum class RecordDraftMode {
    NEW,
    EDIT
}

data class RecordDraft(
    val mode: RecordDraftMode = RecordDraftMode.NEW,
    val step: Int = 1,
    val lotId: String = "",
    val lotCost: String = "",
    val waterDate: String = "",
    val hydrometerStart: String = "",
    val hydrometerEnd: String = "",
    val energyDate: String = "",
    val energyKwh: String = "",
    val chickensEntered: String = "",
    val chickensLeft: String = "",
    val chickensCurrent: String = "",
    val waterRecordId: Long? = null,
    val energyRecordId: Long? = null,
    val chickenLeftRecordId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun hasContent() = listOf(
        lotId,
        lotCost,
        waterDate,
        hydrometerStart,
        hydrometerEnd,
        energyDate,
        energyKwh,
        chickensEntered,
        chickensLeft,
        chickensCurrent
    ).any { it.isNotBlank() }

    fun displayDate(): String {
        return runCatching {
            LocalDate.parse(waterDate.ifBlank { energyDate }).format(DateTimeFormatter.ofPattern("dd/MM"))
        }.getOrDefault("em andamento")
    }
}