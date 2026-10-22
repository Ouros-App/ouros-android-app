package com.ouros.app.feature.notification

import android.widget.FrameLayout
import android.widget.TextView
import com.ouros.app.R
import com.ouros.app.data.notification.NotificationItemResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun adapter_viewTypes_and_viewHolderBinding() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)

        var clickedItem: NotificationItemResponse? = null
        val adapter = NotificationAdapter { item ->
            clickedItem = item
        }

        val goldItem = NotificationItemResponse(id = 1L, title = "Alerta", message = "Água", type = "ALERT_WATER")
        val darkItem = NotificationItemResponse(id = 2L, title = "Midas", message = "Oi", type = "MIDAS_INFO")
        adapter.submitList(listOf(goldItem, darkItem))

        assertEquals(NotificationAdapter.VIEW_TYPE_GOLD, adapter.getItemViewType(0))
        assertEquals(NotificationAdapter.VIEW_TYPE_DARK, adapter.getItemViewType(1))

        val goldHolder = adapter.onCreateViewHolder(parent, NotificationAdapter.VIEW_TYPE_GOLD)
        goldHolder.bind(goldItem)
        assertEquals("Alerta", goldHolder.itemView.findViewById<TextView>(R.id.txtNotificationTitle).text)
        assertEquals("Água", goldHolder.itemView.findViewById<TextView>(R.id.txtNotificationMessage).text)

        goldHolder.itemView.performClick()
        assertEquals(goldItem, clickedItem)

        val darkHolder = adapter.onCreateViewHolder(parent, NotificationAdapter.VIEW_TYPE_DARK)
        darkHolder.bind(darkItem)
        assertEquals("Midas", darkHolder.itemView.findViewById<TextView>(R.id.txtNotificationTitle).text)
        assertEquals("Oi", darkHolder.itemView.findViewById<TextView>(R.id.txtNotificationMessage).text)
    }
}
