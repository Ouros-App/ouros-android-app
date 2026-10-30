package com.ouros.app.feature.secondary.notification

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.ouros.app.R
import com.ouros.app.data.fcm.FcmTokenStore
import com.ouros.app.data.fcm.OurosFirebaseMessagingService
import com.ouros.app.feature.main.MainActivity
import com.ouros.app.feature.secondary.tip.TipDetailActivity

class NotificationTestActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var txtFcmToken: TextView
    private lateinit var btnCopyToken: Button

    private lateinit var btnTestPushWater: Button
    private lateinit var btnTestPushFeed: Button
    private lateinit var btnTestPushTip: Button
    private lateinit var btnTestPushGoal: Button
    private lateinit var btnTestPushRank: Button

    private lateinit var btnOpenNotificationsScreen: Button
    private lateinit var btnOpenTipScreen: Button
    private lateinit var btnOpenGoalsTab: Button

    private val fcmTokenStore by lazy { FcmTokenStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notification_test)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.headerTest)) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBarInset.top + 16,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        bindViews()
        setupListeners()
        loadFcmToken()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        txtFcmToken = findViewById(R.id.txtFcmToken)
        btnCopyToken = findViewById(R.id.btnCopyToken)

        btnTestPushWater = findViewById(R.id.btnTestPushWater)
        btnTestPushFeed = findViewById(R.id.btnTestPushFeed)
        btnTestPushTip = findViewById(R.id.btnTestPushTip)
        btnTestPushGoal = findViewById(R.id.btnTestPushGoal)
        btnTestPushRank = findViewById(R.id.btnTestPushRank)

        btnOpenNotificationsScreen = findViewById(R.id.btnOpenNotificationsScreen)
        btnOpenTipScreen = findViewById(R.id.btnOpenTipScreen)
        btnOpenGoalsTab = findViewById(R.id.btnOpenGoalsTab)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnCopyToken.setOnClickListener {
            val token = txtFcmToken.text.toString()
            if (token.isNotBlank() && !token.contains("Carregando", ignoreCase = true)) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("FCM Token", token)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Token copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nenhum token disponível para copiar", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestPushWater.setOnClickListener {
            OurosFirebaseMessagingService.showNotification(
                context = applicationContext,
                title = "Toma cuidado,",
                message = "os seus pintos estão com sede!",
                type = "ALERT_WATER"
            )
            Toast.makeText(this, "Push disparado! Veja o banner/som no seu telefone.", Toast.LENGTH_SHORT).show()
        }

        btnTestPushFeed.setOnClickListener {
            OurosFirebaseMessagingService.showNotification(
                context = applicationContext,
                title = "Atenção ao Comedouro,",
                message = "o nível de ração no galpão 2 está crítico!",
                type = "ALERT_FEED"
            )
            Toast.makeText(this, "Push disparado! Veja o banner/som no seu telefone.", Toast.LENGTH_SHORT).show()
        }

        btnTestPushTip.setOnClickListener {
            OurosFirebaseMessagingService.showNotification(
                context = applicationContext,
                title = "Nova Dica Técnica",
                message = "Como diminuir a conta de água na granja",
                type = "TIP_CREATED",
                tipId = 1L
            )
            Toast.makeText(this, "Push disparado! Toque nele na barra para ir direto à Dica.", Toast.LENGTH_LONG).show()
        }

        btnTestPushGoal.setOnClickListener {
            OurosFirebaseMessagingService.showNotification(
                context = applicationContext,
                title = "Eii,",
                message = "Uma nova tarefa foi atribuída a sua granja",
                type = "GOAL_ASSIGNED",
                goalId = 5L
            )
            Toast.makeText(this, "Push disparado! Toque nele na barra para ir a Metas.", Toast.LENGTH_LONG).show()
        }

        btnTestPushRank.setOnClickListener {
            OurosFirebaseMessagingService.showNotification(
                context = applicationContext,
                title = "Você viu?",
                message = "Daqui a pouco o novo ranking sai!",
                type = "RANK_UPDATE"
            )
            Toast.makeText(this, "Push disparado! Toque nele na barra para ir ao Ranking.", Toast.LENGTH_LONG).show()
        }

        btnOpenNotificationsScreen.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        btnOpenTipScreen.setOnClickListener {
            startActivity(TipDetailActivity.createIntent(this, 1L))
        }

        btnOpenGoalsTab.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(OurosFirebaseMessagingService.EXTRA_TYPE, "GOAL_ASSIGNED")
            }
            startActivity(intent)
        }
    }

    private fun loadFcmToken() {
        val cachedToken = fcmTokenStore.getToken()
        if (!cachedToken.isNullOrBlank()) {
            txtFcmToken.text = cachedToken
        }

        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    fcmTokenStore.saveToken(token)
                    txtFcmToken.text = token
                } else {
                    if (txtFcmToken.text.isNullOrBlank() || txtFcmToken.text == "Carregando token...") {
                        txtFcmToken.text = cachedToken ?: "Token ainda não disponível no Firebase."
                    }
                }
            }
        } catch (e: Exception) {
            txtFcmToken.text = cachedToken ?: "Firebase não inicializado com google-services.json oficial."
        }
    }
}
