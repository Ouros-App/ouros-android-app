package com.ouros.app.feature.secondary.chatbot

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ouros.app.R
import com.ouros.app.data.chatbot.ChatVisualizationChart
import io.noties.markwon.Markwon
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Renderiza mensagens em linhas recicláveis com DiffUtil. Separa texto, datas, gráficos e indicador de digitação para atualizar apenas o que mudou.
 */
internal class ChatbotAdapter(
    context: android.content.Context,
    private val markwon: Markwon
) : ListAdapter<ChatbotRow, RecyclerView.ViewHolder>(DIFF) {
    private val density = context.resources.displayMetrics.density

    fun submitConversation(
        messages: List<ChatLine>,
        sending: Boolean,
        onSubmitted: () -> Unit = {}
    ) {
        val rows = buildList {
            var previousDate: String? = null
            messages.forEachIndexed { index, line ->
                dateLabel(line.createdAt)?.let { label ->
                    if (label != previousDate) {
                        add(ChatbotRow.Date(label, "date:$label"))
                        previousDate = label
                    }
                }
                val messageKey = "message:$index:${line.createdAt.orEmpty()}:${line.user}"
                add(ChatbotRow.Message(line, messageKey))
                line.visualizations.forEachIndexed { visualIndex, visualization ->
                    visualization.charts.forEachIndexed { chartIndex, chart ->
                        if (chart.renderAs.equals("html", true)) {
                            add(
                                ChatbotRow.Chart(
                                    chart,
                                    "$messageKey:chart:$visualIndex:$chartIndex:${chart.id}"
                                )
                            )
                        }
                    }
                }
            }
            if (sending) add(ChatbotRow.Typing)
        }
        submitList(rows, onSubmitted)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatbotRow.Date -> TYPE_DATE
        is ChatbotRow.Message -> TYPE_MESSAGE
        is ChatbotRow.Chart -> TYPE_CHART
        ChatbotRow.Typing -> TYPE_TYPING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        return when (viewType) {
            TYPE_DATE -> DateHolder(TextView(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setBackgroundResource(R.drawable.bg_chat_date_pill)
            })
            TYPE_MESSAGE -> MessageHolder(FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }, TextView(context).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                maxWidth = context.resources.displayMetrics.widthPixels - dp(96)
            }, markwon)
            TYPE_CHART -> ChartHolder(context)
            else -> TypingHolder(TextView(context).apply {
                text = "Midas está pensando…"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            })
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ChatbotRow.Date -> (holder as DateHolder).bind(row)
            is ChatbotRow.Message -> (holder as MessageHolder).bind(row)
            is ChatbotRow.Chart -> (holder as ChartHolder).bind(row)
            ChatbotRow.Typing -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ChartHolder) holder.dispose()
        super.onViewRecycled(holder)
    }

    fun disposeCharts(recyclerView: RecyclerView) {
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
            if (holder is ChartHolder) holder.dispose()
        }
    }

    private inner class DateHolder(private val label: TextView) : RecyclerView.ViewHolder(label) {
        fun bind(row: ChatbotRow.Date) {
            label.text = row.label
            label.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(4) }
            label.gravity = Gravity.CENTER
        }
    }

    private inner class MessageHolder(
        private val frame: FrameLayout,
        private val bubble: TextView,
        private val markdown: Markwon
    ) : RecyclerView.ViewHolder(frame) {
        init { frame.addView(bubble) }

        fun bind(row: ChatbotRow.Message) {
            val line = row.line
            bubble.setBackgroundResource(
                if (line.user) R.drawable.bg_chat_balloon_user
                else R.drawable.bg_chat_balloon_assistant
            )
            if (line.user) bubble.text = line.text else markdown.setMarkdown(bubble, line.text)
            bubble.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (line.user) Gravity.END else Gravity.START
            ).apply { topMargin = dp(8) }
        }
    }

    private inner class ChartHolder(context: android.content.Context) : RecyclerView.ViewHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(chatDp(context, 12), chatDp(context, 10), chatDp(context, 12), chatDp(context, 10))
            setBackgroundResource(R.drawable.bg_chat_balloon_assistant)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                chatDp(context, 280)
            ).apply { topMargin = chatDp(context, 8); bottomMargin = chatDp(context, 6) }
        }
    ) {
        private val container = itemView as LinearLayout
        private val title = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        private var webView: WebView? = null
        private var loadedHtml: String? = null

        init { container.addView(title) }

        fun bind(row: ChatbotRow.Chart) {
            title.text = row.chart.title
            val chart = webView ?: WebView(container.context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ) = true
                }
                container.addView(this, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ))
                webView = this
            }
            if (loadedHtml != row.chart.html) {
                loadedHtml = row.chart.html
                chart.loadDataWithBaseURL(
                    "https://ms-ai-server.discloud.app/",
                    row.chart.html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }

        fun dispose() {
            webView?.let { chart ->
                chart.stopLoading()
                chart.loadUrl("about:blank")
                container.removeView(chart)
                chart.destroy()
            }
            webView = null
            loadedHtml = null
        }
    }

    private class TypingHolder(view: View) : RecyclerView.ViewHolder(view)

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

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val TYPE_DATE = 0
        const val TYPE_MESSAGE = 1
        const val TYPE_CHART = 2
        const val TYPE_TYPING = 3

        val DIFF = object : DiffUtil.ItemCallback<ChatbotRow>() {
            override fun areItemsTheSame(oldItem: ChatbotRow, newItem: ChatbotRow) =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: ChatbotRow, newItem: ChatbotRow) =
                oldItem == newItem
        }
    }
}

internal sealed interface ChatbotRow {
    val key: String

    data class Date(val label: String, override val key: String) : ChatbotRow
    data class Message(val line: ChatLine, override val key: String) : ChatbotRow
    data class Chart(val chart: ChatVisualizationChart, override val key: String) : ChatbotRow
    data object Typing : ChatbotRow { override val key: String = "typing" }
}

private fun chatDp(context: android.content.Context, value: Int) =
    (value * context.resources.displayMetrics.density).toInt()
