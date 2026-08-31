import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindroidTvReleasePolicyTest {
    @Test
    fun `debug keeps the existing identity and upstream version`() {
        val variant = findroidTvVariant("debug", "1.1.0", 33, revision = 1)

        assertEquals("dev.jdtech.jellyfin.debug", variant.applicationId)
        assertEquals("Findroid Debug", variant.label)
        assertEquals("1.1.0", variant.versionName)
        assertEquals(33, variant.versionCode)
    }

    @Test
    fun `release uses the public ATV identity and revisioned version`() {
        val variant = findroidTvVariant("release", "1.1.0", 33, revision = 1)

        assertEquals("dev.jdtech.jellyfin.atv", variant.applicationId)
        assertEquals("Findroid TV", variant.label)
        assertEquals("1.1.0-atv.1", variant.versionName)
        assertEquals(33001, variant.versionCode)
    }

    @Test
    fun `APK defaults to four ABI splits while universal property disables them`() {
        assertTrue(findroidTvAbiSplitsEnabled(emptyList(), universalApk = false))
        assertTrue(findroidTvAbiSplitsEnabled(listOf("assembleLibreRelease"), universalApk = false))
        assertFalse(findroidTvAbiSplitsEnabled(listOf("assembleLibreRelease"), universalApk = true))
    }

    @Test
    fun `bundle workaround always disables ABI splits`() {
        assertFalse(findroidTvAbiSplitsEnabled(listOf(":app:tv:bundleLibreRelease"), universalApk = false))
        assertFalse(findroidTvAbiSplitsEnabled(listOf(":app:tv:bundleLibreRelease"), universalApk = true))
    }

    @Test
    fun `release signing preflight ignores lint dependencies and accepts artifact tasks`() {
        assertFalse(findroidTvReleaseArtifactRequested("packageLibreReleaseResources"))
        assertFalse(findroidTvReleaseArtifactRequested("lintLibreRelease"))
        assertTrue(findroidTvReleaseArtifactRequested("assembleLibreRelease"))
        assertTrue(findroidTvReleaseArtifactRequested("bundleLibreRelease"))
        assertTrue(findroidTvReleaseArtifactRequested("packageLibreRelease"))
        assertTrue(findroidTvReleaseArtifactRequested("signLibreReleaseBundle"))
    }
}
