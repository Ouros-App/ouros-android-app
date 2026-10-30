package com.ouros.app.feature.secondary.chatbot

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.R

internal fun composerBottomInset(systemBarBottom: Int, imeBottom: Int): Int =
    maxOf(systemBarBottom, imeBottom)

class ChatbotActivity : AppCompatActivity() {
    private lateinit var viewModel: ChatbotViewModel
    private var renderedState: ChatState? = null

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

        val input = findViewById<EditText>(R.id.messageInput)
        findViewById<Button>(R.id.attachFile).setOnClickListener {
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
        findViewById<Button>(R.id.sendMessage).setOnClickListener {
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
                rootPadding[3] + composerBottomInset(bars.bottom, ime.bottom)
            )
            insets
        }
        viewModel.state.observe(this) { render(it) }
    }

    private fun render(state: ChatState) {
        if (state == renderedState) return
        renderedState = state
        val messages = findViewById<LinearLayout>(R.id.chatMessages)
        messages.removeAllViews()
        state.messages.forEach { line ->
            val text = TextView(this).apply {
                this.text = line.text
                textSize = 16f
                setTextColor(getColor(R.color.black))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(if (line.user) getColor(R.color.white) else 0xFFEAF0F3.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
            }
            messages.addView(text)
        }
        findViewById<ScrollView>(R.id.chatScroll).post { findViewById<ScrollView>(R.id.chatScroll).fullScroll(View.FOCUS_DOWN) }
        findViewById<TextView>(R.id.chatError).apply {
            text = state.error.orEmpty()
            visibility = if (state.error == null) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.chatRetry).visibility =
            if (state.error != null && state.preview == null && !state.sending && !state.importing) {
                View.VISIBLE
            } else {
                View.GONE
            }
        findViewById<Button>(R.id.chatRetry).isEnabled = !state.sending && !state.importing
        findViewById<Button>(R.id.sendMessage).apply {
            isEnabled = !state.sending && state.preview == null
            text = if (state.sending) "Enviando…" else "Enviar"
        }
        findViewById<Button>(R.id.attachFile).isEnabled = !state.sending && state.preview == null
        findViewById<EditText>(R.id.messageInput).isEnabled =
            !state.sending && state.preview == null
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

    private fun displayName(uri: Uri): String = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
