package dev.jdtech.jellyfin.settings.domain

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `mpv defaults to audiotrack when no audio output is stored`() {
        val preferences = AppPreferences(sharedPreferencesWithAudioOutput(null))

        assertEquals("audiotrack", preferences.getValue(preferences.playerMpvAo))
    }

    @Test
    fun `mpv preserves an explicitly selected aaudio output`() {
        val preferences = AppPreferences(sharedPreferencesWithAudioOutput("aaudio"))

        assertEquals("aaudio", preferences.getValue(preferences.playerMpvAo))
    }

    private fun sharedPreferencesWithAudioOutput(audioOutput: String?): SharedPreferences {
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getString" -> audioOutput ?: arguments?.get(1)
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
