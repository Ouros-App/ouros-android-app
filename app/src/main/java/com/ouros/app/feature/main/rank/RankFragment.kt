package com.ouros.app.feature.main.rank

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private lateinit var currentLeague: TextView
    private lateinit var resetTime: TextView
    private lateinit var rankingList: RecyclerView

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
        currentLeague = root.findViewById(R.id.textCurrentLeague)
        resetTime = root.findViewById(R.id.textResetTime)
        rankingList = root.findViewById(R.id.recyclerRanking)
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
            currentPosition.text = "--"
            currentLeague.text = "Você está sem divisão"
            adapter.submitList(emptyList())
            return
        }

        currentPosition.text =
            ranking.currentEntry?.let { "${it.rankPosition}º" } ?: "--"

        currentLeague.text =
            league?.let { "Você é um ${it.uppercaseName}" }
                ?: "Você está sem divisão"

        resetTime.text =
            ranking.timeUntilNextReset
                ?.takeIf(String::isNotBlank)
                ?: formatRemainingTime(
                    ranking.secondsUntilNextReset
                )

        adapter.submitList(ranking.rankingPodium)
    }

    private fun formatRemainingTime(seconds: Long?): String {
        val remaining = max(0L, seconds ?: 0L)
        val days = remaining / 86_400

        if (days > 0) {
            return if (days == 1L) "1 dia" else "$days dias"
        }

        val hours = remaining / 3_600

        if (hours > 0) {
            return if (hours == 1L) "1 hora" else "$hours horas"
        }

        val minutes = remaining / 60

        return if (minutes == 1L) {
            "1 minuto"
        } else {
            "$minutes minutos"
        }
    }

    override fun onDestroyView() {
        if (::rankingList.isInitialized) {
            rankingList.adapter = null
        }
        super.onDestroyView()
    }
}
