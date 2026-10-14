package com.ouros.app.feature.main

import android.content.Intent
import com.ouros.app.feature.secondary.profile.ProfileActivity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import coil.load
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.auth.login.LoginActivity
import com.ouros.app.feature.main.dashboard.DashboardFragment
import com.ouros.app.feature.main.goals.GoalsFragment
import com.ouros.app.feature.main.rank.RankFragment
import com.ouros.app.feature.main.record.RecordFragment
import com.ouros.app.feature.secondary.chatbot.ChatbotActivity
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

    private lateinit var navMidas: View
    private lateinit var footerItems: List<FooterItem>

    private var currentDestination =
        MainDestination.ANALYTICS

    private var currentProfile: MainProfileUiModel? = null
    private var mainLoading = false

    private val profilePhotoStore:
            ProfilePhotoStore by lazy {
        ProfilePhotoStore(applicationContext)
    }

    private val farmSessionStore: FarmSessionStore by lazy {
        FarmSessionStore(this)
    }

    private val farmOwnerRepository: FarmOwnerRepository by lazy {
        FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        bindHeaderViews()
        bindFooterViews()

        currentDestination =
            restoreDestination(savedInstanceState)

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
            imgAvatar.setImageResource(
                R.drawable.avatar_yellow
            )
        }

        btnNotifications.isEnabled = !isLoading
    }

    override fun onMainProfileLoaded(
        profile: MainProfileUiModel
    ) {
        currentProfile = profile
        renderHeader()

        val fallbackAvatar =
            avatarForUser(
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

        imgAvatar.setImageResource(
            R.drawable.avatar_yellow
        )

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onMainSessionExpired() {
        AppSession.sessionManager.clear()
        farmSessionStore.clearFarmId()

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

    private fun bindHeaderViews() {
        imgAvatar = findViewById(R.id.imgAvatar)
        txtGreeting = findViewById(R.id.txtGreeting)
        txtUserName = findViewById(R.id.txtUserName)
        btnNotifications = findViewById(
            R.id.btnNotifications
        )
    }

    private fun bindFooterViews() {
        navMidas = findViewById(R.id.navMidas)

        footerItems = listOf(
            FooterItem(
                container = findViewById(
                    R.id.navAnalytics
                ),
                icon = findViewById(
                    R.id.imgAnalytics
                ),
                label = findViewById(
                    R.id.txtAnalytics
                ),
                destination = MainDestination.ANALYTICS,
                defaultIcon = R.drawable.ic_chart,
                selectedIcon = R.drawable.ic_chart_on
            ),
            FooterItem(
                container = findViewById(
                    R.id.navGoals
                ),
                icon = findViewById(
                    R.id.imgGoals
                ),
                label = findViewById(
                    R.id.txtGoals
                ),
                destination = MainDestination.GOALS,
                defaultIcon = R.drawable.ic_check,
                selectedIcon = R.drawable.ic_check_on
            ),
            FooterItem(
                container = findViewById(
                    R.id.navRecord
                ),
                icon = findViewById(
                    R.id.imgRecord
                ),
                label = findViewById(
                    R.id.txtRecord
                ),
                destination = MainDestination.RECORD,
                defaultIcon = R.drawable.ic_drop,
                selectedIcon = R.drawable.ic_drop_on
            ),
            FooterItem(
                container = findViewById(
                    R.id.navRank
                ),
                icon = findViewById(
                    R.id.imgRank
                ),
                label = findViewById(
                    R.id.txtRank
                ),
                destination = MainDestination.RANK,
                defaultIcon = R.drawable.ic_trophy,
                selectedIcon = R.drawable.ic_trophy_on
            )
        )
    }

    private fun configureHeader() {
        renderHeader()

        imgAvatar.setImageResource(
            R.drawable.avatar_yellow
        )

        btnNotifications.setOnClickListener {
            Toast.makeText(
                this,
                "Tela de notificações ainda não implementada.",
                Toast.LENGTH_SHORT
            ).show()
        }
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
                    if (
                        currentDestination !=
                        MainDestination.ANALYTICS
                    ) {
                        showDestination(
                            MainDestination.ANALYTICS
                        )
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun configureProfileNavigation() {
        findViewById<View>(R.id.imgAvatar)
            .setOnClickListener {
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
        val localPhoto =
            profilePhotoStore.localPhotoFile()

        if (localPhoto != null) {
            imgAvatar.load(localPhoto) {
                crossfade(true)
                placeholder(fallbackAvatar)
                error(fallbackAvatar)
            }

            return
        }

        val selectedAvatar =
            profilePhotoStore
                .selectedAvatarResource()
                ?: fallbackAvatar

        if (
            profilePhotoStore
                .shouldIgnoreRemotePhoto()
        ) {
            imgAvatar.setImageResource(
                selectedAvatar
            )

            return
        }

        val normalizedPhotoUrl =
            remotePhotoUrl
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        if (normalizedPhotoUrl == null) {
            imgAvatar.setImageResource(
                selectedAvatar
            )

            return
        }

        imgAvatar.load(normalizedPhotoUrl) {
            crossfade(true)
            placeholder(selectedAvatar)
            error(selectedAvatar)
            fallback(selectedAvatar)
        }
    }

    private fun showDestination(
        destination: MainDestination
    ) {
        val fragmentManager = supportFragmentManager
        val targetTag = tagFor(destination)

        val visibleTarget =
            fragmentManager.findFragmentByTag(targetTag)

        if (
            currentDestination == destination &&
            visibleTarget?.isVisible == true
        ) {
            updateFooterSelection()
            return
        }

        val transaction =
            fragmentManager.beginTransaction()

        transaction.setReorderingAllowed(true)

        fragmentManager.fragments.forEach { fragment ->
            if (
                fragment.id == R.id.mainFragmentContainer
            ) {
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
                txtUserName.text = currentProfile?.name ?: if (mainLoading) "Carregando..." else "Usuário"
            }

            MainDestination.GOALS -> {
                txtGreeting.text = "Organize seus objetivos"
                txtUserName.text = "Metas"
            }

            MainDestination.RECORD -> {
                txtGreeting.text = "Salvar dados"
                txtUserName.text = "Registro"
            }

            MainDestination.RANK -> {
                txtGreeting.text = "Divisão Ouro"
                txtUserName.text = "Ranking"
            }
        }
    }

    private fun updateFooterSelection() {
        val selectedColor = ContextCompat.getColor(
            this,
            R.color.blue
        )

        val defaultColor =
            Color.parseColor("#999999")

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

            item.icon.imageTintList =
                ColorStateList.valueOf(
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

    private fun createFragment(
        destination: MainDestination
    ): Fragment {
        return when (destination) {
            MainDestination.ANALYTICS -> {
                DashboardFragment()
            }

            MainDestination.GOALS -> {
                GoalsFragment()
            }

            MainDestination.RECORD -> {
                RecordFragment()
            }

            MainDestination.RANK -> {
                RankFragment()
            }
        }
    }

    private fun tagFor(
        destination: MainDestination
    ): String {
        return when (destination) {
            MainDestination.ANALYTICS -> {
                TAG_DASHBOARD
            }

            MainDestination.GOALS -> {
                TAG_GOALS
            }

            MainDestination.RECORD -> {
                TAG_RECORD
            }

            MainDestination.RANK -> {
                TAG_RANK
            }
        }
    }

    private fun openChatBot() {
        val intent = Intent(
            this,
            ChatbotActivity::class.java
        )

        startActivity(intent)
    }

    private fun restoreDestination(
        savedInstanceState: Bundle?
    ): MainDestination {
        val destinationName = savedInstanceState
            ?.getString(STATE_CURRENT_DESTINATION)

        return runCatching {
            MainDestination.valueOf(
                destinationName.orEmpty()
            )
        }.getOrDefault(
            MainDestination.ANALYTICS
        )
    }

    private fun greetingForCurrentTime(): String {
        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

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
            userId?.hashCode() ?: name.hashCode()

        val position =
            Math.floorMod(seed, avatars.size)

        return avatars[position]
    }

    private companion object {
        const val STATE_CURRENT_DESTINATION =
            "current_destination"

        const val TAG_DASHBOARD =
            "main_dashboard"

        const val TAG_GOALS =
            "main_goals"

        const val TAG_RECORD =
            "main_record"

        const val TAG_RANK =
            "main_rank"
    }
}