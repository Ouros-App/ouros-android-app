package com.ouros.app.data.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TestNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val title = intent.getStringExtra("title") ?: "Alerta Ouros"
        val message = intent.getStringExtra("message") ?: "Teste de notificação local"
        val type = intent.getStringExtra("type") ?: "GENERAL"
        val tipId = intent.getLongExtra("tip_id", -1L).takeIf { it > 0 }
        val goalId = intent.getLongExtra("goal_id", -1L).takeIf { it > 0 }

        OurosFirebaseMessagingService.showNotification(
            context = context,
            title = title,
            message = message,
            type = type,
            tipId = tipId,
            goalId = goalId
        )
    }
}
