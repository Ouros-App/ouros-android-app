package com.ouros.app.feature.secondary.simulator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.ouros.app.R
import com.ouros.app.core.BrazilianMoney
import com.ouros.app.core.BrazilianMoneyTextWatcher
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.data.simulator.SimulatorNetwork
import com.ouros.app.data.simulator.SimulatorRepository
import com.ouros.app.data.simulator.SimulatorUiState
import com.ouros.app.feature.auth.login.LoginActivity
import java.util.Locale

class SimulatorActivity : AppCompatActivity() {
    private lateinit var viewModel: SimulatorViewModel
    private lateinit var content: View
    private lateinit var loading: View
    private lateinit var error: TextView
    private lateinit var retry: View
    private lateinit var consumptionCard: TextView
    private lateinit var energyCard: TextView
    private lateinit var savingsCard: TextView
    private lateinit var positionsCard: TextView
    private lateinit var currentCgiInput: TextInputEditText
    private lateinit var targetCgiInput: TextInputEditText
    private lateinit var waterPriceInput: TextInputEditText
    private lateinit var energyPriceInput: TextInputEditText
    private lateinit var chickensInput: TextInputEditText
    private lateinit var targetEnergyInput: TextInputEditText
    private lateinit var simulateButton: Button
    private var populateInitialFields = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_simulator)
        populateInitialFields = savedInstanceState == null
        configureViewModel()
        bindViews()
        configureActions()
        observeViewModel()
        viewModel.loadInitialData()
    }

    private fun configureViewModel() {
        val farmId = FarmSessionStore(applicationContext).farmId()
        viewModel = ViewModelProvider(
            this,
            SimulatorViewModelFactory(
                repository = SimulatorRepository(SimulatorNetwork.api),
                farmId = farmId
            )
        )[SimulatorViewModel::class.java]
    }

    private fun bindViews() {
        content = findViewById(R.id.simulatorContent)
        val kpiContainer = findViewById<View>(R.id.simulatorKpiContainer)
        val formContainer = findViewById<View>(R.id.simulatorFormContainer)

        loading = findViewById(R.id.simulatorLoading)
        error = findViewById(R.id.simulatorError)
        retry = findViewById(R.id.simulatorRetry)
        consumptionCard = findViewById(R.id.txtSimulatorConsumption)
        energyCard = findViewById(R.id.txtSimulatorEnergy)
        savingsCard = findViewById(R.id.txtSimulatorSavings)
        positionsCard = findViewById(R.id.txtSimulatorPositions)
        currentCgiInput = findViewById(R.id.inputCurrentCgi)
        targetCgiInput = findViewById(R.id.inputTargetCgi)
        waterPriceInput = findViewById(R.id.inputWaterPrice)
        energyPriceInput = findViewById(R.id.inputEnergyPrice)
        chickensInput = findViewById(R.id.inputChickens)
        targetEnergyInput = findViewById(R.id.inputTargetEnergy)
        simulateButton = findViewById(R.id.buttonSimulator)

        waterPriceInput.addTextChangedListener(BrazilianMoneyTextWatcher(waterPriceInput) {})
        energyPriceInput.addTextChangedListener(BrazilianMoneyTextWatcher(energyPriceInput) {})

    }

    private fun configureActions() {
        findViewById<View>(R.id.btnSimulatorBack).setOnClickListener { finish() }
        retry.setOnClickListener { viewModel.retry() }
        simulateButton.setOnClickListener { submitSimulation() }
        currentCgiInput.isEnabled = false
    }

    private fun observeViewModel() {
        viewModel.state.observe(this, ::render)
        viewModel.event.observe(this) { event ->
            when (event) {
                SimulatorEvent.AuthenticationRequired -> openLogin()
                is SimulatorEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
                null -> Unit
            }
            if (event != null) viewModel.consumeEvent()
        }
    }

    private fun render(state: SimulatorUiState) {
        loading.visibility = if (state.initialLoading || state.simulating) View.VISIBLE else View.GONE
        content.visibility = if (state.hasInitialData) View.VISIBLE else View.INVISIBLE
        error.text = state.errorMessage.orEmpty()
        error.visibility = if (!state.hasInitialData && !state.errorMessage.isNullOrBlank()) View.VISIBLE else View.GONE
        retry.visibility = if (!state.hasInitialData && state.canRetry && !state.initialLoading) View.VISIBLE else View.GONE
        simulateButton.isEnabled = state.hasInitialData && !state.simulating
        consumptionCard.text = state.projectedCgiText
        energyCard.text = state.targetEnergyText
        savingsCard.text = state.savingsText
        positionsCard.text = state.positionsGainedText

        if (state.hasInitialData && populateInitialFields) {
            currentCgiInput.setText(String.format(Locale.US, "%.2f", state.currentCgi))
            chickensInput.setText(state.currentChickens.toString())
            populateInitialFields = false
        }
    }

    private fun submitSimulation() {
        clearErrors()
        val targetCgi = targetCgiInput.decimalValue()
        val waterPrice = waterPriceInput.decimalValue()
        val energyPrice = energyPriceInput.decimalValue()
        val chickens = chickensInput.text?.toString().orEmpty().filter(Char::isDigit).toIntOrNull()
        val targetEnergy = targetEnergyInput.decimalValue()
        var valid = true

        if (targetCgi == null || targetCgi <= 0.0) {
            targetCgiInput.error = "Informe um consumo alvo válido."
            valid = false
        }
        if (waterPrice == null || waterPrice < 0.0) {
            waterPriceInput.error = "Informe o preço da água."
            valid = false
        }
        if (energyPrice == null || energyPrice < 0.0) {
            energyPriceInput.error = "Informe o preço da energia."
            valid = false
        }
        if (chickens == null || chickens <= 0) {
            chickensInput.error = "Informe a quantidade de aves."
            valid = false
        }
        if (targetEnergy == null || targetEnergy < 0.0) {
            targetEnergyInput.error = "Informe o gasto alvo de energia."
            valid = false
        }
        if (!valid) return

        viewModel.simulate(
            targetCgi = targetCgi!!,
            waterPricePerM3 = waterPrice!!,
            energyPricePerKwh = energyPrice!!,
            chickens = chickens!!,
            targetKwhPerBird = targetEnergy!!
        )
    }

    private fun TextInputEditText.decimalValue(): Double? {
        if (this === waterPriceInput || this === energyPriceInput) {
            return BrazilianMoney.parse(text?.toString().orEmpty())?.toDouble()
        }
        val raw = text?.toString().orEmpty().trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
        if (raw.isBlank()) return null
        val normalized = if (raw.contains(',')) raw.replace(".", "").replace(',', '.') else raw
        return normalized.toDoubleOrNull()
    }

    private fun clearErrors() {
        targetCgiInput.error = null
        waterPriceInput.error = null
        energyPriceInput.error = null
        chickensInput.error = null
        targetEnergyInput.error = null
    }

    private fun openLogin() {
        AppSession.sessionManager.clear()
        SecureRefreshTokenStore(applicationContext).clear()
        FarmSessionStore(applicationContext).clear()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
