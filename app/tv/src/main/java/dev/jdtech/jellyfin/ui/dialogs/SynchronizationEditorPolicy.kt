package dev.jdtech.jellyfin.ui.dialogs

import android.view.KeyEvent
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationValue
import dev.jdtech.jellyfin.settings.domain.toLongExact
import java.math.BigInteger

internal enum class SynchronizationField(val placeMs: Long?) {
    DIRECTION(null),
    SECONDS(1_000L),
    HUNDRED_MILLISECONDS(100L),
    TEN_MILLISECONDS(10L),
    ONE_MILLISECOND(1L),
}

internal data class SynchronizationEditorState(
    val valueMs: Long,
    val selectedField: SynchronizationField = SynchronizationField.DIRECTION,
) {
    private fun edit(increase: Boolean): SynchronizationEditorState {
        if (selectedField == SynchronizationField.DIRECTION) {
            return if (valueMs == 0L) this
            else copy(valueMs = runCatching { Math.negateExact(valueMs) }.getOrElse { valueMs })
        }
        val magnitude = BigInteger.valueOf(valueMs).abs()
        val place = checkNotNull(selectedField.placeMs)
        val edited =
            (magnitude + BigInteger.valueOf(if (increase) place else -place))
                .coerceAtLeast(BigInteger.ZERO)
        val signed = if (valueMs < 0L) edited.negate() else edited
        return copy(valueMs = runCatching { signed.toLongExact() }.getOrElse { valueMs })
    }

    fun increment(): SynchronizationEditorState = edit(true)

    fun decrement(): SynchronizationEditorState = edit(false)

    fun move(delta: Int): SynchronizationEditorState =
        copy(selectedField = SynchronizationField.entries[(selectedField.ordinal + delta).coerceIn(0, SynchronizationField.entries.lastIndex)])

    companion object {
        fun from(valueMs: Long) = SynchronizationEditorState(valueMs)
    }
}

internal fun exactSynchronizationValue(earlier: Boolean, magnitude: String): Long? =
    MpvSynchronizationValue.parseDirectedMagnitudeSeconds(magnitude, negative = earlier)

internal fun synchronizationValueLabel(
    valueMs: Long,
    synchronizedLabel: String,
    earlierLabel: String,
    laterLabel: String,
): String =
    if (valueMs == 0L) synchronizedLabel
    else
        "${if (valueMs < 0L) earlierLabel else laterLabel} ${MpvSynchronizationValue.formatMpvMagnitudeSeconds(valueMs)} s"

internal data class SynchronizationMagnitudeParts(
    val seconds: String,
    val hundreds: String,
    val tens: String,
    val ones: String,
)

internal fun synchronizationMagnitudeParts(valueMs: Long): SynchronizationMagnitudeParts {
    val (seconds, fraction) =
        MpvSynchronizationValue.formatMpvMagnitudeSeconds(valueMs).split('.', limit = 2)
    return SynchronizationMagnitudeParts(
        seconds = seconds,
        hundreds = fraction[0].toString(),
        tens = fraction[1].toString(),
        ones = fraction[2].toString(),
    )
}

internal fun synchronizationValueAfterEdit(
    state: SynchronizationEditorState,
    increase: Boolean,
): SynchronizationEditorState? {
    val edited = if (increase) state.increment() else state.decrement()
    return edited.takeIf { it.valueMs != state.valueMs }
}

internal enum class SynchronizationEditorAction {
    NONE,
    DONE,
}

internal data class SynchronizationEditorBackTransition(
    val state: SynchronizationEditorBackState,
    val action: SynchronizationEditorAction,
)

internal data class SynchronizationEditorBackState(val completionArmed: Boolean = false) {
    fun onKey(
        keyCode: Int,
        isDown: Boolean,
        repeatCount: Int,
    ): SynchronizationEditorBackTransition {
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            val nextState = if (isDown && completionArmed) copy(completionArmed = false) else this
            return SynchronizationEditorBackTransition(nextState, SynchronizationEditorAction.NONE)
        }
        if (isDown) {
            val nextState = if (repeatCount == 0) copy(completionArmed = true) else this
            return SynchronizationEditorBackTransition(nextState, SynchronizationEditorAction.NONE)
        }
        return if (completionArmed) {
            SynchronizationEditorBackTransition(
                copy(completionArmed = false),
                SynchronizationEditorAction.DONE,
            )
        } else {
            SynchronizationEditorBackTransition(this, SynchronizationEditorAction.NONE)
        }
    }
}

internal fun synchronizationEditorAction(
    keyCode: Int,
    isDown: Boolean,
    repeatCount: Int,
): SynchronizationEditorAction =
    if (keyCode == KeyEvent.KEYCODE_BACK && isDown && repeatCount == 0) {
        SynchronizationEditorAction.DONE
    } else {
        SynchronizationEditorAction.NONE
    }

internal data class SynchronizationEditorUiState(val exactEntry: Boolean, val valueMs: Long) {
    fun dismissExactEntry(): SynchronizationEditorUiState = copy(exactEntry = false)
}

internal fun initialSynchronizationFocusTarget(
    origin: MpvSynchronizationKind,
    canEditSubtitle: Boolean,
): MpvSynchronizationKind =
    if (origin == MpvSynchronizationKind.SUBTITLE && !canEditSubtitle) {
        MpvSynchronizationKind.AUDIO
    } else {
        origin
    }

internal class SynchronizationKeyGuard(private val openingKeyCode: Int? = KeyEvent.KEYCODE_DPAD_CENTER) {
    private var openingReleasePending = openingKeyCode != null

    fun consume(keyCode: Int, isDown: Boolean): Boolean {
        if (!isDown && openingReleasePending && keyCode == openingKeyCode) {
            openingReleasePending = false
            return true
        }
        return keyCode in OWNED_KEYS
    }

    companion object {
        private val OWNED_KEYS =
            setOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK,
            )
    }
}

internal class SynchronizationDialogPolicy(initialKind: MpvSynchronizationKind) {
    var initialFocusKind: MpvSynchronizationKind = initialKind
        private set
    private var focusReturn: MpvSynchronizationKind? = null
    private var editingKind: MpvSynchronizationKind? = null

    fun openEditor(kind: MpvSynchronizationKind) {
        editingKind = kind
    }

    fun closeEditor() {
        focusReturn = editingKind
        editingKind = null
    }

    fun takeFocusReturn(): MpvSynchronizationKind? = focusReturn.also { focusReturn = null }
}

internal fun trackDialogShowsSynchronization(trackType: Int, synchronizationAvailable: Boolean): Boolean =
    synchronizationAvailable &&
        (trackType == androidx.media3.common.C.TRACK_TYPE_AUDIO ||
            trackType == androidx.media3.common.C.TRACK_TYPE_TEXT)
