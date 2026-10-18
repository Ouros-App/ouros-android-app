package com.ouros.app.feature.notification

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ouros.app.R
import com.ouros.app.core.fcm.OurosFirebaseMessagingService
import com.ouros.app.data.notification.NotificationItemResponse
import com.ouros.app.feature.main.MainActivity
import com.ouros.app.feature.tip.TipDetailActivity
import kotlinx.coroutines.launch

class NotificationActivity : AppCompatActivity() {

    private val viewModel: NotificationViewModel by viewModels {
        NotificationViewModel.Factory()
    }

    private lateinit var btnBack: ImageView
    private lateinit var btnOpenTestScreen: TextView
    private lateinit var btnMarkAllRead: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerNotifications: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutError: LinearLayout
    private lateinit var txtErrorMessage: TextView
    private lateinit var btnRetry: Button

    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.headerNotifications)) { view, insets ->
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
        setupRecyclerView()
        setupListeners()
        observeState()

        viewModel.loadNotifications()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        btnOpenTestScreen = findViewById(R.id.btnOpenTestScreen)
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        recyclerNotifications = findViewById(R.id.recyclerNotifications)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        progressLoading = findViewById(R.id.progressLoading)
        layoutError = findViewById(R.id.layoutError)
        txtErrorMessage = findViewById(R.id.txtErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter { item ->
            onNotificationClicked(item)
        }
        recyclerNotifications.layoutManager = LinearLayoutManager(this)
        recyclerNotifications.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnOpenTestScreen.setOnClickListener {
            startActivity(Intent(this, NotificationTestActivity::class.java))
        }

        findViewById<View>(R.id.titleContainer).setOnLongClickListener {
            startActivity(Intent(this, NotificationTestActivity::class.java))
            true
        }

        btnMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        swipeRefreshLayout.setColorSchemeResources(R.color.gold, R.color.blue)
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshNotifications()
        }

        btnRetry.setOnClickListener {
            viewModel.loadNotifications()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: NotificationUiState) {
        swipeRefreshLayout.isRefreshing = state.isRefreshing
        progressLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        if (state.errorMessage != null && state.notifications.isEmpty()) {
            layoutError.visibility = View.VISIBLE
            txtErrorMessage.text = state.errorMessage
            recyclerNotifications.visibility = View.GONE
            layoutEmpty.visibility = View.GONE
        } else {
            layoutError.visibility = View.GONE

            if (!state.isLoading && state.notifications.isEmpty()) {
                layoutEmpty.visibility = View.VISIBLE
                recyclerNotifications.visibility = View.GONE
            } else {
                layoutEmpty.visibility = View.GONE
                recyclerNotifications.visibility = View.VISIBLE
                adapter.submitList(state.notifications)
            }
        }

        btnMarkAllRead.visibility = View.GONE
    }

    private fun onNotificationClicked(item: NotificationItemResponse) {
        viewModel.markAsRead(item)

        val tipId = item.idTip ?: extractIdFromPayload(item.dataPayload, "tip_id")
        val isTip = item.type.equals("TIP_CREATED", ignoreCase = true) ||
                tipId != null ||
                NotificationAdapter.isGoldAlert(item)

        if (isTip) {
            val intent = TipDetailActivity.createIntent(this, tipId ?: 1L)
            startActivity(intent)
            return
        }

        val isGoal = item.type.contains("GOAL", ignoreCase = true) ||
                item.type.contains("TASK", ignoreCase = true) ||
                (item.title + " " + item.message).contains("tarefa", ignoreCase = true) ||
                (item.title + " " + item.message).contains("meta", ignoreCase = true)

        if (isGoal) {
            val goalId = extractIdFromPayload(item.dataPayload, "goal_id")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(OurosFirebaseMessagingService.EXTRA_TYPE, "GOAL_ASSIGNED")
                if (goalId != null) putExtra(OurosFirebaseMessagingService.EXTRA_GOAL_ID, goalId)
            }
            startActivity(intent)
            finish()
            return
        }

        val isRank = item.type.contains("RANK", ignoreCase = true) ||
                (item.title + " " + item.message).contains("ranking", ignoreCase = true)

        if (isRank) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(OurosFirebaseMessagingService.EXTRA_TYPE, "RANK_UPDATE")
            }
            startActivity(intent)
            finish()
            return
        }
    }

    private fun extractIdFromPayload(payload: String?, key: String): Long? {
        if (payload.isNullOrBlank()) return null
        val regex = Regex("\"?$key\"?\\s*[:=]\\s*\"?(\\d+)\"?", RegexOption.IGNORE_CASE)
        return regex.find(payload)?.groupValues?.get(1)?.toLongOrNull()
    }
}
