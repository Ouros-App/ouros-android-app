package com.ouros.app.feature.notification

import com.ouros.app.data.notification.NotificationItemResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPayloadExtractionTest {

    private fun extractIdFromPayload(payload: String?, key: String): Long? {
        if (payload.isNullOrBlank()) return null
        val regex = Regex("\"?$key\"?\\s*[:=]\\s*\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
        return regex.find(payload)?.groupValues?.get(1)?.toLongOrNull()
    }

    @Test
    fun extractIdFromPayload_supportsStandardAndSnakeCaseJson() {
        val payload1 = """{"tip_id": 42, "metric": "water"}"""
        val payload2 = """{"id_tip": 99, "farm_id": 3}"""
        val payload3 = """{"tip_id": "105"}"""
        val payload4 = """{"id_goal": 77}"""

        assertEquals(42L, extractIdFromPayload(payload1, "tip_id"))
        assertEquals(99L, extractIdFromPayload(payload2, "id_tip"))
        assertEquals(105L, extractIdFromPayload(payload3, "tip_id"))
        assertEquals(77L, extractIdFromPayload(payload4, "id_goal"))
        assertNull(extractIdFromPayload("{}", "tip_id"))
        assertNull(extractIdFromPayload(null, "tip_id"))
        assertNull(extractIdFromPayload("not-json", "tip_id"))
    }

    @Test
    fun tipClassification_recognizesTipActivatedFromAudienceEngine() {
        val item = NotificationItemResponse(
            id = 1L,
            title = "Dica recomendada",
            message = "Veja como otimizar o consumo de ração.",
            type = "TIP_ACTIVATED",
            idTip = 12L
        )

        val tipId = item.idTip
            ?: extractIdFromPayload(item.dataPayload, "tip_id")
            ?: extractIdFromPayload(item.dataPayload, "id_tip")

        val isTip = item.type.contains("TIP", ignoreCase = true) ||
                tipId != null ||
                NotificationAdapter.isGoldAlert(item)

        assertTrue(isTip)
        assertEquals(12L, tipId)
    }

    @Test
    fun tipClassification_recognizesTipFromPayloadWhenIdTipIsNull() {
        val item = NotificationItemResponse(
            id = 2L,
            title = "Alerta de Água",
            message = "Consumo acima da média histórica.",
            type = "ALERT_WATER",
            idTip = null,
            dataPayload = """{"farm_id": 5, "tip_id": 88}"""
        )

        val tipId = item.idTip
            ?: extractIdFromPayload(item.dataPayload, "tip_id")
            ?: extractIdFromPayload(item.dataPayload, "id_tip")

        val isTip = item.type.contains("TIP", ignoreCase = true) ||
                tipId != null ||
                NotificationAdapter.isGoldAlert(item)

        assertTrue(isTip)
        assertEquals(88L, tipId)
    }
}
