package com.ouros.app.feature.main

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import coil.load
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.dashboard.DashboardPeriod
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.rank.RankLeague
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.auth.login.LoginActivity
import com.ouros.app.feature.main.dashboard.DashboardFragment
import com.ouros.app.feature.main.goals.GoalsFragment
import com.ouros.app.feature.main.rank.RankFragment
import com.ouros.app.feature.main.record.RecordFragment
import com.ouros.app.feature.secondary.chatbot.ChatbotActivity
import com.ouros.app.feature.secondary.profile.ProfileActivity
import com.ouros.app.feature.secondary.profile.ProfilePhotoStore
import java.util.Calendar

class MainActivity : AppCompatActivity(), MainViewModel.Listener {
    private data class FooterItem(
        val container: View,
        val icon: ImageView,
        val label: TextView,
        val destination: MainDestination,
        val defaultIcon: Int,
        val selectedIcon: Int
    )

    private lateinit var imgAvatar: ImageView
    private lateinit var txtGreeting: TextView
    private lateinit var txtUserName: TextView
    private lateinit var btnNotifications: ImageView
    private lateinit var btnPeriodSelector: View
    private lateinit var txtSelectedPeriod: TextView
    private lateinit var navMidas: View
    private lateinit var footerItems: List<FooterItem>

    private var currentDestination = MainDestination.ANALYTICS
    private var currentProfile: MainProfileUiModel? = null
    private var currentRankLeague: RankLeague? = null
    private var mainLoading = false
    private var profilePhotoStore: ProfilePhotoStore? = null

    private val farmSessionStore: FarmSessionStore by lazy {
        FarmSessionStore(this)
    }

    private val authRepository: AuthRepository by lazy {
        AuthRepository(
            api = AuthNetwork.api,
            refreshTokenStore = SecureRefreshTokenStore(applicationContext),
            sessionManager = AppSession.sessionManager
        )
    }

