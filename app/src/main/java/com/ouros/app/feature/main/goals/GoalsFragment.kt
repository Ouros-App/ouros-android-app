package com.ouros.app.feature.main.goals

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ouros.app.R
import com.ouros.app.core.SkeletonLoaderView
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.goals.GoalKey
import com.ouros.app.data.goals.GoalOrigin
import com.ouros.app.data.goals.GoalResponse
import com.ouros.app.data.goals.GoalsNetwork
import com.ouros.app.data.goals.GoalsRepository
import com.ouros.app.data.goals.GoalsUiState
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.main.MainActivity

class GoalsFragment : Fragment() {
    private lateinit var viewModel: GoalsViewModel
    private lateinit var progressGoals: CircularProgressIndicator
    private lateinit var textProgressPercent: TextView
    private lateinit var textProgressCount: TextView
    private lateinit var linearProgressGoals: LinearProgressIndicator
    private lateinit var loading: SkeletonLoaderView
    private lateinit var content: View
    private lateinit var error: TextView
    private lateinit var retry: View
    private lateinit var addGoal: View
    private lateinit var personalAdapter: GoalsAdapter
    private lateinit var stateAdapter: GoalsAdapter

    private var goalPopup: Dialog? = null
    private var popupConfirmButton: Button? = null
    private var popupDeleteButton: Button? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_goals, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureViewModel()
        bindViews(view)
        configureLists(view)
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
        val goalsRepository = GoalsRepository(
            api = GoalsNetwork.api,
            authRepository = authRepository,
            sessionManager = AppSession.sessionManager,
            farmSessionStore = FarmSessionStore(applicationContext)
        )
        viewModel = ViewModelProvider(
            requireActivity(),
            GoalsViewModelFactory(goalsRepository)
        )[GoalsViewModel::class.java]
    }

    private fun bindViews(root: View) {
        progressGoals = root.findViewById(R.id.progressGoals)
        textProgressPercent = root.findViewById(R.id.textProgressPercent)
        textProgressCount = root.findViewById(R.id.textProgressCount)
        linearProgressGoals = root.findViewById(R.id.linearProgressGoals)
        loading = root.findViewById(R.id.goalsLoading)
        content = root.findViewById(R.id.goalsContent)
        error = root.findViewById(R.id.goalsError)
        retry = root.findViewById(R.id.goalsRetry)
        addGoal = root.findViewById(R.id.buttonAddGoal)
        loading.mode = SkeletonLoaderView.Mode.GOALS
    }

    private fun configureLists(root: View) {
        personalAdapter = GoalsAdapter(
            onToggle = { goal, checked ->
                viewModel.toggleGoal(
                    origin = GoalOrigin.PERSONAL,
                    goal = goal,
                    completed = checked
                )
            },
            onTitleClick = { goal ->
                showGoalPopup(goal)
            }
        )

        stateAdapter = GoalsAdapter(
            onToggle = { goal, checked ->
                viewModel.toggleGoal(
                    origin = GoalOrigin.STATE,
                    goal = goal,
                    completed = checked
                )
            }
        )

        root.findViewById<RecyclerView>(R.id.recyclerPersonalGoals).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = personalAdapter
            isNestedScrollingEnabled = false
        }

        root.findViewById<RecyclerView>(R.id.recyclerStateGoals).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stateAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun configureActions() {
        addGoal.setOnClickListener {
            showGoalPopup(null)
        }

        retry.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun observeState() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            render(state)
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is GoalsEvent.ShowMessage -> {
                    viewModel.consumeEvent()
                    Toast.makeText(
                        requireContext(),
                        event.message,
                        Toast.LENGTH_LONG
                    ).show()
                }

                GoalsEvent.CloseGoalPopup -> {
                    viewModel.consumeEvent()
                    goalPopup?.dismiss()
                }

                GoalsEvent.AuthenticationRequired -> {
                    viewModel.consumeEvent()
                    (activity as? MainActivity)?.onMainSessionExpired()
                }

                null -> Unit
            }
        }
    }

    private fun render(state: GoalsUiState) {
        progressGoals.setProgressCompat(state.progress, true)
        linearProgressGoals.setProgressCompat(state.progress, true)
        textProgressPercent.text = "${state.progress}%"
        textProgressCount.text = if (state.totalGoals == 1) {
            "${state.completedGoals} de ${state.totalGoals} meta completada"
        } else {
            "${state.completedGoals} de ${state.totalGoals} metas completadas"
        }

        if (state.isLoading) {
            loading.visibility = View.VISIBLE
            loading.start()
            content.visibility = View.INVISIBLE
        } else {
            loading.stop()
            loading.visibility = View.GONE
            content.visibility = if (state.error == null) View.VISIBLE else View.INVISIBLE
        }

        error.text = state.error.orEmpty()
        error.visibility = if (state.error == null) View.GONE else View.VISIBLE
        retry.visibility = if (state.error != null && state.canRetry) View.VISIBLE else View.GONE
        addGoal.isEnabled = !state.isLoading && !state.isPopupSaving
        popupConfirmButton?.isEnabled = !state.isPopupSaving
        popupDeleteButton?.isEnabled = !state.isPopupSaving

        personalAdapter.submitList(
            state.personalGoals.map { goal ->
                GoalListItem(
                    goal = goal,
                    isUpdating = GoalKey(
                        origin = GoalOrigin.PERSONAL,
                        id = goal.id
                    ) in state.updatingGoals
                )
            }
        )

        stateAdapter.submitList(
            state.stateGoals.map { goal ->
                GoalListItem(
                    goal = goal,
                    isUpdating = GoalKey(
                        origin = GoalOrigin.STATE,
                        id = goal.id
                    ) in state.updatingGoals
                )
            }
        )
    }

    private fun showGoalPopup(goal: GoalResponse?) {
        if (goalPopup?.isShowing == true) return

        val popupContent = layoutInflater.inflate(R.layout.popup_goal, null)
        val editTitle = popupContent.findViewById<EditText>(R.id.editGoalTitle)
        val editType = popupContent.findViewById<AutoCompleteTextView>(R.id.editGoalType)
        val editDescription = popupContent.findViewById<EditText>(R.id.editGoalDesc)
        val confirmButton = popupContent.findViewById<Button>(R.id.buttonConfirmGoal)
        val deleteButton = popupContent.findViewById<Button>(R.id.buttonDeleteGoal)
        val backButton = popupContent.findViewById<View>(R.id.btnBack)

        val goalTypes = arrayOf("Água", "Energia", "Bem-estar", "Outro")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_goal_type_dropdown, goalTypes)
        editType.setAdapter(adapter)

        editTitle.setText(goal?.title.orEmpty())
        editDescription.setText(goal?.description.orEmpty())

        val initialType = when {
            goal != null && goal.type.isNotBlank() -> goal.type
            else -> goalTypes[0]
        }
        editType.setText(initialType, false)
        editType.isEnabled = (goal == null)

        deleteButton.visibility = if (goal == null) View.GONE else View.VISIBLE

        val popup = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(popupContent)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                goalPopup = null
                popupConfirmButton = null
                popupDeleteButton = null
            }
        }

        confirmButton.setOnClickListener {
            viewModel.saveGoal(
                goal = goal,
                title = editTitle.text.toString(),
                description = editDescription.text.toString(),
                type = editType.text.toString().ifBlank { "Outro" }
            )
        }

        deleteButton.setOnClickListener {
            if (goal != null) viewModel.deleteGoal(goal)
        }

        backButton.setOnClickListener {
            popup.dismiss()
        }

        goalPopup = popup
        popupConfirmButton = confirmButton
        popupDeleteButton = deleteButton
        popup.show()

        popup.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

            val windowAttributes = attributes
            windowAttributes.dimAmount = 0.18f

            addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            windowAttributes.blurBehindRadius = 35

            attributes = windowAttributes
        }
    }

    override fun onDestroyView() {
        goalPopup?.dismiss()
        goalPopup = null
        popupConfirmButton = null
        popupDeleteButton = null

        if (::loading.isInitialized) {
            loading.stop()
        }

        super.onDestroyView()
    }
}
