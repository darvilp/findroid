package dev.jdtech.jellyfin.presentation.film.components

import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePlayedActionPolicyTest {
    @Test
    fun `enabled playback card action targets the selected item`() {
        val itemId = UUID.fromString("a90e3f35-3022-49c1-a660-84ec09f5ad86")

        assertEquals(
            HomeAction.MarkAsPlayed(itemId),
            HomeRow.Resume.playedActionFor(dummyMovie.copy(id = itemId)),
        )
        assertEquals(
            HomeAction.MarkAsPlayed(itemId),
            HomeRow.NextUp.playedActionFor(dummyMovie.copy(id = itemId)),
        )
    }

    @Test
    fun `non-playback home cards do not expose a played action`() {
        assertNull(HomeRow.Suggestions.playedActionFor(dummyMovie))
        assertNull(HomeRow.Latest.playedActionFor(dummyMovie))
    }
}
