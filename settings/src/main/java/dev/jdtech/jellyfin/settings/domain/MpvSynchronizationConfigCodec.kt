package dev.jdtech.jellyfin.settings.domain

object MpvSynchronizationConfigCodec {
    const val BEGIN_MARKER = "# BEGIN FINDROID MANAGED SYNCHRONIZATION"
    const val END_MARKER = "# END FINDROID MANAGED SYNCHRONIZATION"

    fun readDefaults(config: String): Result<MpvSynchronizationDefaults> = runCatching {
        val blocks = completeBlocks(config)
        ensureMarkersAreWellFormed(config, blocks)
        val source = blocks.lastOrNull()?.value ?: config
        MpvSynchronizationDefaults(
            audioMs = lastActiveAssignment(source, "audio-delay") ?: 0L,
            subtitleMs = lastActiveAssignment(source, "sub-delay") ?: 0L,
        )
    }

    fun writeDefaults(
        config: String,
        defaults: MpvSynchronizationDefaults,
    ): Result<String> = runCatching {
        val blocks = completeBlocks(config)
        ensureMarkersAreWellFormed(config, blocks)
        val lineEnding = if (config.contains("\r\n")) "\r\n" else "\n"
        var preserved = COMPLETE_BLOCK.replace(config, "")
        if (preserved.isNotEmpty() && !preserved.endsWith("\n") && !preserved.endsWith("\r")) {
            preserved += lineEnding
        }
        val block =
            buildString {
                append(BEGIN_MARKER).append(lineEnding)
                append("[default]").append(lineEnding)
                append("audio-delay=")
                    .append(MpvSynchronizationValue.formatMpvSeconds(defaults.audioMs))
                    .append(lineEnding)
                append("sub-delay=")
                    .append(MpvSynchronizationValue.formatMpvSeconds(defaults.subtitleMs))
                    .append(lineEnding)
                append(END_MARKER).append(lineEnding)
            }
        preserved + block
    }

    private fun completeBlocks(config: String): List<MatchResult> =
        COMPLETE_BLOCK.findAll(config).toList()

    private fun ensureMarkersAreWellFormed(config: String, blocks: List<MatchResult>) {
        val beginCount = Regex("(?m)^${Regex.escape(BEGIN_MARKER)}$").findAll(config).count()
        val endCount = Regex("(?m)^${Regex.escape(END_MARKER)}$").findAll(config).count()
        if (beginCount != endCount || blocks.size != beginCount) {
            throw MpvSynchronizationConfigException.MalformedManagedBlock
        }
    }

    private fun lastActiveAssignment(source: String, key: String): Long? {
        var profile: String? = null
        var found: Long? = null
        source.lineSequence().forEach { line ->
            val content = line.substringBefore('#').trim()
            val section = SECTION.matchEntire(content)
            if (section != null) {
                profile = section.groupValues[1].trim()
                return@forEach
            }
            if (profile != null && profile != "default") {
                return@forEach
            }
            val assignment = ASSIGNMENT.matchEntire(content) ?: return@forEach
            if (assignment.groupValues[1] != key) return@forEach
            found =
                MpvSynchronizationValue.parseSignedMpvSeconds(assignment.groupValues[2])
                    ?: throw IllegalArgumentException("Invalid active $key assignment")
        }
        return found
    }

    private val COMPLETE_BLOCK =
        Regex(
            "(?ms)^${Regex.escape(BEGIN_MARKER)}\\r?\\n.*?^${Regex.escape(END_MARKER)}(?:\\r?\\n)?"
        )
    private val SECTION = Regex("^\\[([^]]+)]$")
    private val ASSIGNMENT = Regex("^(audio-delay|sub-delay)\\s*=\\s*(\\S+)\\s*$")
}

sealed class MpvSynchronizationConfigException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    data object MalformedManagedBlock :
        MpvSynchronizationConfigException("The Findroid synchronization block is malformed")

    class ReadFailed(cause: Throwable) :
        MpvSynchronizationConfigException("Unable to read mpv.conf", cause)

    class WriteFailed(cause: Throwable) :
        MpvSynchronizationConfigException("Unable to save mpv.conf", cause)
}
