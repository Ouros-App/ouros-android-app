package com.ouros.app.feature.main.record

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ouros.app.R
import com.ouros.app.data.record.RecordDraftStore
import com.ouros.app.data.record.RecordNetwork
import com.ouros.app.data.record.RecordRepository
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.auth.login.LoginActivity
import com.ouros.app.feature.secondary.simulator.SimulatorActivity

class RecordFragment : Fragment() {
    private lateinit var viewModel: RecordViewModel
    private lateinit var draftStore: RecordDraftStore
    private lateinit var loading: View
    private lateinit var content: View
    private lateinit var error: TextView
    private lateinit var retry: View
    private lateinit var draftTitle: TextView
    private lateinit var draftSubtitle: TextView
    private lateinit var emptyWater: TextView
    private lateinit var emptyEnergy: TextView
    private lateinit var waterAdapter: RecordHistoryAdapter
    private lateinit var energyAdapter: RecordHistoryAdapter
    private var successDialog: Dialog? = null
    private var deleteDialog: Dialog? = null

    private val wizardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            draftStore.clear()
            renderDraftCard()
            viewModel.load()
            showSuccessPopup()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_record, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContext = requireContext().applicationContext
        draftStore = RecordDraftStore(appContext)
        val repository = RecordRepository(RecordNetwork.api, draftStore)
        viewModel = ViewModelProvider(
            this,
            RecordViewModelFactory(
                repository,
                draftStore,
                FarmSessionStore(appContext).farmId()
            )
        )[RecordViewModel::class.java]

        bind(view)
        configureLists(view)

        view.findViewById<View>(R.id.recordStartCard).apply {
            setOnClickListener {
                viewModel.newOrResume()
            }
            setOnLongClickListener {
                if (draftStore.load()?.hasContent() == true) {
                    showDeleteDraftPopup()
                    true
                } else {
                    false
                }
            }
        }

        view.findViewById<View>(R.id.recordSimulatorCard).setOnClickListener {
            startActivity(Intent(requireContext(), SimulatorActivity::class.java))
        }

        retry.setOnClickListener {
            viewModel.load()
        }

        observe()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            renderDraftCard()
            viewModel.load()
        }
    }

    private fun bind(root: View) {
        loading = root.findViewById(R.id.recordLoading)
        content = root.findViewById(R.id.recordContent)
        error = root.findViewById(R.id.recordError)
        retry = root.findViewById(R.id.recordRetry)
        draftTitle = root.findViewById(R.id.recordDraftTitle)
        draftSubtitle = root.findViewById(R.id.recordDraftSubtitle)
        emptyWater = root.findViewById(R.id.recordEmptyWater)
        emptyEnergy = root.findViewById(R.id.recordEmptyEnergy)
    }

    private fun configureLists(root: View) {
        waterAdapter = RecordHistoryAdapter(viewModel::edit)
        energyAdapter = RecordHistoryAdapter(viewModel::edit)

        root.findViewById<RecyclerView>(R.id.waterHistoryList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = waterAdapter
            isNestedScrollingEnabled = false
        }

        root.findViewById<RecyclerView>(R.id.energyHistoryList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = energyAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observe() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            loading.visibility = if (state.loading) View.VISIBLE else View.GONE
            content.visibility = if (state.error == null) View.VISIBLE else View.GONE
            error.visibility = if (state.error != null) View.VISIBLE else View.GONE
            retry.visibility = if (state.error != null) View.VISIBLE else View.GONE
            error.text = state.error

            waterAdapter.submitList(state.water)
            energyAdapter.submitList(state.energy)

            emptyWater.visibility =
                if (!state.loading && state.water.isEmpty()) View.VISIBLE else View.GONE
            emptyEnergy.visibility =
                if (!state.loading && state.energy.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                RecordEvent.OpenWizard -> {
                    wizardLauncher.launch(
                        Intent(requireContext(), RecordWizardActivity::class.java)
                    )
                }

                RecordEvent.AuthenticationRequired -> {
                    startActivity(
                        Intent(requireContext(), LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }

                null -> Unit
            }

            if (event != null) viewModel.consumeEvent()
        }
    }

    private fun renderDraftCard() {
        val draft = draftStore.load()?.takeIf { it.hasContent() }

        draftTitle.text = if (draft == null) "Preparado?" else "Pronto?"
        draftSubtitle.text = if (draft == null) {
            "Vamos começar um novo registro."
        } else {
            "Vamos continuar o registro do dia ${draft.displayDate()}"
        }
    }

    private fun showDeleteDraftPopup() {
        if (deleteDialog?.isShowing == true) return

        val popupContent = layoutInflater.inflate(
            R.layout.popup_record_delete,
            null
        )

        val popup = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(popupContent)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            popupContent.findViewById<Button>(R.id.buttonRecordYes).setOnClickListener {
                draftStore.clear()
                renderDraftCard()
                dismiss()
            }

            popupContent.findViewById<Button>(R.id.buttonRecordNo).setOnClickListener {
                dismiss()
            }

            popupContent.findViewById<View>(R.id.recordConfirmBack).setOnClickListener {
                dismiss()
            }

            setOnDismissListener {
                deleteDialog = null
            }
        }

        deleteDialog = popup
        popup.show()

        popup.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.72f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )
            attributes = attributes.apply {
                dimAmount = 0.18f
                blurBehindRadius = 35
            }
        }
    }

    private fun showSuccessPopup() {
        if (successDialog?.isShowing == true) return

        val popup = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(layoutInflater.inflate(R.layout.popup_record_success, null))
            setCancelable(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            findViewById<Button>(R.id.buttonRecordOkay).setOnClickListener {
                dismiss()
            }
            setOnDismissListener {
                successDialog = null
            }
        }

        successDialog = popup
        popup.show()

        popup.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.72f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )
            attributes = attributes.apply {
                dimAmount = 0.18f
                blurBehindRadius = 35
            }
        }
    }

    override fun onDestroyView() {
        successDialog?.dismiss()
        successDialog = null
        deleteDialog?.dismiss()
        deleteDialog = null
        super.onDestroyView()
    }
}