package com.omer.belestiviclone

import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.omer.belestiviclone.shared.preview.PlaybackPreferences

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object VideoQualitySelection {
    /** buildUpon retains all audio/text/override state. Only video size constraints change. */
    fun parameters(current: TrackSelectionParameters, pref: PlaybackPreferences): TrackSelectionParameters {
        val builder = current.buildUpon().setMaxVideoSize(pref.cap?.width ?: Int.MAX_VALUE, pref.cap?.height ?: Int.MAX_VALUE)
        if (builder is DefaultTrackSelector.Parameters.Builder) builder.setExceedVideoConstraintsIfNecessary(false)
        return builder.build()
    }
}
