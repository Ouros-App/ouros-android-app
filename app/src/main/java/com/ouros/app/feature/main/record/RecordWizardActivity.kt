package com.ouros.app.feature.main.record

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.R
import com.ouros.app.data.record.RecordDraftStore
import com.ouros.app.data.record.RecordNetwork
import com.ouros.app.data.record.RecordRepository
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.auth.login.LoginActivity

class RecordWizardActivity : AppCompatActivity() {
    lateinit var wizardViewModel: RecordWizardViewModel
        private set

    private lateinit var loading: View
    private var confirmDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_record_wizard)

        configureViewModel()
        bindViews()
        observeViewModel()
        configureBackButton()

        if (savedInstanceState == null) {
            showStep(
                wizardViewModel
                    .currentDraft()
                    .step
                    .coerceIn(FIRST_STEP, LAST_STEP)
            )
        }
    }

    private fun configureViewModel() {
        val draftStore = RecordDraftStore(applicationContext)

        val repository = RecordRepository(
            api = RecordNetwork.api,
            draftStore = draftStore
        )

        val farmId = FarmSessionStore(
            applicationContext
        ).farmId()

        wizardViewModel = ViewModelProvider(
            this,
            RecordWizardViewModelFactory(
                repository = repository,
                draftStore = draftStore,
                farmId = farmId
            )
        )[RecordWizardViewModel::class.java]
    }

    private fun bindViews() {
        loading = findViewById(R.id.recordWizardLoading)
    }

    private fun configureBackButton() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    previousStep()
                }
            }
        )
    }

    fun nextStep(step: Int) {
        val safeStep = step.coerceIn(FIRST_STEP, LAST_STEP)

        wizardViewModel.setStep(safeStep)
        showStep(safeStep)
    }

    fun previousStep() {
        val currentStep = wizardViewModel
            .currentDraft()
            .step
            .coerceIn(FIRST_STEP, LAST_STEP)

        if (currentStep == FIRST_STEP) {
            finishKeepingDraft()
            return
        }

        nextStep(currentStep - 1)
    }

    fun finishKeepingDraft() {
        finish()
    }

    fun confirmRegistration() {
        if (confirmDialog?.isShowing == true) {
            return
        }

        val popupContent = layoutInflater.inflate(
            R.layout.popup_record_confirm,
            null
        )

        val popup = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(popupContent)
            setCancelable(true)
            setCanceledOnTouchOutside(true)

            window?.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            popupContent
                .findViewById<View>(R.id.recordConfirmBack)
                .setOnClickListener {
                    dismiss()
                }

            popupContent
                .findViewById<Button>(R.id.buttonRecordYes)
                .setOnClickListener {
                    dismiss()
                    wizardViewModel.submit()
                }

            popupContent
                .findViewById<Button>(R.id.buttonRecordNo)
                .setOnClickListener {
                    dismiss()
                }

            setOnDismissListener {
                confirmDialog = null
            }
        }

        confirmDialog = popup
        popup.show()

        popup.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * POPUP_WIDTH_PERCENTAGE)
                    .toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            )

            attributes = attributes.apply {
                dimAmount = POPUP_DIM_AMOUNT
                blurBehindRadius = POPUP_BLUR_RADIUS
            }
        }
    }

    private fun showStep(step: Int) {
        val fragment = when (step) {
            STEP_LOT -> RecordLotFragment()
            STEP_WATER -> RecordWaterFragment()
            STEP_ENERGY -> RecordEnergyFragment()
            STEP_CHICKENS -> RecordChickensFragment()
            else -> RecordLotFragment()
        }

        supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(
                R.id.recordWizardContainer,
                fragment,
                "record_step_$step"
            )
            .commit()
    }

    private fun observeViewModel() {
        wizardViewModel.state.observe(this) { state ->
            loading.visibility = if (state.submitting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        wizardViewModel.event.observe(this) { event ->
            when (event) {
                RecordWizardEvent.Completed -> {
                    setResult(RESULT_OK)
                    finish()
                }

                RecordWizardEvent.AuthenticationRequired -> {
                    openLogin()
                }

                is RecordWizardEvent.Error -> {
                    Toast.makeText(
                        this,
                        event.message,
                        Toast.LENGTH_LONG
                    ).show()
                }

                null -> Unit
            }

            if (event != null) {
                wizardViewModel.consumeEvent()
            }
        }
    }

    private fun openLogin() {
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

    override fun onDestroy() {
        confirmDialog?.dismiss()
        confirmDialog = null

        super.onDestroy()
    }

    private companion object {
        const val FIRST_STEP = 1
        const val LAST_STEP = 4

        const val STEP_LOT = 1
        const val STEP_WATER = 2
        const val STEP_ENERGY = 3
        const val STEP_CHICKENS = 4

        const val POPUP_WIDTH_PERCENTAGE = 0.72f
        const val POPUP_DIM_AMOUNT = 0.18f
        const val POPUP_BLUR_RADIUS = 35
    }
}