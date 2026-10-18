package com.ouros.app.core.fcm

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OurosFirebaseMessagingServiceTest {

    @Test
    fun showNotification_createsNotificationChannelAndPostsNotification() {
        val context = RuntimeEnvironment.getApplication()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)

        OurosFirebaseMessagingService.showNotification(
            context = context,
            title = "Alerta Teste",
            message = "Mensagem de teste",
            type = "ALERT_WATER",
            tipId = 42L,
            goalId = 99L
        )

        val channel = notificationManager.getNotificationChannel(OurosFirebaseMessagingService.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(OurosFirebaseMessagingService.CHANNEL_NAME, channel.name)

        assertEquals(1, shadowManager.allNotifications.size)
        val posted = shadowManager.allNotifications.first()
        val shadowNotification = shadowOf(posted)
        assertEquals("Alerta Teste", shadowNotification.contentTitle)
        assertEquals("Mensagem de teste", shadowNotification.contentText)
    }
}
