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
import androidx.core.widget.NestedScrollView
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

    private enum class ChartSlot {
        CONSUMPTION,
        WATER,
        ENERGY
    }

    private lateinit var viewModel: DashboardViewModel
    private lateinit var loading: SkeletonLoaderView
    private lateinit var error: TextView
    private lateinit var retry: View
    private lateinit var content: NestedScrollView

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

        content.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
                resizeAllCharts()
            }
        )
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

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    triggerChartResize(view)
                }
            }
        }
    }

    private fun triggerChartResize(webView: WebView?) {
        webView?.post {
            if (isAdded) {
                webView.evaluateJavascript(
                    "if (window.__ourosResize) window.__ourosResize();",
                    null
                )
            }
        }
    }

    private fun resizeAllCharts() {
        if (!isAdded) return
        triggerChartResize(consumptionChart)
        triggerChartResize(waterChart)
        triggerChartResize(energyChart)
    }

    private fun render(state: DashboardUiState) {
        renderLoading(state)
        renderError(state)
        renderKpis(state)

        loadChartIfChanged(
            webView = consumptionChart,
            html = state.consumptionChartHtml,
            previousKey = loadedConsumptionHtml,
            chartSlot = ChartSlot.CONSUMPTION,
            period = state.period
        ) {
            loadedConsumptionHtml = it
        }

        loadChartIfChanged(
            webView = waterChart,
            html = state.waterChartHtml,
            previousKey = loadedWaterHtml,
            chartSlot = ChartSlot.WATER,
            period = state.period
        ) {
            loadedWaterHtml = it
        }

        loadChartIfChanged(
            webView = energyChart,
            html = state.energyChartHtml,
            previousKey = loadedEnergyHtml,
            chartSlot = ChartSlot.ENERGY,
            period = state.period
        ) {
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

    private fun adaptHtmlForMobile(
        html: String,
        chartSlot: ChartSlot,
        period: DashboardPeriod
    ): String {
        val payloadJson = extractJsonObject(html, "const payload =")
        if (payloadJson != null) {
            return buildFigmaMobileChartHtml(payloadJson, chartSlot, period)
        }

        val mobileCss = """
            <style>
                html, body {
                    width: 100% !important;
                    height: 220px !important;
                    min-height: 220px !important;
                    max-height: 220px !important;
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
                    min-height: 220px !important;
                    height: 220px !important;
                    max-height: 220px !important;
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
                #plot, .series-plot {
                    width: 100% !important;
                    flex: 0 0 186px !important;
                    min-height: 186px !important;
                    height: 186px !important;
                    max-height: 186px !important;
                }
                .native-legend, .panel-legend {
                    flex: 0 0 30px !important;
                    min-height: 30px !important;
                    height: 30px !important;
                    padding: 0 4px !important;
                    gap: 6px 24px !important;
                    display: flex !important;
                    justify-content: center !important;
                    align-items: center !important;
                }
            </style>
        """.trimIndent()

        return if (html.contains("</head>", ignoreCase = true)) {
            html.replace("</head>", "$mobileCss</head>", ignoreCase = true)
        } else {
            mobileCss + html
        }
    }

    private fun extractJsonObject(source: String, marker: String): String? {
        val markerIndex = source.indexOf(marker)
        if (markerIndex < 0) return null
        val startBrace = source.indexOf('{', markerIndex + marker.length)
        if (startBrace < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in startBrace until source.length) {
            val ch = source[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') {
                    depth++
                } else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return source.substring(startBrace, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun buildFigmaMobileChartHtml(payloadJson: String, chartSlot: ChartSlot, period: DashboardPeriod): String {
        return """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
              <meta name="color-scheme" content="light">
              <script src="https://cdn.plot.ly/plotly-2.35.2.min.js" crossorigin="anonymous"></script>
              <style>
                * {
                  box-sizing: border-box;
                  -webkit-tap-highlight-color: transparent;
                }
                html, body {
                  width: 100%;
                  height: 220px;
                  min-height: 220px;
                  max-height: 220px;
                  margin: 0;
                  padding: 0;
                  background: transparent;
                  color: #010B13;
                  font-family: "Poppins", "Inter", system-ui, -apple-system, sans-serif;
                  -webkit-font-smoothing: antialiased;
                  overflow: hidden;
                }
                .chart-shell {
                  width: 100%;
                  height: 220px;
                  min-height: 220px;
                  max-height: 220px;
                  display: flex;
                  flex-direction: column;
                  justify-content: space-between;
                  overflow: hidden;
                  background: transparent;
                  border: 0;
                  margin: 0;
                  padding: 0;
                }
                #plot {
                  width: 100%;
                  height: 186px !important;
                  min-height: 186px !important;
                  max-height: 186px !important;
                  flex: 0 0 186px !important;
                }
                #native-legend {
                  width: 100%;
                  height: 30px !important;
                  min-height: 30px !important;
                  max-height: 30px !important;
                  flex: 0 0 30px !important;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  gap: 28px;
                  padding: 0 8px;
                  margin: 0;
                }
                #native-legend[hidden] {
                  display: none !important;
                }
                .legend-item {
                  display: inline-flex;
                  align-items: center;
                  gap: 6px;
                  color: #010B13;
                  font-size: 13.5px;
                  font-weight: 400;
                  line-height: 1;
                  letter-spacing: -0.4px;
                  white-space: nowrap;
                }
                .legend-swatch {
                  width: 8px;
                  height: 8px;
                  flex: 0 0 8px;
                  background: var(--legend-color);
                }
                .empty-state {
                  width: 100%;
                  height: 220px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  text-align: center;
                  padding: 16px;
                }
                .empty-state[hidden] {
                  display: none !important;
                }
                .empty-card {
                  max-width: 280px;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  gap: 6px;
                }
                .empty-mark {
                  width: 28px;
                  height: 3px;
                  border-radius: 999px;
                  background: #D8A23A;
                }
                .empty-title {
                  margin: 2px 0 0;
                  color: #010B13;
                  font-size: 14px;
                  font-weight: 600;
                }
                .empty-copy {
                  margin: 0;
                  color: #7E7D89;
                  font-size: 11px;
                  line-height: 1.4;
                }
              </style>
            </head>
            <body>
              <main id="chart-shell" class="chart-shell">
                <div id="plot" role="img"></div>
                <div id="native-legend" aria-label="Legenda"></div>
                <section id="empty-state" class="empty-state" hidden aria-live="polite">
                  <div class="empty-card">
                    <span class="empty-mark" aria-hidden="true"></span>
                    <p class="empty-title">Sem dados neste período</p>
                    <p class="empty-copy">Quando novas leituras entrarem no período, este card se atualiza automaticamente.</p>
                  </div>
                </section>
              </main>
              <script>
                (function() {
                  const payload = $payloadJson;
                  const slot = "${chartSlot.name}";
                  const rawRows = Array.isArray(payload.rows) ? payload.rows : [];
                  const plot = document.getElementById("plot");
                  const nativeLegend = document.getElementById("native-legend");
                  const emptyState = document.getElementById("empty-state");

                  const COLOR_CURRENT = "#D8A23A";
                  const COLOR_PREVIOUS = "#110B95";
                  const COLOR_MUTED = "#7E7D89";
                  const COLOR_TEXT = "#010B13";
                  const COLOR_GRID = "#CACACA";
                  const COLOR_SURFACE = "#FFFFFF";
                  const PLOT_HEIGHT = 186;

                  const MONTH_LABELS = [
                    "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
                    "Jul", "Ago", "Set", "Out", "Nov", "Dez"
                  ];

                  const nullableNumber = (val) => {
                    if (val === null || val === undefined || val === "") return null;
                    const n = Number(val);
                    return Number.isFinite(n) ? n : null;
                  };

                  const parseIsoDate = (val) => {
                    if (typeof val !== "string" || !/^[0-9]{4}-[0-9]{2}-[0-9]{2}/.test(val)) return null;
                    const d = new Date(val.slice(0, 10) + "T00:00:00Z");
                    return Number.isNaN(d.getTime()) ? null : d;
                  };

                  const appendLegendItem = (legend, labelText, color) => {
                    const item = document.createElement("span");
                    item.className = "legend-item";
                    const swatch = document.createElement("span");
                    swatch.className = "legend-swatch";
                    swatch.style.setProperty("--legend-color", color);
                    swatch.setAttribute("aria-hidden", "true");
                    const label = document.createElement("span");
                    label.textContent = labelText;
                    item.append(swatch, label);
                    legend.append(item);
                  };

                  if (!rawRows.length) {
                    plot.hidden = true;
                    nativeLegend.hidden = true;
                    emptyState.hidden = false;
                    return;
                  }

                  let normalizedValues = [];
                  if (slot === "CONSUMPTION") {
                    const combinedScores = rawRows.map((row) => {
                      const directPct = nullableNumber(row.relative_consumption_pct);
                      if (directPct !== null) return { direct: true, value: directPct };
                      const wM3 = nullableNumber(row.water_consumed_m3);
                      const eKwh = nullableNumber(row.energy_consumed_kwh);
                      if (wM3 !== null || eKwh !== null) {
                        return { direct: false, value: (wM3 || 0) + (eKwh || 0) };
                      }
                      const wBird = nullableNumber(row.water_m3_per_chicken);
                      const eBird = nullableNumber(row.energy_kwh_per_chicken);
                      if (wBird !== null || eBird !== null) {
                        return { direct: false, value: ((wBird || 0) * 1000 * 0.7) + ((eBird || 0) * 0.3) };
                      }
                      return { direct: false, value: null };
                    });
                    const maxCombined = combinedScores.reduce((max, item) => {
                      if (item.direct || item.value === null) return max;
                      return item.value > max ? item.value : max;
                    }, 0);
                    normalizedValues = combinedScores.map((item) => {
                      if (item.value === null) return null;
                      if (item.direct) return Math.min(100, Math.max(0, item.value));
                      if (maxCombined <= 0) return 0;
                      return Math.min(100, Math.max(0, Math.round((item.value * 100 / maxCombined) * 10) / 10));
                    });
                  } else if (slot === "WATER") {
                    normalizedValues = rawRows.map((row) => {
                      const wM3 = nullableNumber(row.water_consumed_m3);
                      if (wM3 !== null) return Math.round(wM3 * 1000 * 100) / 100;
                      const wBird = nullableNumber(row.water_m3_per_chicken);
                      if (wBird !== null) return Math.round(wBird * 1000 * 100) / 100;
                      return null;
                    });
                  } else {
                    normalizedValues = rawRows.map((row) => {
                      const eKwh = nullableNumber(row.energy_consumed_kwh);
                      if (eKwh !== null) return eKwh;
                      const eBird = nullableNumber(row.energy_kwh_per_chicken);
                      if (eBird !== null) return eBird;
                      return null;
                    });
                  }

                  const datedItems = rawRows
                    .map((row, idx) => ({
                      date: parseIsoDate(row.month_start || row.delivery_date || row.registration_date),
                      value: normalizedValues[idx]
                    }))
                    .filter((item) => item.date !== null);

                  if (!datedItems.length) {
                    plot.hidden = true;
                    nativeLegend.hidden = true;
                    emptyState.hidden = false;
                    return;
                  }

                  const latestDate = datedItems.reduce(
                    (latest, item) => (item.date > latest ? item.date : latest),
                    datedItems[0].date
                  );
                  const currentYear = latestDate.getUTCFullYear();
                  const previousYear = currentYear - 1;
                  const maxMonths = ${period.maxMonths};
                  const lastMonthIndex = latestDate.getUTCMonth();
                  const startMonthIndex = maxMonths <= 1
                    ? lastMonthIndex
                    : maxMonths <= 3
                    ? Math.max(0, lastMonthIndex - 2)
                    : 0;
                  const months = Array.from(
                    { length: lastMonthIndex - startMonthIndex + 1 },
                    (_, i) => startMonthIndex + i
                  );

                  const currentMap = new Map();
                  const previousMap = new Map();
                  datedItems.forEach((item) => {
                    const yr = item.date.getUTCFullYear();
                    const mo = item.date.getUTCMonth();
                    if (yr === currentYear) currentMap.set(mo, item.value);
                    if (yr === previousYear) previousMap.set(mo, item.value);
                  });

                  const xCategories = months.map((m) => MONTH_LABELS[m]);
                  const xTickText = months.map((m, i) =>
                    i === months.length - 1 ? "<b>" + MONTH_LABELS[m] + "</b>" : MONTH_LABELS[m]
                  );
                  const currentSeries = months.map((m) => (currentMap.has(m) ? currentMap.get(m) : null));
                  const previousSeries = months.map((m) => (previousMap.has(m) ? previousMap.get(m) : null));

                  nativeLegend.replaceChildren();
                  appendLegendItem(nativeLegend, "Este ano", COLOR_CURRENT);
                  appendLegendItem(nativeLegend, "Ano passado", COLOR_PREVIOUS);
                  nativeLegend.hidden = false;

                  const unitSuffix = slot === "CONSUMPTION" ? "%" : slot === "WATER" ? "L" : "kWh";
                  const hoverSuffix = unitSuffix ? " " + unitSuffix : "";
                  const isLine = slot === "CONSUMPTION";

                  const makeTrace = (label, values, year, color) => {
                    if (isLine) {
                      return {
                        type: "scatter",
                        mode: "lines+markers",
                        name: label,
                        x: xCategories,
                        y: values,
                        customdata: xCategories.map(() => year),
                        connectgaps: false,
                        line: { color: color, width: 2, shape: "linear" },
                        marker: {
                          color: color,
                          size: months.length === 1 ? 10 : 7.5,
                          line: { color: COLOR_SURFACE, width: 1 }
                        },
                        hovertemplate: "<b>%{x} %{customdata}</b><br>%{y}" + hoverSuffix + "<extra></extra>"
                      };
                    }
                    return {
                      type: "bar",
                      name: label,
                      x: xCategories,
                      y: values,
                      customdata: xCategories.map(() => year),
                      marker: {
                        color: color,
                        line: { width: 0 },
                        cornerradius: 3
                      },
                      hovertemplate: "<b>%{x} %{customdata}</b><br>%{y}" + hoverSuffix + "<extra></extra>"
                    };
                  };

                  const traces = [
                    makeTrace("Ano passado", previousSeries, previousYear, COLOR_PREVIOUS),
                    makeTrace("Este ano", currentSeries, currentYear, COLOR_CURRENT)
                  ];

                  const initialWidth = Math.max(
                    plot.clientWidth || 0,
                    window.innerWidth || 0,
                    document.documentElement.clientWidth || 0,
                    320
                  );

                  const layout = {
                    autosize: true,
                    width: initialWidth,
                    height: PLOT_HEIGHT,
                    margin: { l: 44, r: 8, t: 10, b: 26 },
                    paper_bgcolor: "rgba(0,0,0,0)",
                    plot_bgcolor: "rgba(0,0,0,0)",
                    showlegend: false,
                    hovermode: "x unified",
                    font: {
                      family: "Poppins, Inter, system-ui, sans-serif",
                      color: COLOR_TEXT,
                      size: 11.5
                    },
                    hoverlabel: {
                      bgcolor: "#171438",
                      bordercolor: "rgba(255,255,255,0.08)",
                      font: {
                        color: "#F2F5F7",
                        family: "Poppins, Inter, system-ui, sans-serif",
                        size: 11
                      }
                    },
                    xaxis: {
                      type: "category",
                      categoryorder: "array",
                      categoryarray: xCategories,
                      tickvals: xCategories,
                      ticktext: xTickText,
                      range: [-0.5, xCategories.length - 0.5],
                      showgrid: false,
                      zeroline: false,
                      showline: true,
                      linecolor: COLOR_GRID,
                      linewidth: 1,
                      automargin: true,
                      ticks: "",
                      tickfont: { color: COLOR_MUTED, size: 11.5 },
                      fixedrange: true
                    },
                    yaxis: {
                      showgrid: true,
                      gridcolor: COLOR_GRID,
                      griddash: "dash",
                      gridwidth: 1,
                      zeroline: false,
                      showline: false,
                      automargin: true,
                      ticks: "",
                      tickfont: { color: COLOR_MUTED, size: 11.5 },
                      fixedrange: true,
                      rangemode: "tozero",
                      nticks: 6
                    }
                  };

                  if (slot === "CONSUMPTION") {
                    layout.yaxis.range = [0, 105];
                    layout.yaxis.tickvals = [0, 20, 40, 60, 80, 100];
                    layout.yaxis.ticktext = ["0%", "20%", "40%", "60%", "80%", "100%"];
                  } else {
                    layout.barmode = "group";
                    layout.bargap = 0.36;
                    layout.bargroupgap = 0.14;
                    layout.yaxis.ticksuffix = unitSuffix;
                  }

                  const config = {
                    responsive: true,
                    displaylogo: false,
                    displayModeBar: false,
                    scrollZoom: false,
                    doubleClick: false,
                    showTips: false
                  };

                  const renderChart = () => {
                    if (typeof Plotly === "undefined") {
                      setTimeout(renderChart, 50);
                      return;
                    }
                    Plotly.newPlot(plot, traces, layout, config);
                  };

                  window.__ourosResize = () => {
                    if (typeof Plotly === "undefined") return;
                    const w = Math.max(
                      plot.clientWidth || 0,
                      window.innerWidth || 0,
                      document.documentElement.clientWidth || 0
                    );
                    if (w > 0) {
                      Plotly.relayout(plot, { width: w, height: PLOT_HEIGHT });
                    }
                  };

                  window.addEventListener("resize", window.__ourosResize);
                  renderChart();
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun loadChartIfChanged(
        webView: WebView,
        html: String?,
        previousKey: String?,
        chartSlot: ChartSlot,
        period: DashboardPeriod,
        onLoaded: (String) -> Unit
    ) {
        if (html.isNullOrBlank()) {
            return
        }
        val currentKey = "${period.name}:$html"
        if (currentKey == previousKey) {
            return
        }

        val adaptedHtml = adaptHtmlForMobile(html, chartSlot, period)

        webView.loadDataWithBaseURL(
            DASHBOARD_BASE_URL,
            adaptedHtml,
            MIME_TYPE_HTML,
            Charsets.UTF_8.name(),
            null
        )

        onLoaded(currentKey)
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
