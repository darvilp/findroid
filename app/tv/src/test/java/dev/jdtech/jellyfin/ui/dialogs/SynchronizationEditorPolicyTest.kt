package dev.jdtech.jellyfin.ui.dialogs

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizationEditorPolicyTest {
    @Test
    fun `place editing carries borrows and clamps at zero`() {
        var state = SynchronizationEditorState.from(999L)
        state = state.copy(selectedField = SynchronizationField.ONE_MILLISECOND).increment()
        assertEquals(1_000L, state.valueMs)
        state = state.decrement()
        assertEquals(999L, state.valueMs)

        state = SynchronizationEditorState.from(50L).copy(selectedField = SynchronizationField.HUNDRED_MILLISECONDS).decrement()
        assertEquals(0L, state.valueMs)
        assertEquals(
            "Synchronized",
            synchronizationValueLabel(state.valueMs, "Synchronized", "Earlier", "Later"),
        )
    }

    @Test
    fun `direction toggles sign and zero stays synchronized`() {
        assertEquals(-1_500L, SynchronizationEditorState.from(1_500L).copy(selectedField = SynchronizationField.DIRECTION).increment().valueMs)
        assertEquals(1_500L, SynchronizationEditorState.from(-1_500L).copy(selectedField = SynchronizationField.DIRECTION).decrement().valueMs)
        assertEquals(0L, SynchronizationEditorState.from(0L).copy(selectedField = SynchronizationField.DIRECTION).increment().valueMs)
    }

    @Test
    fun `exact entry accepts up to three decimals and rejects invalid or overflow`() {
        assertEquals(0L, exactSynchronizationValue(false, "0"))
        assertEquals(-125L, exactSynchronizationValue(true, "0.125"))
        assertEquals(1_500L, exactSynchronizationValue(false, "1.5"))
        assertEquals(12_345L, exactSynchronizationValue(false, "12.345"))
        assertNull(exactSynchronizationValue(false, "1.0001"))
        assertNull(exactSynchronizationValue(false, "1.0000"))
        assertNull(exactSynchronizationValue(false, "9223372036854776"))
    }

    @Test
    fun `modal consumes complete dpad ok and back gestures including opening release`() {
        val guard = SynchronizationKeyGuard(openingKeyCode = 23)
        assertTrue(guard.consume(keyCode = 23, isDown = false))
        assertTrue(guard.consume(keyCode = 21, isDown = true))
        assertTrue(guard.consume(keyCode = 21, isDown = false))
        assertTrue(guard.consume(keyCode = 4, isDown = true))
        assertTrue(guard.consume(keyCode = 4, isDown = false))
        assertFalse(guard.consume(keyCode = 85, isDown = true))
    }

    @Test
    fun `closing editor returns focus to its originating row`() {
        val policy = SynchronizationDialogPolicy(MpvSynchronizationKind.SUBTITLE)
        policy.openEditor(MpvSynchronizationKind.AUDIO)
        policy.closeEditor()
        assertEquals(MpvSynchronizationKind.AUDIO, policy.takeFocusReturn())
        assertNull(policy.takeFocusReturn())
    }

    @Test
    fun `back completion stays armed until release and completes once`() {
        var state = SynchronizationEditorBackState()

        var transition =
            state.onKey(keyCode = 4, isDown = true, repeatCount = 0)
        state = transition.state
        assertTrue(state.completionArmed)
        assertEquals(SynchronizationEditorAction.NONE, transition.action)

        transition = state.onKey(keyCode = 4, isDown = true, repeatCount = 1)
        state = transition.state
        assertTrue(state.completionArmed)
        assertEquals(SynchronizationEditorAction.NONE, transition.action)

        transition = state.onKey(keyCode = 4, isDown = false, repeatCount = 0)
        state = transition.state
        assertFalse(state.completionArmed)
        assertEquals(SynchronizationEditorAction.DONE, transition.action)

        transition = state.onKey(keyCode = 4, isDown = false, repeatCount = 0)
        assertFalse(transition.state.completionArmed)
        assertEquals(SynchronizationEditorAction.NONE, transition.action)
    }

    @Test
    fun `interrupted back gesture clears completion arm`() {
        val armed =
            SynchronizationEditorBackState()
                .onKey(keyCode = 4, isDown = true, repeatCount = 0)
                .state

        val interrupted = armed.onKey(keyCode = 21, isDown = true, repeatCount = 0)
        assertFalse(interrupted.state.completionArmed)
        assertEquals(SynchronizationEditorAction.NONE, interrupted.action)
        assertEquals(
            SynchronizationEditorAction.NONE,
            interrupted.state.onKey(keyCode = 4, isDown = false, repeatCount = 0).action,
        )
    }

    @Test
    fun `dismissing exact entry returns to flip editor without changing value`() {
        val state = SynchronizationEditorUiState(exactEntry = true, valueMs = -125L)

        assertEquals(
            SynchronizationEditorUiState(exactEntry = false, valueMs = -125L),
            state.dismissExactEntry(),
        )
    }

    @Test
    fun `disabled subtitle origin focuses enabled audio row`() {
        assertEquals(
            MpvSynchronizationKind.AUDIO,
            initialSynchronizationFocusTarget(
                origin = MpvSynchronizationKind.SUBTITLE,
                canEditSubtitle = false,
            ),
        )
        assertEquals(
            MpvSynchronizationKind.SUBTITLE,
            initialSynchronizationFocusTarget(
                origin = MpvSynchronizationKind.SUBTITLE,
                canEditSubtitle = true,
            ),
        )
    }


    @Test
    fun `long minimum displays earlier with a positive magnitude and valid digits`() {
        assertEquals(
            "Earlier 9223372036854775.808 s",
            synchronizationValueLabel(Long.MIN_VALUE, "Synchronized", "Earlier", "Later"),
        )
        assertEquals(
            SynchronizationMagnitudeParts("9223372036854775", "8", "0", "8"),
            synchronizationMagnitudeParts(Long.MIN_VALUE),
        )
    }

    @Test
    fun `unrepresentable direction and place edits keep the live value unchanged`() {
        val direction =
            SynchronizationEditorState.from(Long.MIN_VALUE)
                .copy(selectedField = SynchronizationField.DIRECTION)
                .increment()
        val place =
            SynchronizationEditorState.from(Long.MIN_VALUE)
                .copy(selectedField = SynchronizationField.ONE_MILLISECOND)
                .increment()

        assertEquals(Long.MIN_VALUE, direction.valueMs)
        assertEquals(Long.MIN_VALUE, place.valueMs)
        assertNull(
            synchronizationValueAfterEdit(
                SynchronizationEditorState.from(Long.MIN_VALUE)
                    .copy(selectedField = SynchronizationField.DIRECTION),
                increase = true,
            )
        )
    }

    @Test
    fun `exact entry supports long minimum only in the earlier direction`() {
        assertEquals(Long.MIN_VALUE, exactSynchronizationValue(true, "9223372036854775.808"))
        assertNull(exactSynchronizationValue(false, "9223372036854775.808"))
    }
}
