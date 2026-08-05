package dev.jdtech.jellyfin

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRouteTest {
    @Test
    fun `movie target carries play from beginning intent`() {
        val movieId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val route = PlayerRoute.movie(itemId = movieId, startFromBeginning = true)

        assertEquals(movieId.toString(), route.itemId)
        assertEquals("Movie", route.itemKind)
        assertTrue(route.startFromBeginning)
    }

    @Test
    fun `series target retains collection playback semantics`() {
        val seriesId = UUID.fromString("12345678-1234-5678-9abc-123456789abc")

        val route = PlayerRoute.series(itemId = seriesId)

        assertEquals(seriesId.toString(), route.itemId)
        assertEquals("Series", route.itemKind)
        assertFalse(route.startFromBeginning)
    }

    @Test
    fun `season target retains collection playback semantics`() {
        val seasonId = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210")

        val route = PlayerRoute.season(itemId = seasonId, startFromBeginning = true)

        assertEquals(seasonId.toString(), route.itemId)
        assertEquals("Season", route.itemKind)
        assertTrue(route.startFromBeginning)
    }

    @Test
    fun `episode target defaults to resume intent`() {
        val episodeId = UUID.fromString("11111111-2222-3333-4444-555555555555")

        val route = PlayerRoute.episode(itemId = episodeId)

        assertEquals(episodeId.toString(), route.itemId)
        assertEquals("Episode", route.itemKind)
        assertFalse(route.startFromBeginning)
    }
}
