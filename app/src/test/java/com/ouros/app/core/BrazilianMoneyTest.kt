package com.ouros.app.core

import android.widget.EditText
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrazilianMoneyTest {

    @Test
    fun parse_acceptsBrazilianAndInternationalSeparators() {
        assertEquals(BigDecimal("1234.56"), BrazilianMoney.parse("R$ 1.234,56"))
        assertEquals(BigDecimal("1234.56"), BrazilianMoney.parse("$ 1,234.56"))
        assertEquals(BigDecimal("1234.56"), BrazilianMoney.parse("1234,56"))
        assertEquals(BigDecimal("1234.56"), BrazilianMoney.parse("1234.56"))
    }

    @Test
    fun parse_returnsNullForBlankOrMalformedNumbers() {
        assertNull(BrazilianMoney.parse("R$ --"))
        assertNull(BrazilianMoney.parse("1,2,3"))
        assertNull(BrazilianMoney.parse("."))
    }

    @Test
    fun format_parsesInputAndReturnsEmptyForInvalidInput() {
        assertEquals("1.234,57", BrazilianMoney.format("R$ 1.234,565"))
        assertEquals("", BrazilianMoney.format("sem valor"))
    }

    @Test
    fun format_bigDecimalUsesBrazilianGroupingAndHalfUpRounding() {
        assertEquals("1.000.000,00", BrazilianMoney.format(BigDecimal("999999.995")))
        assertEquals("-1.234,50", BrazilianMoney.format(BigDecimal("-1234.5")))
    }

    @Test
    fun textWatcherFormatsDigitsAndReportsFormattedValue() {
        val editText = EditText(RuntimeEnvironment.getApplication())
        val changes = mutableListOf<String>()
        editText.addTextChangedListener(BrazilianMoneyTextWatcher(editText, changes::add))

        editText.setText("123456")

        assertEquals("1.234,56", editText.text.toString())
        assertEquals(listOf("1.234,56"), changes)
    }

    @Test
    fun textWatcherClearsEmptyInputAndReportsTheChange() {
        val editText = EditText(RuntimeEnvironment.getApplication())
        val changes = mutableListOf<String>()
        editText.addTextChangedListener(BrazilianMoneyTextWatcher(editText, changes::add))

        editText.setText("123")
        editText.setText("")

        assertEquals("", editText.text.toString())
        assertEquals(listOf("1,23", ""), changes)
    }
}
