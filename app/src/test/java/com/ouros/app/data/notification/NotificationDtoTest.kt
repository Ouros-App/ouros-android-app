package com.ouros.app.data.notification

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDtoTest {

    private val gson = Gson()

    @Test
    fun deserializesNotificationItemResponseCorrectly() {
        val json = """
            {
                "id": 101,
                "title": "Nova Dica Técnica",
                "message": "Veja como economizar água no galpão",
                "type": "TIP_CREATED",
                "id_tip": 12,
                "data_payload": "{\"tip_id\":12}",
                "is_read": false,
                "created_at": "2026-10-16T10:00:00Z",
                "read_at": null
            }
        """.trimIndent()

        val item = gson.fromJson(json, NotificationItemResponse::class.java)

        assertEquals(101L, item.id)
        assertEquals("Nova Dica Técnica", item.title)
        assertEquals("Veja como economizar água no galpão", item.message)
        assertEquals("TIP_CREATED", item.type)
        assertEquals(12L, item.idTip)
        assertFalse(item.isRead)
        assertEquals("2026-10-16T10:00:00Z", item.createdAt)
    }

    @Test
    fun deserializesNotificationPageResponse() {
        val json = """
            {
                "content": [
                    {
                        "id": 1,
                        "title": "Alerta de Água",
                        "message": "Nível baixo",
                        "type": "ALERT_WATER",
                        "is_read": true
                    }
                ],
                "total_elements": 1,
                "total_pages": 1,
                "number": 0,
                "is_last": true
            }
        """.trimIndent()

        val page = gson.fromJson(json, NotificationPageResponse::class.java)

        assertEquals(1, page.content.size)
        assertEquals("Alerta de Água", page.content[0].title)
        assertTrue(page.content[0].isRead)
        assertEquals(1L, page.effectiveTotalElements)
        assertEquals(1, page.effectiveTotalPages)
        assertTrue(page.effectiveIsLast)
    }

    @Test
    fun deserializesUnreadCountResponse() {
        val json = """{ "unread_count": 5 }"""
        val unread = gson.fromJson(json, UnreadCountResponse::class.java)
        assertEquals(5, unread.count)
    }

    @Test
    fun serializesDeviceTokenRequest() {
        val request = DeviceTokenRequest(token = "sample_fcm_token_xyz", platform = "ANDROID")
        val json = gson.toJson(request)
        assertTrue(json.contains("sample_fcm_token_xyz"))
        assertTrue(json.contains("ANDROID"))
    }
}
