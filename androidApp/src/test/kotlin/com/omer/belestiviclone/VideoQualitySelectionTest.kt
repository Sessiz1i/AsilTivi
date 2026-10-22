package com.omer.belestiviclone

import androidx.media3.common.TrackSelectionParameters
import com.omer.belestiviclone.shared.preview.PlaybackPreferences
import kotlin.test.*

class VideoQualitySelectionTest {
    @Test fun videoCeilingPreservesAudioSubtitleAndOtherConstraints() {
        val initial=TrackSelectionParameters.Builder().setPreferredAudioRoleFlags(2).setPreferredTextRoleFlags(4)
            .setSelectUndeterminedTextLanguage(true).setMaxAudioBitrate(192000).setMaxVideoBitrate(8000000).build()
        val low=VideoQualitySelection.parameters(initial,PlaybackPreferences("360p"))
        assertEquals(640,low.maxVideoWidth);assertEquals(360,low.maxVideoHeight)
        assertEquals(initial.preferredAudioLanguages,low.preferredAudioLanguages)
        assertEquals(initial.preferredTextLanguages,low.preferredTextLanguages)
        assertEquals(initial.preferredAudioRoleFlags,low.preferredAudioRoleFlags)
        assertEquals(initial.preferredTextRoleFlags,low.preferredTextRoleFlags)
        assertEquals(initial.selectUndeterminedTextLanguage,low.selectUndeterminedTextLanguage)
        assertEquals(initial.maxAudioBitrate,low.maxAudioBitrate);assertEquals(initial.maxVideoBitrate,low.maxVideoBitrate)
        val high=VideoQualitySelection.parameters(low,PlaybackPreferences("1080p FHD"))
        assertEquals(1920,high.maxVideoWidth);assertEquals(1080,high.maxVideoHeight)
        assertEquals(Int.MAX_VALUE,VideoQualitySelection.parameters(high,PlaybackPreferences("unknown")).maxVideoHeight)
        assertEquals(360,low.maxVideoHeight);assertEquals(Int.MAX_VALUE,initial.maxVideoHeight)
    }
    @Test fun independentInstancesAndAttemptOwnership() {
        val initial=TrackSelectionParameters.Builder().build()
        val a=VideoQualitySelection.parameters(initial,PlaybackPreferences("480p"))
        val b=VideoQualitySelection.parameters(initial,PlaybackPreferences("4K UHD"))
        assertEquals(480,a.maxVideoHeight);assertEquals(2160,b.maxVideoHeight)
        val old=PlaybackAttempt.create("fresh","one","https://example.test/live")
        val next=PlaybackAttempt.create("fresh","two","https://example.test/live")
        assertFalse(next.accepts(old.id));assertTrue(next.accepts(next.id));assertFalse(next.accepts(null))
    }
}
