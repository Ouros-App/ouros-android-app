package com.ouros.app.core.fcm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestNotificationReceiverTest {

    @Test
    fun onReceive_triggersNotification() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = TestNotificationReceiver()
        val intent = Intent("com.ouros.app.TEST_NOTIFICATION").apply {
            putExtra("title", "Título Recebido")
            putExtra("message", "Mensagem Recebida")
            putExtra("type", "GENERAL")
            putExtra("tip_id", 10L)
            putExtra("goal_id", 20L)
        }

        receiver.onReceive(context, intent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)
        assertEquals(1, shadowManager.allNotifications.size)
        val posted = shadowManager.allNotifications.first()
        val shadowNotification = shadowOf(posted)
        assertEquals("Título Recebido", shadowNotification.contentTitle)
        assertEquals("Mensagem Recebida", shadowNotification.contentText)
    }

    @Test
    fun onReceive_nullIntent_doesNotCrash() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = TestNotificationReceiver()
        receiver.onReceive(context, null)
    }
}
