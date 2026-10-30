package com.ouros.app.core

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object BrazilianMoney {
    private val locale = Locale.forLanguageTag("pt-BR")

    fun parse(raw: String): BigDecimal? {
        val cleaned = raw.filter {
            it.isDigit() || it == ',' || it == '.' || it == '-'
        }
        if (cleaned.isBlank()) return null

        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            cleaned.contains(',') -> cleaned.replace(',', '.')
            else -> cleaned
        }

        return normalized.toBigDecimalOrNull()
    }

    fun format(raw: String): String {
        return parse(raw)?.let(::format).orEmpty()
    }

    fun format(value: BigDecimal): String {
        return NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
        }.format(value.setScale(2, RoundingMode.HALF_UP))
    }
}

class BrazilianMoneyTextWatcher(
    private val editText: EditText,
    private val onValueChanged: (String) -> Unit
) : TextWatcher {
    private var updating = false

    override fun beforeTextChanged(
        text: CharSequence?,
        start: Int,
        count: Int,
        after: Int
    ) = Unit

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        before: Int,
        count: Int
    ) = Unit

    override fun afterTextChanged(editable: Editable?) {
        if (updating) return

        val digits = editable
            ?.toString()
            .orEmpty()
            .filter(Char::isDigit)

        val formatted = if (digits.isBlank()) {
            ""
        } else {
            BrazilianMoney.format(
                digits.toBigDecimal().movePointLeft(2)
            )
        }

        updating = true
        editText.setText(formatted)
        editText.setSelection(formatted.length)
        updating = false

        onValueChanged(formatted)
    }
}