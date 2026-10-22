package com.omer.belestiviclone

import androidx.media3.common.*
import com.omer.belestiviclone.shared.preview.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object LanguageTrackSelection {
    fun tracks(tracks: Tracks, type: Int) = tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).map { index ->
            val f=group.getTrackFormat(index)
            LanguageTrack(group.mediaTrackGroup.id,index,f.id,f.language,group.isTrackSupported(index),group.isTrackSelected(index),
                f.roleFlags,f.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0)
        }
    }
    /** Each API updates only its own type, using the live selector's buildUpon subtype. */
    fun audio(current: TrackSelectionParameters, label: String): TrackSelectionParameters = current.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(TrackLanguage.audio(label).tag).build()
    fun text(current: TrackSelectionParameters, label: String, tracks: Tracks): TrackSelectionParameters {
        val request=TrackLanguage.subtitle(label)
        val target=subtitleTrack(request,tracks(tracks,C.TRACK_TYPE_TEXT))
        val builder=current.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setPreferredTextLanguage(request.tag).setSelectUndeterminedTextLanguage(request.mode == LanguageMode.FIRST_SUPPORTED)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,target == null)
        if (target != null) {
            val group=tracks.groups.first { it.type == C.TRACK_TYPE_TEXT && it.mediaTrackGroup.id == target.group }
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,target.index))
        }
        return builder.build()
    }
}