    private val farmOwnerRepository: FarmOwnerRepository by lazy {
        FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )
    }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            farmOwnerRepository = farmOwnerRepository,
            farmSessionStore = farmSessionStore
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.TRANSPARENT
            )
        )
        setContentView(R.layout.activity_main)

        bindHeaderViews()
        bindFooterViews()
        currentDestination = restoreDestination(savedInstanceState)
        configureHeader()
        configureFooter()
        configureBackButton()
        configureProfileNavigation()
        showDestination(currentDestination)
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.setListener(this)
        mainViewModel.loadProfile()
    }

    override fun onStop() {
        mainViewModel.clearListener(this)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            STATE_CURRENT_DESTINATION,
            currentDestination.name
        )
        super.onSaveInstanceState(outState)
    }

    override fun onMainLoadingChanged(isLoading: Boolean) {
        mainLoading = isLoading
        renderHeader()

        if (isLoading && currentProfile == null) {
            imgAvatar.setImageResource(R.drawable.avatar_yellow)
        }

        btnNotifications.isEnabled = !isLoading
    }

    override fun onMainProfileLoaded(profile: MainProfileUiModel) {
        currentProfile = profile
        profilePhotoStore = profile.farmOwnerId?.let {
            ProfilePhotoStore(applicationContext, it)
        }
        renderHeader()

        val fallbackAvatar = avatarForUser(
            userId = profile.farmOwnerId,
            name = profile.name
        )

        renderProfileAvatar(
            remotePhotoUrl = profile.photoUrl,
            fallbackAvatar = fallbackAvatar
        )
    }

    override fun onMainProfileError(message: String) {
        renderHeader()
        imgAvatar.setImageResource(R.drawable.avatar_yellow)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onMainSessionExpired() {
        AppSession.sessionManager.clear()
        farmSessionStore.clear()
        profilePhotoStore = null
        currentRankLeague = null

        val intent = Intent(
            this,
            LoginActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    fun updateRankDivision(league: RankLeague?) {
        currentRankLeague = league

        if (currentDestination == MainDestination.RANK) {
            renderHeader()
        }
    }

    private fun bindHeaderViews() {
        imgAvatar = findViewById(R.id.imgAvatar)
        txtGreeting = findViewById(R.id.txtGreeting)
        txtUserName = findViewById(R.id.txtUserName)
        btnNotifications = findViewById(R.id.btnNotifications)
        btnPeriodSelector = findViewById(R.id.btnPeriodSelector)
        txtSelectedPeriod = findViewById(R.id.txtSelectedPeriod)
    }

    private fun bindFooterViews() {
        navMidas = findViewById(R.id.navMidas)

        footerItems = listOf(
            FooterItem(
                container = findViewById(R.id.navAnalytics),
                icon = findViewById(R.id.imgAnalytics),
                label = findViewById(R.id.txtAnalytics),
                destination = MainDestination.ANALYTICS,
                defaultIcon = R.drawable.ic_chart,
                selectedIcon = R.drawable.ic_chart_on
            ),
            FooterItem(
                container = findViewById(R.id.navGoals),
                icon = findViewById(R.id.imgGoals),
                label = findViewById(R.id.txtGoals),
                destination = MainDestination.GOALS,
                defaultIcon = R.drawable.ic_check,
                selectedIcon = R.drawable.ic_check_on
            ),
            FooterItem(
                container = findViewById(R.id.navRecord),
                icon = findViewById(R.id.imgRecord),
                label = findViewById(R.id.txtRecord),
                destination = MainDestination.RECORD,
                defaultIcon = R.drawable.ic_drop,
                selectedIcon = R.drawable.ic_drop_on
            ),
            FooterItem(
                container = findViewById(R.id.navRank),
                icon = findViewById(R.id.imgRank),
                label = findViewById(R.id.txtRank),
                destination = MainDestination.RANK,
                defaultIcon = R.drawable.ic_trophy,
                selectedIcon = R.drawable.ic_trophy_on
            )
        )
    }

    private fun configureHeader() {
        renderHeader()
        imgAvatar.setImageResource(R.drawable.avatar_yellow)

        btnNotifications.setOnClickListener {
            Toast.makeText(
                this,
                "Tela de notificações ainda não implementada.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnPeriodSelector.setOnClickListener {
            showPeriodSelectionMenu()
        }
    }

    private fun showPeriodSelectionMenu() {
        val popup = PopupMenu(this, btnPeriodSelector)
        popup.menu.add(0, 1, 0, DashboardPeriod.YEAR.label)
        popup.menu.add(0, 2, 1, DashboardPeriod.QUARTER.label)
        popup.menu.add(0, 3, 2, DashboardPeriod.MONTH.label)

        popup.setOnMenuItemClickListener { item ->
            val selectedPeriod = when (item.itemId) {
                1 -> DashboardPeriod.YEAR
                2 -> DashboardPeriod.QUARTER
                3 -> DashboardPeriod.MONTH
                else -> DashboardPeriod.YEAR
            }
            txtSelectedPeriod.text = selectedPeriod.label

            val dashboardFragment =
                supportFragmentManager.findFragmentByTag(tagFor(MainDestination.ANALYTICS)) as? DashboardFragment
            dashboardFragment?.selectPeriod(selectedPeriod)
            true
        }
        popup.show()
    }

    private fun configureFooter() {
        footerItems.forEach { item ->
            item.container.isClickable = true
            item.container.isFocusable = true
            item.container.setOnClickListener {
                showDestination(item.destination)
            }
        }

        navMidas.isClickable = true
        navMidas.isFocusable = true
        navMidas.setOnClickListener {
            openChatBot()
        }

        updateFooterSelection()
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentDestination != MainDestination.ANALYTICS) {
                        showDestination(MainDestination.ANALYTICS)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun configureProfileNavigation() {
        imgAvatar.setOnClickListener {
            mainViewModel.clearProfileCache()
            startActivity(
                Intent(
                    this,
                    ProfileActivity::class.java
                )
            )
        }
    }

    private fun renderProfileAvatar(
        remotePhotoUrl: String?,
        fallbackAvatar: Int
    ) {
        val store = profilePhotoStore
        store?.syncWithRemote(remotePhotoUrl)

        val localPhoto = store?.localPhotoFile()

        if (localPhoto != null) {
            imgAvatar.load(localPhoto) {
                crossfade(true)
                placeholder(fallbackAvatar)
                error(fallbackAvatar)
            }
            return
        }

        val selectedAvatar =
            store?.selectedAvatarResource()
                ?: fallbackAvatar

        if (store?.shouldIgnoreRemotePhoto() == true) {
            imgAvatar.setImageResource(selectedAvatar)
            return
        }

        val normalizedPhotoUrl =
            remotePhotoUrl
                ?.trim()
                ?.takeIf(String::isNotBlank)

        if (normalizedPhotoUrl == null) {
            imgAvatar.setImageResource(selectedAvatar)
            return
        }

        imgAvatar.load(normalizedPhotoUrl) {
            crossfade(true)
            placeholder(selectedAvatar)
            error(selectedAvatar)
            fallback(selectedAvatar)
        }
    }

    private fun showDestination(destination: MainDestination) {
        val fragmentManager = supportFragmentManager
        val targetTag = tagFor(destination)
        val visibleTarget = fragmentManager.findFragmentByTag(targetTag)

        if (
            currentDestination == destination &&
            visibleTarget?.isVisible == true
        ) {
            renderHeader()
            updateFooterSelection()
            return
        }

        val transaction = fragmentManager.beginTransaction()
        transaction.setReorderingAllowed(true)

        fragmentManager.fragments.forEach { fragment ->
            if (fragment.id == R.id.mainFragmentContainer) {
                transaction.hide(fragment)
            }
        }

        val targetFragment =
            fragmentManager.findFragmentByTag(targetTag)

        if (targetFragment == null) {
            transaction.add(
                R.id.mainFragmentContainer,
                createFragment(destination),
                targetTag
            )
        } else {
            transaction.show(targetFragment)
        }

        transaction.commit()
        currentDestination = destination
        renderHeader()
        updateFooterSelection()
    }

    private fun renderHeader() {
        when (currentDestination) {
            MainDestination.ANALYTICS -> {
                txtGreeting.text = greetingForCurrentTime()
                txtUserName.text =
                    currentProfile?.name
                        ?: if (mainLoading) "Carregando..."
                        else "Usuário"
                btnPeriodSelector.visibility = View.VISIBLE
            }
            MainDestination.GOALS -> {
                txtGreeting.text = "Organize seus objetivos"
                txtUserName.text = "Metas"
                btnPeriodSelector.visibility = View.GONE
            }
            MainDestination.RECORD -> {
                txtGreeting.text = "Salvar dados"
                txtUserName.text = "Registro"
                btnPeriodSelector.visibility = View.GONE
            }
            MainDestination.RANK -> {
                txtGreeting.text =
                    currentRankLeague?.let {
                        "Divisão ${it.displayName}"
                    } ?: "Divisão"
                txtUserName.text = "Ranking"
                btnPeriodSelector.visibility = View.GONE
            }
        }
    }

    private fun updateFooterSelection() {
        val selectedColor = ContextCompat.getColor(this, R.color.blue)
        val defaultColor = Color.parseColor("#999999")

        footerItems.forEach { item ->
            val selected =
                item.destination == currentDestination

            item.container.isSelected = selected
            item.icon.isSelected = selected
            item.label.isSelected = selected

            item.icon.setImageResource(
                if (selected) {
                    item.selectedIcon
                } else {
                    item.defaultIcon
                }
            )

            item.icon.imageTintList = ColorStateList.valueOf(
                if (selected) {
                    selectedColor
                } else {
                    defaultColor
                }
            )

            item.label.setTextAppearance(
                if (selected) {
                    R.style.footer_text_on
                } else {
                    R.style.footer_text
                }
            )
        }
    }

    private fun createFragment(destination: MainDestination): Fragment {
        return when (destination) {
            MainDestination.ANALYTICS -> DashboardFragment()
            MainDestination.GOALS -> GoalsFragment()
            MainDestination.RECORD -> RecordFragment()
            MainDestination.RANK -> RankFragment()
        }
    }

    private fun tagFor(destination: MainDestination): String {
        return when (destination) {
            MainDestination.ANALYTICS -> TAG_DASHBOARD
            MainDestination.GOALS -> TAG_GOALS
            MainDestination.RECORD -> TAG_RECORD
            MainDestination.RANK -> TAG_RANK
        }
    }

    private fun openChatBot() {
        startActivity(
            Intent(
                this,
                ChatbotActivity::class.java
            )
        )
    }

    private fun restoreDestination(
        savedInstanceState: Bundle?
    ): MainDestination {
        val destinationName =
            savedInstanceState?.getString(
                STATE_CURRENT_DESTINATION
            )

        return runCatching {
            MainDestination.valueOf(
                destinationName.orEmpty()
            )
        }.getOrDefault(MainDestination.ANALYTICS)
    }

    private fun greetingForCurrentTime(): String {
        val hour =
            Calendar.getInstance().get(
                Calendar.HOUR_OF_DAY
            )

        return when (hour) {
            in 5..11 -> "Bom dia,"
            in 12..17 -> "Boa tarde,"
            else -> "Boa noite,"
        }
    }

    private fun avatarForUser(
        userId: Long?,
        name: String
    ): Int {
        val avatars = intArrayOf(
            R.drawable.avatar_yellow,
            R.drawable.avatar_blue,
            R.drawable.avatar_green,
            R.drawable.avatar_purple,
            R.drawable.avatar_orange
        )

        val seed =
            userId?.hashCode()
                ?: name.hashCode()

        return avatars[
            Math.floorMod(
                seed,
                avatars.size
            )
        ]
    }

    private companion object {
        const val STATE_CURRENT_DESTINATION = "current_destination"
        const val TAG_DASHBOARD = "main_dashboard"
        const val TAG_GOALS = "main_goals"
        const val TAG_RECORD = "main_record"
        const val TAG_RANK = "main_rank"
    }
}
