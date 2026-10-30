package com.ouros.app.feature.main.record

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.ouros.app.R
import com.ouros.app.core.util.applyBottomSystemBarMargin
import com.ouros.app.core.util.applyImeFixedTranslation
import com.ouros.app.core.util.dp
import com.ouros.app.data.record.RecordDraft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.ouros.app.core.BrazilianMoney
import com.ouros.app.core.BrazilianMoneyTextWatcher

abstract class RecordStepFragment(@LayoutRes layout: Int) : Fragment(layout) {
    protected val host: RecordWizardActivity get() = requireActivity() as RecordWizardActivity
    protected val wizard: RecordWizardViewModel get() = host.wizardViewModel

    protected fun bindNavigation(root: View) {
        root.findViewById<View>(R.id.recordBack)?.setOnClickListener { host.previousStep() }
        val returnHome = root.findViewById<View>(R.id.recordReturnHome)
        returnHome?.setOnClickListener { host.finishKeepingDraft() }
        configureKeyboardBehavior(root, returnHome)
    }

    private fun configureKeyboardBehavior(root: View, returnHome: View?) {
        val formContainer = root.findViewById<View>(R.id.recordFormContainer)
            ?: root.findViewById<View>(R.id.recordContentContainer)
        val headerInfo = root.findViewById<View>(R.id.recordHeaderInfo)
            ?: root.findViewById<View>(R.id.linearLayout2)
        val primaryButton = root.findViewById<View>(R.id.recordPrimary)

        formContainer?.applyImeFixedTranslation(
            bottomAnchorView = primaryButton,
            extraMarginPx = 16.dp,
            viewsToFadeOut = listOfNotNull(returnHome, headerInfo)
        )
        returnHome?.applyBottomSystemBarMargin()

        root.post {
            val currentFocus = root.findFocus()
            if (currentFocus == null || currentFocus === root) {
                root.isFocusableInTouchMode = true
                root.requestFocus()
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.ime())
                }
            }
        }
    }

    protected fun watch(field: EditText, change: (RecordDraft, String) -> RecordDraft) {
        field.doAfterTextChanged { value ->
            wizard.update { change(it, value?.toString()?.trim().orEmpty()) }
        }
    }

    protected fun openDatePicker(field: EditText, onSelected: (String) -> Unit) {
        val selected = runCatching {
            LocalDate.parse(field.text.toString(), DISPLAY_DATE)
        }.getOrDefault(LocalDate.now())

        DatePickerDialog(requireContext(), { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            field.setText(date.format(DISPLAY_DATE))
            onSelected(date.toString())
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    protected fun invalid(field: EditText, message: String): Boolean {
        field.error = message
        field.requestFocus()
        return false
    }

    protected fun EditText.decimalOrNull(): Double? {
        return text.toString().trim().replace(',', '.').toDoubleOrNull()
    }

    protected fun EditText.moneyOrNull(): Double? {
        return BrazilianMoney.parse(text.toString())?.toDouble()
    }

    protected fun EditText.nonNegativeInt(): Int? {
        return text.toString().toIntOrNull()?.takeIf { it >= 0 }
    }

    protected fun EditText.hasValidDate(): Boolean {
        return runCatching {
            LocalDate.parse(text.toString(), DISPLAY_DATE)
        }.getOrNull()?.let {
            !it.isAfter(LocalDate.now())
        } == true
    }

    protected fun String.toDisplayDate(): String {
        return if (isBlank()) {
            ""
        } else {
            runCatching {
                LocalDate.parse(this).format(DISPLAY_DATE)
            }.getOrDefault("")
        }
    }

    private companion object {
        val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}

class RecordLotFragment : RecordStepFragment(R.layout.fragment_record_lot) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindNavigation(view)

        val lotId = view.findViewById<EditText>(R.id.inputLotId)
        val lotCost = view.findViewById<EditText>(R.id.inputLotCost)
        val draft = wizard.currentDraft()

        lotId.setText(draft.lotId)
        lotCost.setText(BrazilianMoney.format(draft.lotCost))

        lotCost.addTextChangedListener(
            BrazilianMoneyTextWatcher(lotCost) { formatted ->
                wizard.update { current ->
                    current.copy(lotCost = formatted)
                }
            }
        )

        watch(lotCost) { current, value ->
            current.copy(lotCost = value)
        }

        view.findViewById<Button>(R.id.recordPrimary).setOnClickListener {
            when {
                lotId.text.toString().toLongOrNull()?.takeIf { it > 0 } == null ->
                    invalid(lotId, "Informe um ID de lote válido.")

                lotCost.moneyOrNull()?.takeIf { it >= 0.0 } == null ->
                    invalid(lotCost, "Informe um custo válido.")

                else -> host.nextStep(2)
            }
        }
    }
}

class RecordWaterFragment : RecordStepFragment(R.layout.fragment_record_water) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindNavigation(view)

        val date = view.findViewById<EditText>(R.id.inputWaterDate)
        val start = view.findViewById<EditText>(R.id.inputHydrometerStart)
        val end = view.findViewById<EditText>(R.id.inputHydrometerEnd)
        val draft = wizard.currentDraft()

        date.setText(draft.waterDate.toDisplayDate())
        start.setText(draft.hydrometerStart)
        end.setText(draft.hydrometerEnd)

        date.setOnClickListener {
            openDatePicker(date) { iso ->
                wizard.update { it.copy(waterDate = iso) }
            }
        }

        watch(start) { current, value ->
            current.copy(hydrometerStart = value)
        }

        watch(end) { current, value ->
            current.copy(hydrometerEnd = value)
        }

        view.findViewById<Button>(R.id.recordPrimary).setOnClickListener {
            val initial = start.decimalOrNull()
            val final = end.decimalOrNull()

            when {
                !date.hasValidDate() ->
                    invalid(date, "Informe uma data válida.")

                initial == null || initial < 0.0 ->
                    invalid(start, "Informe a leitura inicial.")

                final == null || final < initial ->
                    invalid(end, "A leitura final deve ser maior ou igual à inicial.")

                else -> host.nextStep(3)
            }
        }
    }
}

