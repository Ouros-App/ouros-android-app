package com.ouros.app.feature.notification

import com.ouros.app.data.notification.NotificationItemResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAdapterTest {

    @Test
    fun isGoldAlert_returnsTrueForOperationalAlerts() {
        val waterAlert = NotificationItemResponse(
            id = 1,
            title = "Toma cuidado,",
            message = "os seus pintos estão com sede!",
            type = "ALERT_WATER"
        )
        assertTrue(NotificationAdapter.isGoldAlert(waterAlert))

        val feedAlert = NotificationItemResponse(
            id = 2,
            title = "Atenção",
            message = "Vishh vai alimentar os seus pintos direito!",
            type = "GENERAL"
        )
        assertTrue(NotificationAdapter.isGoldAlert(feedAlert))
    }

    @Test
    fun isGoldAlert_returnsFalseForGeneralAndRankingNotifications() {
        val midasNotice = NotificationItemResponse(
            id = 3,
            title = "Piiuu",
            message = "O midas está com saudade!!",
            type = "MIDAS_INFO"
        )
        assertFalse(NotificationAdapter.isGoldAlert(midasNotice))

        val rankingNotice = NotificationItemResponse(
            id = 4,
            title = "Você viu?",
            message = "Daqui a pouco o novo ranking sai!",
            type = "RANK_UPDATE"
        )
        assertFalse(NotificationAdapter.isGoldAlert(rankingNotice))

        val goalNotice = NotificationItemResponse(
            id = 5,
            title = "Eii,",
            message = "Uma nova tarefa foi atribuída a sua granja",
            type = "GOAL_ASSIGNED"
        )
        assertFalse(NotificationAdapter.isGoldAlert(goalNotice))
    }
}
