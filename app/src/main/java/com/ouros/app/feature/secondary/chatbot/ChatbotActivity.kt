package com.ouros.app.feature.secondary.chatbot

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.R
import com.ouros.app.data.local.OfflineSyncRegistry
import io.noties.markwon.Markwon
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun composerBottomInset(systemBarBottom: Int, imeBottom: Int): Int =
    maxOf(systemBarBottom, imeBottom)

class ChatbotActivity : AppCompatActivity() {
    private lateinit var viewModel: ChatbotViewModel
    private var renderedState: ChatState? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallbackRegistered = false
    private var isOnline = true
    private val markwon by lazy { Markwon.create(this) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectivity()

        override fun onLost(network: Network) = refreshConnectivity()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = refreshConnectivity()
    }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.setAttachment(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chatbot)
        viewModel = ViewModelProvider(this)[ChatbotViewModel::class.java]
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        isOnline = OfflineSyncRegistry.isOnline()

        val input = findViewById<EditText>(R.id.messageInput)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.attachFile).setOnClickListener {
            documentPicker.launch(
                arrayOf(
                    "text/csv",
                    "application/csv",
                    "text/comma-separated-values",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/pdf"
                )
            )
        }
        findViewById<TextView>(R.id.selectedFile).setOnClickListener {
            viewModel.discardAttachment()
        }
        findViewById<ImageButton>(R.id.sendMessage).setOnClickListener {
            viewModel.send(input.text?.toString().orEmpty())
            input.text?.clear()
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                viewModel.send(input.text?.toString().orEmpty())
                input.text?.clear()
                true
            } else false
        }
        findViewById<Button>(R.id.confirmImport).setOnClickListener { viewModel.confirmImport() }
        findViewById<Button>(R.id.cancelImport).setOnClickListener { viewModel.cancelPreview() }
        findViewById<Button>(R.id.chatRetry).setOnClickListener { viewModel.retry() }
        val chatRoot = findViewById<View>(R.id.chatRoot)
        val rootPadding = intArrayOf(
            chatRoot.paddingLeft,
            chatRoot.paddingTop,
            chatRoot.paddingRight,
            chatRoot.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(chatRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                rootPadding[0],
                rootPadding[1] + bars.top,
                rootPadding[2],
                rootPadding[3] + dp(16) + composerBottomInset(bars.bottom, ime.bottom)
            )
            insets
        }
        viewModel.state.observe(this) { render(it) }
        viewModel.loadConversation()
    }

    override fun onStart() {
        super.onStart()
        if (!networkCallbackRegistered) {
            networkCallbackRegistered = runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                true
            }.getOrDefault(false)
        }
        refreshConnectivity()
    }

    override fun onStop() {
        if (networkCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        super.onStop()
    }

    private fun refreshConnectivity() {
        runOnUiThread {
            val onlineNow = OfflineSyncRegistry.isOnline()
            if (onlineNow != isOnline) {
                isOnline = onlineNow
                if (onlineNow) viewModel.loadConversation(force = true)
                renderedState?.let(::renderControls)
            }
        }
    }

    private fun render(state: ChatState) {
        if (state == renderedState) return
        renderedState = state
        val messages = findViewById<LinearLayout>(R.id.chatMessages)
        for (index in 0 until messages.childCount) destroyWebViews(messages.getChildAt(index))
        messages.removeAllViews()
        var previousDate: String? = null
        state.messages.forEach { line ->
            val dateLabel = dateLabel(line.createdAt)
            if (dateLabel != null && dateLabel != previousDate) {
                messages.addView(TextView(this).apply {
                    text = dateLabel
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(getColor(android.R.color.darker_gray))
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setBackgroundResource(R.drawable.bg_chat_date_pill)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER; topMargin = dp(8); bottomMargin = dp(4) }
                })
                previousDate = dateLabel
            }
            val text = TextView(this).apply {
                textSize = 14f
                setTextColor(getColor(android.R.color.white))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setBackgroundResource(
                    if (line.user) R.drawable.bg_chat_balloon_user
                    else R.drawable.bg_chat_balloon_assistant
                )
                maxWidth = resources.displayMetrics.widthPixels - dp(96)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                    gravity = if (line.user) Gravity.END else Gravity.START
                }
            }
            if (line.user) text.text = line.text else markwon.setMarkdown(text, line.text)
            messages.addView(text)
            line.visualizations.forEach { visualization ->
                visualization.charts.filter { it.renderAs.equals("html", true) }.forEach { chart ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        setBackgroundResource(R.drawable.bg_chat_balloon_assistant)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(280)
                        ).apply { topMargin = dp(8); bottomMargin = dp(6) }
                    }
                    if (chart.title.isNotBlank()) {
                        card.addView(TextView(this).apply {
                            this.text = chart.title
                            textSize = 14f
                            setTextColor(getColor(android.R.color.white))
                        })
                    }
                    val webView = WebView(this).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ) = true
                        }
                        loadDataWithBaseURL(
                            "https://ms-ai-server.discloud.app/",
                            chart.html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                    card.addView(webView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    ))
                    messages.addView(card)
                }
            }
        }
        if (state.sending) {
            messages.addView(TextView(this).apply {
                text = "Midas está pensando…"
                textSize = 14f
                setTextColor(getColor(android.R.color.darker_gray))
                setPadding(dp(14), dp(10), dp(14), dp(10))
            })
        }
        findViewById<ScrollView>(R.id.chatScroll).post { findViewById<ScrollView>(R.id.chatScroll).fullScroll(View.FOCUS_DOWN) }
        renderControls(state)
        findViewById<TextView>(R.id.selectedFile).apply {
            val uri = state.selectedAttachment
            text = uri?.let { "Arquivo: ${displayName(it)} · toque para remover" }.orEmpty()
            visibility = if (uri != null) View.VISIBLE else View.GONE
        }
        val preview = state.preview
        val previewPanel = findViewById<LinearLayout>(R.id.importPreview)
        previewPanel.visibility = if (preview == null) View.GONE else View.VISIBLE
        if (preview != null) {
            findViewById<TextView>(R.id.previewTitle).text =
                "${preview.filename}\n${preview.records.size} registros válidos; ${preview.issues.size} linhas com erro serão ignoradas."
            findViewById<TextView>(R.id.previewRecords).text = preview.records.mapIndexed { index, row ->
                val detail = when (row.kind.lowercase()) {
                    "water" -> "Água · ${row.registrationDate} · hidrômetro ${row.startHydrometer ?: "—"} → ${row.endHydrometer ?: "—"}"
                    "energy" -> "Energia · ${row.registrationDate} · ${row.energyConsumption ?: "—"}"
                    else -> "${row.kind} · ${row.registrationDate}"
                }
                "${index + 1}. $detail\n${row.sourceReference}"
            }.ifEmpty { listOf("Nenhum registro válido encontrado.") }.joinToString("\n\n")
            findViewById<TextView>(R.id.previewIssues).text = preview.issues.joinToString("\n") { "${it.sourceReference}: ${it.message}" }
            findViewById<Button>(R.id.confirmImport).apply {
                isEnabled = preview.requiresConfirmation &&
                    !state.importing &&
                    !state.imported &&
                    !state.importAttemptFailed &&
                    preview.records.any {
                        it.kind.equals("water", true) || it.kind.equals("energy", true)
                    }
                text = if (state.importing) "Importando…" else "Confirmar importação"
            }
            findViewById<Button>(R.id.cancelImport).isEnabled = !state.importing
        }
    }

    private fun renderControls(state: ChatState) {
        findViewById<View>(R.id.offlineNotice).visibility =
            if (isOnline && state.serviceAvailable) View.GONE else View.VISIBLE
        val serviceUnavailable = isOnline && !state.serviceAvailable
        findViewById<TextView>(R.id.offlineTitle).text =
            if (isOnline) "Midas temporariamente indisponível" else "Midas está offline"
        findViewById<TextView>(R.id.offlineBody).text = if (serviceUnavailable) {
            "Não foi possível conectar ao assistente. Respostas de orientação básica estarão disponíveis enquanto o serviço se recupera."
        } else {
            "O assistente precisa de internet para responder. A conversa será liberada assim que a conexão voltar."
        }
        findViewById<TextView>(R.id.chatError).apply {
            text = state.error.orEmpty()
            visibility = if (isOnline && state.error != null) View.VISIBLE else View.GONE
        }
        findViewById<Button>(R.id.chatRetry).apply {
            visibility = if (
                isOnline &&
                state.error != null &&
                state.preview == null &&
                !state.sending &&
                !state.importing
            ) View.VISIBLE else View.GONE
            isEnabled = isOnline && !state.sending && !state.importing
        }
        val composerEnabled = isOnline && !state.sending && state.preview == null
        findViewById<ImageButton>(R.id.sendMessage).apply {
            isEnabled = composerEnabled
            alpha = if (isEnabled) 1f else 0.7f
        }
        findViewById<ImageButton>(R.id.attachFile).apply {
            isEnabled = composerEnabled
            alpha = if (isEnabled) 1f else 0.45f
        }
        findViewById<EditText>(R.id.messageInput).apply {
            isEnabled = composerEnabled
            hint = if (isOnline) "Pergunte algo!" else "Conecte-se para conversar"
        }
        findViewById<View>(R.id.messageInputContainer).alpha =
            if (composerEnabled) 1f else 0.55f
    }

    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()

    private fun dateLabel(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val date = runCatching { OffsetDateTime.parse(value).toLocalDate() }
            .getOrElse { runCatching { LocalDate.parse(value.take(10)) }.getOrNull() }
            ?: return null
        val today = LocalDate.now()
        return when (date) {
            today -> "Hoje"
            today.minusDays(1) -> "Ontem"
            else -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }

    private fun destroyWebViews(view: View) {
        if (view is WebView) {
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        } else if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) destroyWebViews(view.getChildAt(index))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
