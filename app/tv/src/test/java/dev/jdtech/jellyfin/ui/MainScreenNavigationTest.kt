package dev.jdtech.jellyfin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenNavigationTest {
    @Test
    fun `home remains the default destination when search is available`() {
        val destinations = listOf(TabDestination.Search, TabDestination.Home, TabDestination.Libraries)

        assertEquals(1, defaultTabIndex(destinations))
        assertEquals(0, defaultTabIndex(destinations.drop(1)))
    }
}
