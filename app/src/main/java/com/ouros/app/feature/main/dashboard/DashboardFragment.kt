package com.ouros.app.feature.main.dashboard

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.R
import com.ouros.app.core.SkeletonLoaderView
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.dashboard.DashboardCache
import com.ouros.app.data.dashboard.DashboardNetwork
import com.ouros.app.data.dashboard.DashboardPeriod
import com.ouros.app.data.dashboard.DashboardRepository
import com.ouros.app.data.dashboard.DashboardUiState
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.main.MainActivity

class DashboardFragment : Fragment() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var loading: SkeletonLoaderView
    private lateinit var error: TextView
    private lateinit var retry: View
    private lateinit var content: View

    private lateinit var txtKpiConsumptionValue: TextView
    private lateinit var txtKpiWaterValue: TextView
    private lateinit var txtKpiEnergyValue: TextView

    private lateinit var consumptionChart: WebView
    private lateinit var waterChart: WebView
    private lateinit var energyChart: WebView
    private var loadedConsumptionHtml: String? = null
    private var loadedWaterHtml: String? = null
    private var loadedEnergyHtml: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(
            R.layout.fragment_dashboard,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureViewModel()
        bindViews(view)
        configureWebViews()
        configureActions()
        observeState()

        viewModel.loadInitialData()
    }

    private fun configureViewModel() {
        val applicationContext = requireContext().applicationContext

        val authRepository = AuthRepository(
            api = AuthNetwork.api,
            refreshTokenStore = SecureRefreshTokenStore(applicationContext)
        )

        val dashboardRepository = DashboardRepository(
            api = DashboardNetwork.api,
            authRepository = authRepository,
            sessionManager = AppSession.sessionManager,
            cache = DashboardCache(applicationContext),
            farmSessionStore = FarmSessionStore(applicationContext)
        )

        val factory = DashboardViewModelFactory(
            dashboardRepository = dashboardRepository
        )

        viewModel = ViewModelProvider(requireActivity(), factory)[DashboardViewModel::class.java]
    }

    private fun bindViews(root: View) {
        loading = root.findViewById(R.id.dashboardLoading)
        error = root.findViewById(R.id.dashboardError)
        retry = root.findViewById(R.id.dashboardRetry)
        content = root.findViewById(R.id.dashboardContent)

        txtKpiConsumptionValue = root.findViewById(R.id.txtKpiConsumptionValue)
        txtKpiWaterValue = root.findViewById(R.id.txtKpiWaterValue)
        txtKpiEnergyValue = root.findViewById(R.id.txtKpiEnergyValue)

        consumptionChart = root.findViewById(R.id.chartConsumption)
        waterChart = root.findViewById(R.id.chartWater)
        energyChart = root.findViewById(R.id.chartEnergy)
    }

    private fun configureActions() {
        retry.setOnClickListener { viewModel.retry() }
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            render(state)
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                DashboardEvent.AuthenticationRequired -> {
                    viewModel.consumeEvent()
                    (activity as? MainActivity)?.onMainSessionExpired()
                }
                null -> Unit
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViews() {
        listOf(consumptionChart, waterChart, energyChart).forEach { webView ->
            webView.setBackgroundColor(Color.TRANSPARENT)
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return true
                }
            }
        }
    }

    private fun render(state: DashboardUiState) {
        renderLoading(state)
        renderError(state)
        renderKpis(state)

        loadChartIfChanged(webView = consumptionChart, html = state.consumptionChartHtml, previousHtml = loadedConsumptionHtml) {
            loadedConsumptionHtml = it
        }

        loadChartIfChanged(webView = waterChart, html = state.waterChartHtml, previousHtml = loadedWaterHtml) {
            loadedWaterHtml = it
        }

        loadChartIfChanged(webView = energyChart, html = state.energyChartHtml, previousHtml = loadedEnergyHtml) {
            loadedEnergyHtml = it
        }
    }

    private fun renderKpis(state: DashboardUiState) {
        txtKpiConsumptionValue.text = state.kpiConsumption
        txtKpiWaterValue.text = state.kpiWaterLitersPerBird
        txtKpiEnergyValue.text = state.kpiEnergyKwhPerBird
    }

    private fun renderLoading(state: DashboardUiState) {
        if (state.isLoading) {
            loading.visibility = View.VISIBLE
            loading.start()
            content.visibility = View.INVISIBLE
            return
        }

        loading.stop()
        loading.visibility = View.GONE

        val hasCharts =
            !state.consumptionChartHtml.isNullOrBlank() ||
            !state.waterChartHtml.isNullOrBlank() ||
            !state.energyChartHtml.isNullOrBlank()

        content.visibility =
            if (hasCharts) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
    }

    private fun renderError(state: DashboardUiState) {
        error.text = state.error.orEmpty()
        error.visibility =
            if (state.error.isNullOrBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        retry.visibility =
            if (state.canRetry && !state.isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun adaptHtmlForMobile(html: String): String {
        val mobileCss = """
            <style>
                html, body {
                    width: 100% !important;
                    height: 100% !important;
                    min-height: 0 !important;
                    background: transparent !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    overflow: hidden !important;
                }
                .chart-shell, .series-panel {
                    border: none !important;
                    border-radius: 0 !important;
                    background: transparent !important;
                    box-shadow: none !important;
                    min-height: 0 !important;
                    height: 100% !important;
                    width: 100% !important;
                    max-width: 100% !important;
                    padding: 0 !important;
                    margin: 0 !important;
                    display: flex !important;
                    flex-direction: column !important;
                    overflow: hidden !important;
                }
                .chart-heading, .series-panel-heading {
                    display: none !important;
                }
                .series-grid {
                    display: none !important;
                }
                #plot, .series-plot {
                    width: 100% !important;
                    flex: 1 1 auto !important;
                    min-height: 0 !important;
                    height: auto !important;
                }
                .native-legend, .panel-legend {
                    flex: 0 0 auto !important;
                    min-height: 24px !important;
                    padding: 4px 4px 2px 4px !important;
                    gap: 6px 16px !important;
                    justify-content: center !important;
                    overflow: visible !important;
                }
                .legend-item {
                    font-size: 13px !important;
                    line-height: 1.2 !important;
                    white-space: nowrap !important;
                }
            </style>
        """.trimIndent()

        val singleChartHtml = html
            .replace("const shouldSplitSeries = (", "const shouldSplitSeries = false && (")
            .replace("const shouldSplitSeries=(", "const shouldSplitSeries=false&&(")

        return if (singleChartHtml.contains("</head>", ignoreCase = true)) {
            singleChartHtml.replace("</head>", "$mobileCss</head>", ignoreCase = true)
        } else {
            mobileCss + singleChartHtml
        }
    }

    private fun loadChartIfChanged(webView: WebView, html: String?, previousHtml: String?, onLoaded: (String) -> Unit) {
        if (html.isNullOrBlank() || html == previousHtml) {
            return
        }

        val adaptedHtml = adaptHtmlForMobile(html)

        webView.loadDataWithBaseURL(
            DASHBOARD_BASE_URL,
            adaptedHtml,
            MIME_TYPE_HTML,
            Charsets.UTF_8.name(),
            null
        )

        onLoaded(html)
    }

    fun selectPeriod(period: DashboardPeriod) {
        if (::viewModel.isInitialized) {
            viewModel.selectPeriod(period)
        }
    }

    override fun onDestroyView() {
        if (this::consumptionChart.isInitialized) {
            destroyWebView(consumptionChart)
        }

        if (this::waterChart.isInitialized) {
            destroyWebView(waterChart)
        }

        if (this::energyChart.isInitialized) {
            destroyWebView(energyChart)
        }

        if (this::loading.isInitialized) {
            loading.stop()
        }

        super.onDestroyView()
    }

    private fun destroyWebView(webView: WebView) {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        webView.destroy()
    }

    private companion object {
        val DASHBOARD_BASE_URL: String = com.ouros.app.BuildConfig.DASHBOARD_BASE_URL
        const val MIME_TYPE_HTML = "text/html"
    }
}
