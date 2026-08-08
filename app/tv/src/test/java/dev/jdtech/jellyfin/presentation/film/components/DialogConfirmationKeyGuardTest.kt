package dev.jdtech.jellyfin.presentation.film.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogConfirmationKeyGuardTest {
    @Test
    fun `release from the long press that opened the dialog is consumed`() {
        val guard = DialogConfirmationKeyGuard()

        assertTrue(guard.shouldConsume(confirmKey = true, keyDown = true))
        assertTrue(guard.shouldConsume(confirmKey = true, keyDown = true))
        assertTrue(guard.shouldConsume(confirmKey = true, keyDown = false))
    }

    @Test
    fun `fresh confirmation press is delivered after opening release`() {
        val guard = DialogConfirmationKeyGuard()

        guard.shouldConsume(confirmKey = true, keyDown = false)

        assertFalse(guard.shouldConsume(confirmKey = true, keyDown = true))
        assertFalse(guard.shouldConsume(confirmKey = true, keyDown = false))
    }

    @Test
    fun `unrelated key release does not disarm the guard`() {
        val guard = DialogConfirmationKeyGuard()

        assertFalse(guard.shouldConsume(confirmKey = false, keyDown = false))
        assertTrue(guard.shouldConsume(confirmKey = true, keyDown = false))
    }
}
