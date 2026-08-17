package dev.jdtech.jellyfin

import dev.jdtech.jellyfin.models.InitialTrackSelection
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.descriptors.PrimitiveKind
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

    @Test
    fun `movie target carries an exact pre-playback track selection`() {
        val selection =
            InitialTrackSelection(
                mediaSourceId = "source-1",
                audioStreamIndex = 1,
                subtitleStreamIndex = 4,
            )

        val route =
            PlayerRoute.movie(
                itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                initialTrackSelection = selection,
            )

        assertEquals(selection, route.initialTrackSelection)
    }

    @Test
    fun `track selection route values survive serialization`() {
        val selections =
            listOf(
                null,
                InitialTrackSelection(mediaSourceId = "source-1", audioStreamIndex = 1),
                InitialTrackSelection(mediaSourceId = "source-1", subtitleStreamIndex = 4),
                InitialTrackSelection(
                    mediaSourceId = "source-1",
                    subtitleStreamIndex = InitialTrackSelection.SUBTITLE_OFF,
                ),
            )

        selections.forEach { selection ->
            val route =
                PlayerRoute.movie(
                    itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                    initialTrackSelection = selection,
                )

            val decoded = Json.decodeFromString<PlayerRoute>(Json.encodeToString(route))

            assertEquals(route, decoded)
            assertEquals(selection, decoded.initialTrackSelection)
        }
    }

    @Test
    fun `all serialized player route arguments use navigation supported primitives`() {
        val descriptor = PlayerRoute.serializer().descriptor

        repeat(descriptor.elementsCount) { index ->
            val argument = descriptor.getElementDescriptor(index)
            assertTrue(
                "${descriptor.getElementName(index)} must be a primitive navigation argument",
                argument.kind is PrimitiveKind,
            )
        }
    }
}
