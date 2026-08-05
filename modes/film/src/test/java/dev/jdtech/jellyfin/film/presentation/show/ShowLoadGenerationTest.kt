package dev.jdtech.jellyfin.film.presentation.show

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowLoadGenerationTest {
    @Test
    fun `out of order completion cannot overwrite the latest load`() {
        val loadGeneration = ShowLoadGeneration()
        val olderLoad = loadGeneration.begin()
        val latestLoad = loadGeneration.begin()
        var value = "unchanged"

        assertFalse(loadGeneration.commit(olderLoad) { value = "older" })
        assertEquals("unchanged", value)

        assertTrue(loadGeneration.commit(latestLoad) { value = "latest" })
        assertEquals("latest", value)
    }
}
