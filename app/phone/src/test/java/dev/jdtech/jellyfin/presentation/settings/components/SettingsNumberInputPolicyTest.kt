package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNumberInputPolicyTest {
    @Test
    fun signedInputAcceptsOneLeadingMinus() {
        assertTrue(isValidNumberInput("-", allowNegative = true))
        assertTrue(isValidNumberInput("-125", allowNegative = true))
        assertFalse(isValidNumberInput("12-5", allowNegative = true))
    }

    @Test
    fun existingNumberInputRemainsNonNegative() {
        assertTrue(isValidNumberInput("125", allowNegative = false))
        assertFalse(isValidNumberInput("-125", allowNegative = false))
    }

    @Test
    fun signedInputUsesAMinusCapableKeyboard() {
        assertEquals(KeyboardType.Text, numberInputKeyboardType(allowNegative = true))
        assertEquals(KeyboardType.Number, numberInputKeyboardType(allowNegative = false))
    }
}
