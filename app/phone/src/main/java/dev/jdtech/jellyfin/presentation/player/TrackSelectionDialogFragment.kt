package dev.jdtech.jellyfin.presentation.player

import android.app.Dialog
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import java.lang.IllegalStateException

class TrackSelectionDialogFragment(
    private val type: @C.TrackType Int,
    private val viewModel: PlayerViewModel,
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleResource =
            when (type) {
                C.TRACK_TYPE_AUDIO -> R.string.select_audio_track
                C.TRACK_TYPE_TEXT -> R.string.select_subtitle_track
                else -> throw IllegalStateException("TrackType must be AUDIO or TEXT")
            }
        val tracks = viewModel.currentTrackOptions(type).filter { it.supported }
        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(titleResource)).setSingleChoiceItems(
                arrayOf(getString(R.string.none)) +
                    tracks
                        .map { track ->
                            listOfNotNull(track.label, track.language, track.codec)
                                .filter { it.isNotBlank() }
                                .joinToString(separator = " - ")
                        }
                        .toTypedArray(), // Add "None" at the top of the list
                tracks.indexOfFirst { it.selected } +
                    1, // Add 1 to the index to account for the "None" item
            ) { dialog, which ->
                val track = tracks.getOrNull(which - 1)
                viewModel.switchToTrack(type, track?.groupIndex, track?.trackIndex)
                dialog.dismiss()
            }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix for hiding the system bars on API < 30
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
