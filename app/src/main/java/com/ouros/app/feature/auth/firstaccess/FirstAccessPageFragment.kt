package com.ouros.app.feature.auth.firstaccess

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ouros.app.R
import com.ouros.app.core.util.applyBottomSystemBarMargin
import com.ouros.app.core.util.applyImeFixedTranslation

class FirstAccessPageFragment :
    Fragment() {

    private lateinit var step:
            FirstAccessStep

    private var codeFields:
            List<EditText> = emptyList()

    private val viewModel:
            FirstAccessViewModel by lazy {

        ViewModelProvider(
            requireActivity()
        )[FirstAccessViewModel::class.java]
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val stepName =
            requireArguments()
                .getString(ARG_STEP)
                ?: error(
                    "A etapa do primeiro acesso não foi informada."
                )

        step = FirstAccessStep.valueOf(
            stepName
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            step.layoutRes,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        when (step) {
            FirstAccessStep.WELCOME -> {
                bindWelcome(view)
            }

            FirstAccessStep.CREDENTIALS -> {
                bindCredentials(view)
            }

            FirstAccessStep.CODE -> {
                bindCode(view)
            }

            FirstAccessStep.PERSONAL_DATA -> {
                bindPersonalData(view)
            }

            FirstAccessStep.NEW_PASSWORD -> {
                bindNewPassword(view)
            }
        }

        renderLoading(
            viewModel.isLoading()
        )
    }

    private fun bindWelcome(view: View) {
        view.findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackClicked()
        }

        view.findViewById<View>(
            R.id.btnStart
        ).setOnClickListener {
            viewModel.onStartClicked()
        }
    }

    private fun bindCredentials(
        view: View
    ) {
        val inputEmail =
            view.findViewById<TextInputLayout>(
                R.id.inputEmail
            )

        val inputPassword =
            view.findViewById<TextInputLayout>(
                R.id.inputPassword
            )

        val editEmail =
            view.findViewById<TextInputEditText>(
                R.id.editEmail
            )

        val editPassword =
            view.findViewById<TextInputEditText>(
                R.id.editPassword
            )

        val btnContinue =
            view.findViewById<Button>(
                R.id.btnContinue
            )

        editEmail.setText(
            viewModel.currentEmail()
        )

        editEmail.doAfterTextChanged {
            viewModel.onEmailChanged(
                it.toString()
            )
        }

        editPassword.doAfterTextChanged {
            viewModel
                .onInitialPasswordChanged(
                    it.toString()
                )
        }

        view.findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackClicked()
        }

        btnContinue.setOnClickListener {
            viewModel
                .onCredentialsContinueClicked()
        }

        inputEmail.error = null
        inputPassword.error = null

        val btnContinueCred = view.findViewById<View>(R.id.btnContinue)
        val headerInfoCred = view.findViewById<View>(R.id.linearLayout2)
        val formContainerCred = view.findViewById<View>(R.id.firstAccessCredentialsFormContainer)
            ?: view.findViewById<View>(R.id.firstAccessCredentialsContentContainer)
        formContainerCred?.applyImeFixedTranslation(
            bottomAnchorView = btnContinueCred,
            viewsToFadeOut = listOfNotNull(headerInfoCred)
        )

    }

    private fun bindCode(view: View) {
        codeFields = listOf(
            view.findViewById(
                R.id.editCodeDigitOne
            ),
            view.findViewById(
                R.id.editCodeDigitTwo
            ),
            view.findViewById(
                R.id.editCodeDigitThree
            ),
            view.findViewById(
                R.id.editCodeDigitFour
            ),
            view.findViewById(
                R.id.editCodeDigitFive
            ),
            view.findViewById(
                R.id.editCodeDigitSix
            )
        )

        restoreVerificationCode()
        configureCodeFields()

        val btnResend = view.findViewById<View>(R.id.btnResendCode)
        val btnContinueCode = view.findViewById<View>(R.id.btnContinue)
        view.findViewById<View>(R.id.firstAccessCodeContentContainer)?.applyImeFixedTranslation(
            bottomAnchorView = btnContinueCode,
            viewsToFadeOut = listOfNotNull(btnResend)
        )
        btnResend?.applyBottomSystemBarMargin()

        renderCodeChallenge(
            maskedEmail =
                viewModel.currentMaskedEmail(),
            expiresInSeconds = 0L
        )

        view.findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackClicked()
        }

        view.findViewById<View>(
            R.id.btnContinue
        ).setOnClickListener {
            viewModel.onCodeContinueClicked()
        }

        view.findViewById<View>(
            R.id.btnResendCode
        ).setOnClickListener {
            viewModel.onResendCodeClicked()
        }

        codeFields
            .firstOrNull {
                it.text.isNullOrEmpty()
            }
            ?.post {
                codeFields
                    .firstOrNull {
                        it.text.isNullOrEmpty()
                    }
                    ?.requestFocus()
            }
    }

    private fun restoreVerificationCode() {
        val savedCode =
            viewModel
                .currentVerificationCode()

        codeFields.forEachIndexed {
                index,
                field ->

            val digit =
                savedCode.getOrNull(index)

            field.setText(
                digit?.toString().orEmpty()
            )
        }
    }

    private fun configureCodeFields() {
        codeFields.forEachIndexed {
                index,
                field ->

            field.doAfterTextChanged {
                val value =
                    field.text
                        ?.toString()
                        .orEmpty()
                        .filter(
                            Char::isDigit
                        )

                if (
                    value !=
                    field.text
                        ?.toString()
                        .orEmpty()
                ) {
                    field.setText(value)
                    field.setSelection(
                        value.length
                    )

                    return@doAfterTextChanged
                }

                viewModel
                    .onVerificationCodeChanged(
                        currentCodeFromFields()
                    )

                if (
                    value.length == 1 &&
                    index <
                    codeFields.lastIndex
                ) {
                    codeFields[index + 1]
                        .requestFocus()
                }
            }

            field.setOnKeyListener {
                    _,
                    keyCode,
                    event ->

                val deleting =
                    keyCode ==
                            KeyEvent.KEYCODE_DEL &&
                            event.action ==
                            KeyEvent.ACTION_DOWN

                if (
                    deleting &&
                    field.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    val previous =
                        codeFields[index - 1]

                    previous.requestFocus()
                    previous.text?.clear()

                    true
                } else {
                    false
                }
            }
        }
    }

    private fun currentCodeFromFields():
            String {

        return codeFields.joinToString(
            separator = ""
        ) {
            it.text
                ?.toString()
                .orEmpty()
        }
    }

    private fun bindPersonalData(
        view: View
    ) {
        val inputName =
            view.findViewById<TextInputLayout>(
                R.id.inputName
            )

        val inputCpf =
            view.findViewById<TextInputLayout>(
                R.id.inputCpf
            )

        val inputTelephone =
            view.findViewById<TextInputLayout>(
                R.id.inputTelephone
            )

        val editName =
            view.findViewById<TextInputEditText>(
                R.id.editName
            )

        val editCpf =
            view.findViewById<TextInputEditText>(
                R.id.editCpf
            )

        val editTelephone =
            view.findViewById<TextInputEditText>(
                R.id.editTelephone
            )

        val btnContinue =
            view.findViewById<Button>(
                R.id.btnContinue
            )

        editName.setText(
            viewModel.currentName()
        )

        editCpf.setText(
            formatCpf(
                viewModel.currentDocument()
            )
        )

        editTelephone.setText(
            formatTelephone(
                viewModel.currentTelephone()
            )
        )

        applyCpfMask(editCpf)
        applyTelephoneMask(editTelephone)

        editName.doAfterTextChanged {
            viewModel.onNameChanged(
                it.toString()
            )
        }

        editCpf.doAfterTextChanged {
            viewModel.onCpfChanged(
                it.toString()
            )
        }

        editTelephone.doAfterTextChanged {
            viewModel.onTelephoneChanged(
                it.toString()
            )
        }

        view.findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackClicked()
        }

        btnContinue.setOnClickListener {
            viewModel.onPersonalDataContinueClicked()
        }

        inputName.error = null
        inputCpf.error = null
        inputTelephone.error = null

        val btnContinuePersonal = view.findViewById<View>(R.id.btnContinue)
        val headerInfoPersonal = view.findViewById<View>(R.id.linearLayout2)
        val formContainerPersonal = view.findViewById<View>(R.id.firstAccessPersonalFormContainer)
            ?: view.findViewById<View>(R.id.firstAccessPersonalContentContainer)
        formContainerPersonal?.applyImeFixedTranslation(
            bottomAnchorView = btnContinuePersonal,
            viewsToFadeOut = listOfNotNull(headerInfoPersonal)
        )

    }

    private fun bindNewPassword(
        view: View
    ) {
        val inputNewPassword =
            view.findViewById<TextInputLayout>(
                R.id.inputNewPassword
            )

        val editNewPassword =
            view.findViewById<TextInputEditText>(
                R.id.editNewPassword
            )

        val btnFinish =
            view.findViewById<Button>(
                R.id.btnFinish
            )

        editNewPassword
            .doAfterTextChanged {
                viewModel
                    .onNewPasswordChanged(
                        it.toString()
                    )
            }

        view.findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackClicked()
        }

        btnFinish.setOnClickListener {
            viewModel.onFinishClicked()
        }

        inputNewPassword.error = null

        val imgMidas = view.findViewById<View>(R.id.imageView3)
        val textMidas = view.findViewById<View>(R.id.textView22)
        view.findViewById<View>(R.id.firstAccessPasswordContentContainer)?.applyImeFixedTranslation(
            bottomAnchorView = btnFinish,
            viewsToFadeOut = listOfNotNull(imgMidas, textMidas)
        )

    }

    fun renderCredentialsErrors(
        emailMessage: String?,
        passwordMessage: String?
    ) {
        if (
            step !=
            FirstAccessStep.CREDENTIALS
        ) {
            return
        }

        view?.findViewById<TextInputLayout>(
            R.id.inputEmail
        )?.error = emailMessage

        view?.findViewById<TextInputLayout>(
            R.id.inputPassword
        )?.error = passwordMessage
    }

    fun renderCodeError(
        message: String?
    ) {
        if (
            step != FirstAccessStep.CODE
        ) {
            return
        }

        val errorView =
            view?.findViewById<TextView>(
                R.id.textCodeError
            ) ?: return

        errorView.text = message

        errorView.isVisible =
            !message.isNullOrBlank()
    }

    fun renderCodeChallenge(
        maskedEmail: String,
        expiresInSeconds: Long
    ) {
        if (
            step != FirstAccessStep.CODE ||
            maskedEmail.isBlank()
        ) {
            return
        }

        val description =
            if (expiresInSeconds > 0L) {
                "Enviamos um código temporário para $maskedEmail. Ele expira em ${expiresInSeconds / 60} minuto(s)."
            } else {
                "Enviamos um código temporário para $maskedEmail."
            }

        view?.findViewById<TextView>(
            R.id.textCodeDescription
        )?.text = description
    }

    fun renderPersonalDataErrors(
        nameMessage: String?,
        cpfMessage: String?,
        telephoneMessage: String?
    ) {
        if (
            step !=
            FirstAccessStep.PERSONAL_DATA
        ) {
            return
        }

        view?.findViewById<TextInputLayout>(
            R.id.inputName
        )?.error = nameMessage

        view?.findViewById<TextInputLayout>(
            R.id.inputCpf
        )?.error = cpfMessage

        view?.findViewById<TextInputLayout>(
            R.id.inputTelephone
        )?.error = telephoneMessage
    }

    fun renderNewPasswordError(
        message: String?
    ) {
        if (
            step !=
            FirstAccessStep.NEW_PASSWORD
        ) {
            return
        }

        view?.findViewById<TextInputLayout>(
            R.id.inputNewPassword
        )?.error = message
    }

    fun renderLoading(
        isLoading: Boolean
    ) {
        val root = view ?: return
        val enabled = !isLoading

        when (step) {
            FirstAccessStep.WELCOME -> {
                root.findViewById<View>(
                    R.id.btnStart
                ).isEnabled = enabled
            }

            FirstAccessStep.CREDENTIALS -> {
                root.findViewById<View>(
                    R.id.editEmail
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.editPassword
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.btnContinue
                ).isEnabled = enabled
            }

            FirstAccessStep.CODE -> {
                codeFields.forEach {
                    it.isEnabled = enabled
                }

                root.findViewById<View>(
                    R.id.btnContinue
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.btnResendCode
                ).isEnabled = enabled
            }

            FirstAccessStep.PERSONAL_DATA -> {
                root.findViewById<View>(
                    R.id.editName
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.editCpf
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.editTelephone
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.btnContinue
                ).isEnabled = enabled
            }

            FirstAccessStep.NEW_PASSWORD -> {
                root.findViewById<View>(
                    R.id.editNewPassword
                ).isEnabled = enabled

                root.findViewById<View>(
                    R.id.btnFinish
                ).isEnabled = enabled
            }
        }
    }

    override fun onDestroyView() {
        codeFields = emptyList()

        super.onDestroyView()
    }

    companion object {

        private const val ARG_STEP =
            "first_access_step"

        fun newInstance(
            step: FirstAccessStep
        ): FirstAccessPageFragment {

            return FirstAccessPageFragment()
                .apply {
                    arguments = Bundle().apply {
                        putString(
                            ARG_STEP,
                            step.name
                        )
                    }
                }
        }
    }

    private fun applyCpfMask(
        editText: EditText
    ) {
        var isFormatting = false

        editText.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(
                    editable: Editable?
                ) {
                    if (isFormatting) {
                        return
                    }

                    val digits =
                        editable
                            ?.toString()
                            .orEmpty()
                            .filter(Char::isDigit)
                            .take(11)

                    val formatted =
                        formatCpf(digits)

                    if (
                        editable?.toString() ==
                        formatted
                    ) {
                        return
                    }

                    isFormatting = true

                    editText.setText(formatted)
                    editText.setSelection(
                        formatted.length
                    )

                    isFormatting = false
                }
            }
        )
    }

    private fun applyTelephoneMask(
        editText: EditText
    ) {
        var isFormatting = false

        editText.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(
                    editable: Editable?
                ) {
                    if (isFormatting) {
                        return
                    }

                    val digits =
                        editable
                            ?.toString()
                            .orEmpty()
                            .filter(Char::isDigit)
                            .take(11)

                    val formatted =
                        formatTelephone(digits)

                    if (
                        editable?.toString() ==
                        formatted
                    ) {
                        return
                    }

                    isFormatting = true

                    editText.setText(formatted)
                    editText.setSelection(
                        formatted.length
                    )

                    isFormatting = false
                }
            }
        )
    }

    private fun formatCpf(
        value: String
    ): String {
        val digits =
            value
                .filter(Char::isDigit)
                .take(11)

        return buildString {
            digits.forEachIndexed { index, digit ->

                if (index == 3 || index == 6) {
                    append(".")
                }

                if (index == 9) {
                    append("-")
                }

                append(digit)
            }
        }
    }


    private fun formatTelephone(
        value: String
    ): String {
        val digits =
            value
                .filter(Char::isDigit)
                .take(11)

        return buildString {
            digits.forEachIndexed { index, digit ->

                if (index == 0) {
                    append("(")
                }

                if (index == 2) {
                    append(") ")
                }

                if (
                    digits.length > 10 &&
                    index == 7
                ) {
                    append("-")
                }

                if (
                    digits.length <= 10 &&
                    index == 6
                ) {
                    append("-")
                }

                append(digit)
            }
        }
    }
}
