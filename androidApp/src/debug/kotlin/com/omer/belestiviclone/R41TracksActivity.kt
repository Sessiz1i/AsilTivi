package com.omer.belestiviclone

import android.os.SystemClock
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.sharedui.PreviewState
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import androidx.media3.common.*
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/** No normal store, catalogue, old cache or restore seed is used by this route. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class R41TracksActivity: MainActivity() {
    private lateinit var session: String
    private lateinit var seedData: PreviewData
    private var store: AndroidPreviewStore? = null
    private var loadedOnce=false
    private var readyOnce=false
    private var failNext=false
    private fun event(name: String, detail: String="") = Log.i("BelesR41",
        "monoNs=${SystemClock.elapsedRealtimeNanos()} session=$session db=$session.db key=2 event=$name $detail")
    private fun id(): OpaqueId { val u=UUID.randomUUID();return OpaqueId(u.mostSignificantBits,u.leastSignificantBits) }
    override fun r41Store(session: String): PreviewStore {
        this.session=session
        nativeSelectionFixture()
        val name="$session.db";val cache=File(filesDir,"$session-catalog")
        check(listOf(name,"$name-wal","$name-shm").none { getDatabasePath(it).exists() } && !cache.exists() && !File("${cache.path}.bak").exists())
        val uri="https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8"
        val ch=PreviewChannel(id(),"R41 · NOT_CATALOG_SAMPLE","R41 test","ikon_tv",definition=PlaybackDefinition(Address.fromForm(uri),mimeType="application/x-mpegURL"))
        seedData=PreviewData(channels=listOf(ch),categories=listOf(ch.category),favorites=listOf(ch.id),history=emptyList(),sources=emptyList(),profileId=id())
            .withPref("startup","Açık").withPref("history","Kapalı").withPref("lastChannel",PreviewCodec.id(ch.id))
        check(cache.createNewFile());cache.writeText(LibraryCodec.channels(seedData.channels),Charsets.UTF_8)
        store=AndroidPreviewStore(applicationContext,key=2,databaseName=name) { seedData }
        event("fresh-fixture","testId=${PreviewCodec.id(ch.id)} uriSha256=${r40Hash(uri)} normalStoreOpened=false sample=NOT_CATALOG_SAMPLE")
        return object: PreviewStore {
            override suspend fun load(): PreviewData {
                try {
                    val s=requireNotNull(store)
                    if (loadedOnce) check(s.readRow()!=null) { "R41-existing-row-required" }
                    val data=s.load();check(data.channels==seedData.channels);check(s.readRow()!=null);loadedOnce=true
                    event("fixture-load","existingRow=$loadedOnce");return data
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    event("HOLD","type=${e.javaClass.simpleName} noDemoFallback=true normalStoreOpened=false")
                    withContext(Dispatchers.Main.immediate) { finish() }
                    throw CancellationException("R41-owned-load-HOLD").apply { initCause(e) }
                }
            }
            override suspend fun save(data: PreviewData) {
                if (failNext) { failNext=false;event("forced-write-failure");error("R41-owned-fixture-write-fault") }
                requireNotNull(store).save(data)
                event("fixture-save","audio=${data.pref("audio")} text=${data.pref("subtitle")} quality=${data.pref("quality")}")
            }
        }
    }
    private fun nativeSelectionFixture() {
        fun group(id: String,lang: String?,support: Int) = Tracks.Group(TrackGroup(id,Format.Builder().setId(id)
            .setSampleMimeType("text/vtt").setLanguage(lang).setRoleFlags(C.ROLE_FLAG_SUBTITLE).build()),false,intArrayOf(support),booleanArrayOf(false))
        val tracks=Tracks(listOf(group("bad","tr",C.FORMAT_UNSUPPORTED_TYPE),group("regional","en-US",C.FORMAT_HANDLED),group("unknown",null,C.FORMAT_HANDLED)))
        val initial=DefaultTrackSelector.Parameters.Builder(applicationContext).setPreferredTextLanguage("fr")
            .setPreferredAudioRoleFlags(C.ROLE_FLAG_ALTERNATE).setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE).setMaxAudioBitrate(192000)
            .setExceedVideoConstraintsIfNecessary(true).build()
        val video=VideoQualitySelection.parameters(initial,PlaybackPreferences("360p"))
        val audio=LanguageTrackSelection.audio(video,"English")
        check(audio is DefaultTrackSelector.Parameters && !audio.exceedVideoConstraintsIfNecessary)
        check(audio.maxVideoHeight==360 && audio.preferredTextLanguages==initial.preferredTextLanguages)
        check(audio.preferredTextRoleFlags==C.ROLE_FLAG_SUBTITLE && audio.preferredAudioRoleFlags==C.ROLE_FLAG_ALTERNATE && audio.maxAudioBitrate==192000)
        val english=LanguageTrackSelection.text(audio,"English",tracks)
        check(C.TRACK_TYPE_TEXT !in english.disabledTrackTypes && english.overrides.keys.single().id=="regional")
        check(english.preferredAudioLanguages==audio.preferredAudioLanguages && english.maxVideoHeight==360)
        val missing=LanguageTrackSelection.text(english,"Türkçe",tracks)
        check(C.TRACK_TYPE_TEXT in missing.disabledTrackTypes && missing.overrides.isEmpty())
        val auto=LanguageTrackSelection.text(missing,"Otomatik (İlk Bulunan)",tracks)
        check(auto.overrides.keys.single().id=="regional" && auto.maxVideoHeight==360)
        val off=LanguageTrackSelection.text(auto,"Kapalı",tracks)
        check(C.TRACK_TYPE_TEXT in off.disabledTrackTypes && off.preferredAudioLanguages==audio.preferredAudioLanguages)
        event("native-parameter-fixture","PASS subtype=true cap=true roles=true otherLanguage=true unsupportedSkip=true region=true missingDisabled=true off=true actualPlayback=false")
    }
    override suspend fun r41Ready(state: PreviewState) {
        if (readyOnce) return;readyOnce=true
        val started=SystemClock.elapsedRealtimeNanos()
        try {
            check(!state.sessionOnly && state.channel?.id==seedData.channels.single().id)
            state.info=true;state.panel=false
            event("budget-start","limitNs=90000000000 sourceTestsPlanned=1 maxProfiles=2")
            if(session.endsWith("-a")) {
                window("default-tr-off",8000)
                change(state,"audio","English",0);change(state,"subtitle","English",12000)
                change(state,"audio","Deutsch",14000)
                change(state,"subtitle","Otomatik (İlk Bulunan)",8000)
                change(state,"subtitle","Kapalı",4000)
                change(state,"audio","Español",0);change(state,"subtitle","Türkçe",5000)
                change(state,"audio","Auto",0);change(state,"subtitle","Français",8000)
                change(state,"quality","360p",6000)
                change(state,"subtitle","English",7000)
            } else {
                window("default-tr-off",6000)
                change(state,"audio","Deutsch",0);change(state,"subtitle","English",12000)
                change(state,"subtitle","Otomatik (İlk Bulunan)",6000)
                change(state,"subtitle","Kapalı",2000)
                change(state,"subtitle","Türkçe",3000)
                change(state,"subtitle","English",0)
                val before=state.data;failNext=true
                state.pref("audio","Español");while(state.busy)delay(10)
                check(state.data==before);event("failed-write-preserved")
                change(state,"audio","legacy-audio",0);change(state,"subtitle","legacy-text",4000)
                event("settings-begin");state.settings();delay(500)
                change(state,"quality","360p",0);change(state,"audio","English",0);change(state,"subtitle","English",0)
                state.back();state.panel=false;state.info=true;window("settings-return-new-instance",10000)
                val alias=state.channel!!.copy(id=id(),name="R41 synthetic alias")
                state.commit(state.data.copy(channels=state.data.channels+alias));while(state.busy)delay(10)
                state.select(alias.id);window("channel-change",5000)
                state.retry++;window("retry-new-attempt",5000)
                state.startMulti(2);state.multi!!.slots[1]=state.selected;state.multiRevision++
                window("two-slot",5000);state.back();state.panel=false;window("slots-return",4000)
                change(state,"subtitle","Kapalı",2000)
            }
            state.page="home";state.dialog=null;state.multi=null;delay(500)
            val expected=state.data;val row=requireNotNull(store?.readRow());store!!.close()
            store=AndroidPreviewStore(applicationContext,key=2,databaseName="$session.db") { seedData }
            check(store!!.readRow()==row) // existing own row, before pure decode base
            check(store!!.load()==expected && store!!.readRow()==row)
            event("own-room-reopen","sameRow=true audio=${expected.pref("audio")} text=${expected.pref("subtitle")} normalStoreOpened=false")
            check(SystemClock.elapsedRealtimeNanos()-started < 90_000_000_000L)
            event("budget-end","durationNs=${SystemClock.elapsedRealtimeNanos()-started}")
            event("product-exit-request");state.exitRequested=true
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { event("HOLD","type=${e.javaClass.simpleName}");state.exitRequested=true }
    }
    private suspend fun window(name: String,ms: Long) { event("window-start","name=$name durationMs=$ms");delay(ms);event("window-end","name=$name") }
    private suspend fun change(state: PreviewState,key: String,value: String,ms: Long) {
        event("pref-request","key=$key value=$value requestedNs=${SystemClock.elapsedRealtimeNanos()}")
        state.pref(key,value);while(state.busy)delay(10);check(state.data.pref(key)==value)
        event("pref-visible","key=$key value=$value");window("$key-$value",ms)
    }
    override fun onDestroy() { super.onDestroy();store?.close();store=null;if(::session.isInitialized)event("destroy") }
}
