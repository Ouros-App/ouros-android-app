package com.ouros.app.core.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.notification.NotificationRepository
import com.ouros.app.feature.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OurosFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationRepository by lazy { NotificationRepository() }
    private val fcmTokenStore by lazy { FcmTokenStore(applicationContext) }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        fcmTokenStore.saveToken(token)

        if (AppSession.sessionManager.hasValidAccessToken()) {
            serviceScope.launch {
                notificationRepository.registerDeviceToken(token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)

        val message = remoteMessage.notification?.body
            ?: remoteMessage.data["message"]
            ?: remoteMessage.data["body"]
            ?: ""

        val type = remoteMessage.data["type"] ?: "GENERAL"
        val tipId = (remoteMessage.data["tip_id"] ?: remoteMessage.data["id_tip"])?.toLongOrNull()
        val goalId = (remoteMessage.data["goal_id"] ?: remoteMessage.data["id_goal"])?.toLongOrNull()

        showNotification(
            context = this,
            title = title,
            message = message,
            type = type,
            tipId = tipId,
            goalId = goalId
        )
    }

    companion object {
        const val CHANNEL_ID = "ouros_notifications"
        const val CHANNEL_NAME = "Notificações Ouros"

        const val EXTRA_TYPE = "notification_type"
        const val EXTRA_TIP_ID = "notification_tip_id"
        const val EXTRA_GOAL_ID = "notification_goal_id"

        fun showNotification(
            context: Context,
            title: String,
            message: String,
            type: String = "GENERAL",
            tipId: Long? = null,
            goalId: Long? = null
        ) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificações do ecossistema Ouros"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val targetIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TYPE, type)
                putExtra("type", type)
                if (tipId != null) {
                    putExtra(EXTRA_TIP_ID, tipId)
                    putExtra("tip_id", tipId.toString())
                    putExtra("id_tip", tipId.toString())
                }
                if (goalId != null) {
                    putExtra(EXTRA_GOAL_ID, goalId)
                    putExtra("goal_id", goalId.toString())
                    putExtra("id_goal", goalId.toString())
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.gold))
                .setContentIntent(pendingIntent)
                .build()

            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(notificationId, notification)
        }
    }
}
