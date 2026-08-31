package dev.jdtech.jellyfin.settings.domain

import java.math.BigDecimal
import java.math.BigInteger

private val LONG_MIN_BIG_INTEGER = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX_BIG_INTEGER = BigInteger.valueOf(Long.MAX_VALUE)

fun BigInteger.toLongExact(): Long {
    if (this < LONG_MIN_BIG_INTEGER || this > LONG_MAX_BIG_INTEGER) {
        throw ArithmeticException("BigInteger out of Long range")
    }
    return toLong()
}

enum class MpvSynchronizationKind {
    AUDIO,
    SUBTITLE,
}

data class MpvSynchronizationDefaults(val audioMs: Long, val subtitleMs: Long) {
    fun value(kind: MpvSynchronizationKind): Long =
        when (kind) {
            MpvSynchronizationKind.AUDIO -> audioMs
            MpvSynchronizationKind.SUBTITLE -> subtitleMs
        }

    fun withValue(kind: MpvSynchronizationKind, valueMs: Long): MpvSynchronizationDefaults =
        when (kind) {
            MpvSynchronizationKind.AUDIO -> copy(audioMs = valueMs)
            MpvSynchronizationKind.SUBTITLE -> copy(subtitleMs = valueMs)
        }
}

object MpvSynchronizationValue {
    fun formatMpvSeconds(valueMs: Long): String {
        val negative = valueMs < 0
        val magnitude = if (valueMs == Long.MIN_VALUE) BigDecimal(valueMs).abs() else BigDecimal(kotlin.math.abs(valueMs))
        val seconds = magnitude.movePointLeft(3).setScale(3)
        return (if (negative) "-" else "") + seconds.toPlainString()
    }

    fun parseMagnitudeSeconds(value: String): Long? {
        if (!MAGNITUDE_PATTERN.matches(value)) return null
        return runCatching { BigDecimal(value).movePointRight(3).longValueExact() }.getOrNull()
    }

    fun parseSignedMpvSeconds(value: String): Long? {
        val normalized = value.trim()
        if (!SIGNED_SECONDS_PATTERN.matches(normalized)) return null
        return runCatching { BigDecimal(normalized).movePointRight(3).longValueExact() }.getOrNull()
    }

    private val MAGNITUDE_PATTERN = Regex("^(?:0|[1-9]\\d*)(?:\\.\\d{1,3})?$")
    fun parseDirectedMagnitudeSeconds(value: String, negative: Boolean): Long? {
        if (!MAGNITUDE_PATTERN.matches(value)) return null
        val milliseconds =
            runCatching { BigDecimal(value).movePointRight(3).toBigIntegerExact() }.getOrNull()
                ?: return null
        return runCatching {
                (if (negative && milliseconds != BigInteger.ZERO) milliseconds.negate()
                    else milliseconds)
                    .toLongExact()
            }
            .getOrNull()
    }

    private fun magnitudeMilliseconds(valueMs: Long): BigInteger =
        BigInteger.valueOf(valueMs).abs()

    fun formatMpvMagnitudeSeconds(valueMs: Long): String =
        BigDecimal(magnitudeMilliseconds(valueMs)).movePointLeft(3).setScale(3).toPlainString()

    private val SIGNED_SECONDS_PATTERN = Regex("^[+-]?(?:0|[1-9]\\d*)(?:\\.\\d+)?$")
}

class MpvSynchronizationState(audioBaselineMs: Long = 0L, subtitleBaselineMs: Long = 0L) {
    private val baselines =
        mutableMapOf(
            MpvSynchronizationKind.AUDIO to audioBaselineMs,
            MpvSynchronizationKind.SUBTITLE to subtitleBaselineMs,
        )
    private val temporary =
        mutableMapOf(
            MpvSynchronizationKind.AUDIO to BigInteger.ZERO,
            MpvSynchronizationKind.SUBTITLE to BigInteger.ZERO,
        )

    fun baseline(kind: MpvSynchronizationKind): Long = baselines.getValue(kind)

    internal fun temporary(kind: MpvSynchronizationKind): BigInteger = temporary.getValue(kind)

    fun effective(kind: MpvSynchronizationKind): Long =
        BigInteger.valueOf(baseline(kind)).add(temporary(kind)).toLongExact()

    fun setTemporary(kind: MpvSynchronizationKind, valueMs: Long) {
        val delta = BigInteger.valueOf(valueMs)
        BigInteger.valueOf(baseline(kind)).add(delta).toLongExact()
        temporary[kind] = delta
    }

    fun setEffective(kind: MpvSynchronizationKind, valueMs: Long) {
        temporary[kind] =
            BigInteger.valueOf(valueMs).subtract(BigInteger.valueOf(baseline(kind)))
    }

    fun reset(kind: MpvSynchronizationKind) {
        temporary[kind] = BigInteger.ZERO
    }

    fun promote(kind: MpvSynchronizationKind) {
        baselines[kind] = effective(kind)
        temporary[kind] = BigInteger.ZERO
    }

    fun replaceBaselines(defaults: MpvSynchronizationDefaults) {
        baselines[MpvSynchronizationKind.AUDIO] = defaults.audioMs
        baselines[MpvSynchronizationKind.SUBTITLE] = defaults.subtitleMs
        temporary.keys.forEach { temporary[it] = BigInteger.ZERO }
    }
}

fun adjustSynchronizationMagnitude(magnitudeMs: Long, deltaMs: Long): Long {
    require(magnitudeMs >= 0L)
    if (deltaMs < 0L && magnitudeMs < -deltaMs) return 0L
    return Math.addExact(magnitudeMs, deltaMs).coerceAtLeast(0L)
}
