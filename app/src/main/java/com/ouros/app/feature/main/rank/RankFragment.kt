package com.ouros.app.feature.main.rank

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.rank.RankNetwork
import com.ouros.app.data.rank.RankRepository
import com.ouros.app.data.rank.RankUiState
import com.ouros.app.data.rank.formatRemainingTime
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.main.MainActivity
import kotlin.math.max

class RankFragment : Fragment() {
    private lateinit var viewModel: RankViewModel
    private lateinit var adapter: RankAdapter
    private lateinit var content: View
    private lateinit var loading: View
    private lateinit var error: TextView
    private lateinit var retry: View
    private lateinit var currentPosition: TextView
    private var currentLugarLabel: TextView? = null
    private lateinit var currentLeague: TextView
    private var medalImage: ImageView? = null
    private lateinit var resetTime: TextView
    private lateinit var rankingList: RecyclerView

    private var targetResetElapsedRealtime: Long? = null
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var isCountdownRunning = false

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!isCountdownRunning) return
            updateCountdownText()
            if (isCountdownRunning) {
                countdownHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_rank, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureViewModel()
        bindViews(view)
        configureRankingList()
        configureActions()
        observeState()
        viewModel.loadInitialData()
    }

    private fun configureViewModel() {
        val context = requireContext().applicationContext
        val authRepository = AuthRepository(
            api = AuthNetwork.api,
            refreshTokenStore = SecureRefreshTokenStore(context),
            sessionManager = AppSession.sessionManager
        )
        val farmOwnerRepository = FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )
        val repository = RankRepository(
            api = RankNetwork.api,
            farmOwnerRepository = farmOwnerRepository,
            farmSessionStore = FarmSessionStore(context)
        )
        viewModel = ViewModelProvider(
            this,
            RankViewModelFactory(repository)
        )[RankViewModel::class.java]
    }

    private fun bindViews(root: View) {
        content = root.findViewById(R.id.rankContent)
        loading = root.findViewById(R.id.rankLoading)
        error = root.findViewById(R.id.rankError)
        retry = root.findViewById(R.id.rankRetry)
        currentPosition = root.findViewById(R.id.textCurrentPosition)
        currentLugarLabel = root.findViewById(R.id.textLugarLabel)
        currentLeague = root.findViewById(R.id.textCurrentLeague)
        medalImage = root.findViewById(R.id.imgRankMedal)
        resetTime = root.findViewById(R.id.textResetTime)
        rankingList = root.findViewById(R.id.recyclerRanking)

        val formulaView = root.findViewById<TextView>(R.id.textCgiFormula)
        val cgiHtml = "(CAA × 0.7) + (CEA × 0.3) = <font color='#110B95'><b>CGI</b></font>"
        formulaView?.text = Html.fromHtml(cgiHtml, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun configureRankingList() {
        adapter = RankAdapter()
        rankingList.layoutManager = LinearLayoutManager(requireContext())
        rankingList.adapter = adapter
        rankingList.itemAnimator = null
        rankingList.isNestedScrollingEnabled = false
    }

    private fun configureActions() {
        retry.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner, ::render)

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                RankEvent.AuthenticationRequired -> {
                    viewModel.consumeEvent()
                    (activity as? MainActivity)?.onMainSessionExpired()
                }
                null -> Unit
            }
        }
    }

    private fun render(state: RankUiState) {
        loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        content.visibility =
            if (!state.isLoading && state.ranking != null) View.VISIBLE
            else View.INVISIBLE

        error.text = state.error.orEmpty()
        error.visibility =
            if (state.error.isNullOrBlank()) View.GONE
            else View.VISIBLE

        retry.visibility =
            if (state.canRetry && !state.isLoading) View.VISIBLE
            else View.GONE

        val ranking = state.ranking
        val league = ranking?.currentLeague

        (activity as? MainActivity)?.updateRankDivision(league)

        if (ranking == null) {
            targetResetElapsedRealtime = null
            stopCountdown()
            currentPosition.text = "--"
            currentLugarLabel?.visibility = View.GONE
            currentLeague.text = "Você está sem divisão"
            medalImage?.setImageResource(R.drawable.rank_ouro)
            adapter.submitList(emptyList())
            return
        }

        val rankPosition = ranking.currentEntry?.rankPosition
        if (rankPosition != null) {
            currentPosition.text = "${rankPosition}º"
            currentLugarLabel?.visibility = View.VISIBLE
        } else {
            currentPosition.text = "--"
            currentLugarLabel?.visibility = View.GONE
        }

        val leagueHtml = league?.let {
            "Você é um <font color='#D8A23A'><b>${it.uppercaseName}</b></font>"
        } ?: "Você está sem divisão"
        currentLeague.text = Html.fromHtml(leagueHtml, Html.FROM_HTML_MODE_LEGACY)

        league?.iconRes?.let { medalImage?.setImageResource(it) }

        val seconds = ranking.secondsUntilNextReset
            ?: parseSecondsFromIso(ranking.nextResetAt)

        if (seconds != null && seconds > 0L) {
            targetResetElapsedRealtime = SystemClock.elapsedRealtime() + (seconds * 1000L)
            if (!isHidden) {
                startCountdown()
            } else {
                updateCountdownText()
            }
        } else {
            targetResetElapsedRealtime = null
            stopCountdown()
            resetTime.text =
                ranking.time?.toDisplayString()
                    ?: ranking.timeUntilNextReset?.takeIf(String::isNotBlank)
                    ?: "Em instantes"
        }

        adapter.submitList(ranking.rankingPodium.orEmpty())
    }

    override fun onStart() {
        super.onStart()
        if (!isHidden) {
            startCountdown()
        }
    }

    override fun onStop() {
        stopCountdown()
        super.onStop()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopCountdown()
        } else {
            startCountdown()
        }
    }

    private fun startCountdown() {
        if (targetResetElapsedRealtime == null) return
        updateCountdownText()
        if (isCountdownRunning) return
        isCountdownRunning = true
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.postDelayed(countdownRunnable, 1000L)
    }

    private fun stopCountdown() {
        isCountdownRunning = false
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun updateCountdownText() {
        val target = targetResetElapsedRealtime ?: return
        val remainingMillis = target - SystemClock.elapsedRealtime()
        val remainingSeconds = max(0L, (remainingMillis + 999L) / 1000L)

        resetTime.text = formatRemainingTime(remainingSeconds)

        if (remainingSeconds <= 0L) {
            stopCountdown()
            targetResetElapsedRealtime = null
        }
    }

    private fun parseSecondsFromIso(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            val target = java.time.OffsetDateTime.parse(iso).toInstant()
            val now = java.time.Instant.now()
            max(0L, java.time.Duration.between(now, target).seconds)
        }.getOrNull()
    }

    override fun onDestroyView() {
        stopCountdown()
        if (::rankingList.isInitialized) {
            rankingList.adapter = null
        }
        super.onDestroyView()
    }
}
