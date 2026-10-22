package com.omer.belestiviclone

import androidx.media3.common.*
import com.omer.belestiviclone.shared.preview.PlaybackPreferences
import kotlin.test.*

class LanguageTrackSelectionTest {
    // JVM's android.jar throws in TextUtils while Media3 builds its ISO registry. These tests
    // exercise selection parameters with already canonical tags, not Android locale behavior.
    // Prime only that cache in this test process; native R41 uses the real Android registry.
    private val registry=androidx.media3.common.util.Util::class.java.getDeclaredField("languageTagReplacementMap").apply { isAccessible=true }
    private var originalRegistry: Any? = null
    @BeforeTest fun canonicalTagRegistry() { originalRegistry=registry.get(null);registry.set(null,hashMapOf("eng" to "en","deu" to "de")) }
    @AfterTest fun restoreRegistry() { registry.set(null,originalRegistry) }
    @Test fun independentTypeUpdatesKeepVideoCapRoleBitrateAndOtherLanguage() {
        val base=TrackSelectionParameters.Builder().setPreferredAudioRoleFlags(2).setPreferredTextRoleFlags(4)
            .setMaxAudioBitrate(192000).setMaxVideoBitrate(8000000).build()
        val video=VideoQualitySelection.parameters(base,PlaybackPreferences("360p"))
        val audio=LanguageTrackSelection.audio(video,"Deutsch")
        assertEquals(listOf("de"),audio.preferredAudioLanguages);assertEquals(640,audio.maxVideoWidth)
        assertEquals(360,audio.maxVideoHeight);assertEquals(4,audio.preferredTextRoleFlags);assertEquals(2,audio.preferredAudioRoleFlags)
        assertEquals(192000,audio.maxAudioBitrate);assertEquals(8000000,audio.maxVideoBitrate)
        val off=LanguageTrackSelection.text(audio,"Kapalı",Tracks.EMPTY)
        assertTrue(C.TRACK_TYPE_TEXT in off.disabledTrackTypes);assertEquals(audio.preferredAudioLanguages,off.preferredAudioLanguages)
        val higher=VideoQualitySelection.parameters(off,PlaybackPreferences("1080p FHD"))
        assertEquals(1080,higher.maxVideoHeight);assertTrue(C.TRACK_TYPE_TEXT in higher.disabledTrackTypes)
        assertEquals(listOf("de"),higher.preferredAudioLanguages)
    }
    // Supported TrackGroup/override fixture runs in R41TracksActivity on real Android:
    // TrackGroup's constructor also requires android.text.TextUtils, unavailable in this JVM.
    @Test fun entryRequiresExactSessionActivityCandidateDatabaseAndCommand() {
        val session=R41Entry.sessions.first()
        fun admit(debug: Boolean=true,candidate: String?=R41Entry.candidate,s: String?=session,action: String?=R41Entry.action,
            cmd: String?="open",db: String?="$session.db",key: Int=2,clazz: String=R41Entry.activity,restored: Boolean=false) =
            R41Entry.admit(debug,candidate,s,action,cmd,db,key,clazz,restored)
        assertEquals(session,admit())
        listOf(admit(debug=false),admit(candidate="R20-C013"),admit(s=null),admit(s=session+"x"),admit(action="android.intent.action.MAIN"),
            admit(cmd="unknown"),admit(db="belestivi_preview_v1.db"),admit(key=1),admit(clazz="com.omer.belestiviclone.MainActivity"),admit(restored=true)).forEach(::assertNull)
        assertTrue(R41Entry.marked(null,null,setOf("r41Action"),"MainActivity"))
        assertTrue(R41Entry.marked(null,null,emptySet(),R41Entry.activity))
    }
}