class RecordEnergyFragment : RecordStepFragment(R.layout.fragment_record_energy) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindNavigation(view)

        val date = view.findViewById<EditText>(R.id.inputEnergyDate)
        val consumption = view.findViewById<EditText>(R.id.inputEnergyKwh)
        var draft = wizard.currentDraft()

        if (draft.energyDate.isBlank() && draft.waterDate.isNotBlank()) {
            wizard.update { it.copy(energyDate = it.waterDate) }
            draft = wizard.currentDraft()
        }

        date.setText(draft.energyDate.toDisplayDate())
        consumption.setText(draft.energyKwh)

        date.setOnClickListener {
            openDatePicker(date) { iso ->
                wizard.update { it.copy(energyDate = iso) }
            }
        }

        watch(consumption) { current, value ->
            current.copy(energyKwh = value)
        }

        view.findViewById<Button>(R.id.recordPrimary).setOnClickListener {
            when {
                !date.hasValidDate() ->
                    invalid(date, "Informe uma data válida.")

                consumption.decimalOrNull()?.takeIf { it > 0.0 } == null ->
                    invalid(consumption, "Informe um consumo maior que zero.")

                else -> host.nextStep(4)
            }
        }
    }
}

class RecordChickensFragment : RecordStepFragment(R.layout.fragment_record_chickens) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindNavigation(view)

        val entered = view.findViewById<EditText>(R.id.inputChickensEntered)
        val left = view.findViewById<EditText>(R.id.inputChickensLeft)
        val current = view.findViewById<EditText>(R.id.inputChickensCurrent)
        val draft = wizard.currentDraft()

        entered.setText(draft.chickensEntered)
        left.setText(draft.chickensLeft)
        current.setText(draft.chickensCurrent)

        watch(entered) { old, value ->
            old.copy(chickensEntered = value)
        }

        watch(left) { old, value ->
            old.copy(chickensLeft = value)
        }

        watch(current) { old, value ->
            old.copy(chickensCurrent = value)
        }

        view.findViewById<Button>(R.id.recordPrimary).setOnClickListener {
            when {
                entered.nonNegativeInt() == null ->
                    invalid(entered, "Informe a quantidade que entrou.")

                left.nonNegativeInt() == null ->
                    invalid(left, "Informe a quantidade que saiu.")

                current.nonNegativeInt() == null ->
                    invalid(current, "Informe a quantidade atual.")

                else -> host.confirmRegistration()
            }
        }
    }
}
