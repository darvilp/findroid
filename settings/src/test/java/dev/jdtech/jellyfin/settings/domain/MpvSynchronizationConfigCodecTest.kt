package dev.jdtech.jellyfin.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvSynchronizationConfigCodecTest {
    @Test
    fun `reads the last simple global assignments when no managed block exists`() {
        val config = "audio-delay=0.100\n# audio-delay=9\nsub-delay=-0.050\naudio-delay=0.250\n"

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 250L, subtitleMs = -50L),
            MpvSynchronizationConfigCodec.readDefaults(config).getOrThrow(),
        )
    }

    @Test
    fun `managed values are authoritative over ordinary assignments`() {
        val config =
            "audio-delay=9\n" +
                "# BEGIN FINDROID MANAGED SYNCHRONIZATION\n" +
                "audio-delay=0.125\n" +
                "sub-delay=-0.050\n" +
                "# END FINDROID MANAGED SYNCHRONIZATION\n" +
                "sub-delay=8\n"

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 125L, subtitleMs = -50L),
            MpvSynchronizationConfigCodec.readDefaults(config).getOrThrow(),
        )
    }

    @Test
    fun `write appends one complete block and preserves surrounding bytes`() {
        val original = "# user config\r\nprofile=fast\r\n"

        val updated =
            MpvSynchronizationConfigCodec.writeDefaults(
                    original,
                    MpvSynchronizationDefaults(audioMs = 125L, subtitleMs = -50L),
                )
                .getOrThrow()

        assertEquals(
            original +
                "# BEGIN FINDROID MANAGED SYNCHRONIZATION\r\n" +
                "[default]\r\n" +
                "audio-delay=0.125\r\n" +
                "sub-delay=-0.050\r\n" +
                "# END FINDROID MANAGED SYNCHRONIZATION\r\n",
            updated,
        )
    }

    @Test
    fun `write normalizes duplicate complete blocks and is idempotent`() {
        val block =
            "# BEGIN FINDROID MANAGED SYNCHRONIZATION\n" +
                "audio-delay=1.000\nsub-delay=2.000\n" +
                "# END FINDROID MANAGED SYNCHRONIZATION\n"
        val original = "vo=gpu\n${block}ao=audiotrack\n$block"
        val defaults = MpvSynchronizationDefaults(audioMs = 10L, subtitleMs = -20L)

        val once = MpvSynchronizationConfigCodec.writeDefaults(original, defaults).getOrThrow()
        val twice = MpvSynchronizationConfigCodec.writeDefaults(once, defaults).getOrThrow()

        assertEquals(once, twice)
        assertEquals(1, "BEGIN FINDROID".toRegex().findAll(once).count())
        assertTrue(once.startsWith("vo=gpu\nao=audiotrack\n"))
    }

    @Test
    fun `malformed markers refuse reads and writes`() {
        val malformed = "vo=gpu\n# BEGIN FINDROID MANAGED SYNCHRONIZATION\naudio-delay=1\n"

        assertTrue(MpvSynchronizationConfigCodec.readDefaults(malformed).isFailure)
        assertTrue(
            MpvSynchronizationConfigCodec.writeDefaults(
                    malformed,
                    MpvSynchronizationDefaults(0L, 0L),
                )
                .isFailure
        )
    }

    @Test
    fun `named profile assignments are ignored while global and default assignments count`() {
        val config =
            "audio-delay=0.100\n" +
                "[cinema]\n" +
                "audio-delay=9.000\n" +
                "sub-delay=8.000\n" +
                "[default]\n" +
                "sub-delay=-0.250 # active default value\n"

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 100L, subtitleMs = -250L),
            MpvSynchronizationConfigCodec.readDefaults(config).getOrThrow(),
        )
    }

    @Test
    fun `inline comments and any exact decimal scale are parsed`() {
        val config = "audio-delay=0.125000 # exact\nsub-delay=-0.050000#exact\n"

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 125L, subtitleMs = -50L),
            MpvSynchronizationConfigCodec.readDefaults(config).getOrThrow(),
        )
    }

    @Test
    fun `invalid active values fail instead of falling back to zero`() {
        assertTrue(MpvSynchronizationConfigCodec.readDefaults("audio-delay=0.0005\n").isFailure)
        assertTrue(
            MpvSynchronizationConfigCodec.readDefaults("sub-delay=9223372036854775.808\n")
                .isFailure
        )
    }

    @Test
    fun `invalid values inside named profiles do not poison global discovery`() {
        val config =
            "audio-delay=0.100\n" +
                "[broken]\naudio-delay=not-a-number\n" +
                "[Default]\naudio-delay=9.000\n"

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 100L, subtitleMs = 0L),
            MpvSynchronizationConfigCodec.readDefaults(config).getOrThrow(),
        )
    }

    @Test
    fun `one kind rewrite preserves the other discovered active value`() {
        val original = "audio-delay=0.125000 # user\nsub-delay=-0.050000 # user\n"
        val discovered = MpvSynchronizationConfigCodec.readDefaults(original).getOrThrow()

        val updated =
            MpvSynchronizationConfigCodec.writeDefaults(
                    original,
                    discovered.withValue(MpvSynchronizationKind.AUDIO, 300L),
                )
                .getOrThrow()

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 300L, subtitleMs = -50L),
            MpvSynchronizationConfigCodec.readDefaults(updated).getOrThrow(),
        )
    }

    @Test
    fun `managed block is global at eof and replacing old blocks preserves every user byte`() {
        val oldBlock =
            "# BEGIN FINDROID MANAGED SYNCHRONIZATION\n" +
                "audio-delay=1.000\n" +
                "sub-delay=2.000\n" +
                "# END FINDROID MANAGED SYNCHRONIZATION\n"
        val userBytes = "# user\n[cinema]\nvideo-sync=display-resample\n"

        val updated =
            MpvSynchronizationConfigCodec.writeDefaults(
                    userBytes + oldBlock + oldBlock,
                    MpvSynchronizationDefaults(125L, -50L),
                )
                .getOrThrow()

        assertEquals(
            userBytes +
                "# BEGIN FINDROID MANAGED SYNCHRONIZATION\n" +
                "[default]\n" +
                "audio-delay=0.125\n" +
                "sub-delay=-0.050\n" +
                "# END FINDROID MANAGED SYNCHRONIZATION\n",
            updated,
        )
    }
}
