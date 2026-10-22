package com.omer.belestiviclone

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DecoderReuseEvaluation
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** R39 observation only. Player getters stay on its application thread, before close(). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class R39Diagnostics(private val exo: ExoPlayer, private val context: String, private val metadata: TracePlayerMetadata) {
    private val handler = Handler(exo.applicationLooper)
    @Volatile private var active: String? = null
    @Volatile private var closed = false
    @Volatile private var texture = "none"
    private var selectNs = 0L
    private var endLogged = false
    private val frames = AtomicLong()
    private val lastPtsUs = AtomicLong(Long.MIN_VALUE)
    private fun emit(name: String, data: JSONObject = JSONObject()) {
        if (closed) return
        val now = SystemClock.elapsedRealtimeNanos()
        data.put("event", name).put("monoNs", now).put("attempt", active).put("texture", texture)
            .put("player", metadata.identity).put("playerSeq", metadata.sequence)
            .put("thread", Thread.currentThread().id).put("appThread", metadata.applicationThreadId)
        Log.i("BelesR39Metric", "$context state=$data")
    }
    private fun snapshot(name: String, extra: JSONObject = JSONObject()) {
        check(Thread.currentThread().id == metadata.applicationThreadId)
        if (closed) return
        val timeline = exo.currentTimeline
        extra.put("positionMs", exo.currentPosition).put("playing", exo.isPlaying).put("state", exo.playbackState)
            .put("mediaItemIndex", exo.currentMediaItemIndex).put("periodIndex", exo.currentPeriodIndex)
            .put("mediaId", exo.currentMediaItem?.mediaId).put("liveOffsetMs", exo.currentLiveOffset)
            .put("frameCount", frames.get()).put("lastFramePtsUs", lastPtsUs.get())
        exo.videoDecoderCounters?.let { c -> c.ensureUpdated(); extra.put("renderedBuffers", c.renderedOutputBufferCount).put("droppedBuffers", c.droppedBufferCount).put("skippedBuffers", c.skippedOutputBufferCount) }
        if (!timeline.isEmpty && exo.currentMediaItemIndex in 0 until timeline.windowCount) {
            val w = timeline.getWindow(exo.currentMediaItemIndex, Timeline.Window())
            extra.put("windowUid", RenderTrace.identity(w.uid)).put("windowStartTimeMs", w.windowStartTimeMs)
                .put("positionInFirstPeriodUs", w.positionInFirstPeriodUs).put("windowDurationUs", w.durationUs)
                .put("windowDefaultPositionUs", w.defaultPositionUs).put("live", w.isLive)
            if (exo.currentPeriodIndex in 0 until timeline.periodCount) {
                val p = timeline.getPeriod(exo.currentPeriodIndex, Timeline.Period(), true)
                extra.put("periodUid", RenderTrace.identity(p.uid)).put("periodPositionInWindowUs", p.positionInWindowUs)
            }
        }
        emit(name, extra)
    }
    private val samples = object : Runnable {
        override fun run() {
            if (closed || active == null) return
            val now = SystemClock.elapsedRealtimeNanos()
            val elapsed = now - selectNs
            if (elapsed <= 25_000_000_000L) snapshot("sample", JSONObject().put("sinceSelectNs", elapsed))
            else if (!endLogged) { endLogged = true; snapshot("window25-end", JSONObject().put("selectMonoNs", selectNs).put("deadlineMonoNs", selectNs + 25_000_000_000L).put("sinceSelectNs", elapsed)) }
            handler.postDelayed(this, 1000)
        }
    }
    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = snapshot("timeline", JSONObject().put("reason", reason))
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
            snapshot("discontinuity", JSONObject().put("reason", reason).put("oldPositionMs", oldPosition.positionMs)
                .put("newPositionMs", newPosition.positionMs).put("oldWindowUid", RenderTrace.identity(oldPosition.windowUid))
                .put("newWindowUid", RenderTrace.identity(newPosition.windowUid)).put("oldPeriodUid", RenderTrace.identity(oldPosition.periodUid))
                .put("newPeriodUid", RenderTrace.identity(newPosition.periodUid)))
        override fun onPlaybackStateChanged(playbackState: Int) = snapshot("state", JSONObject().put("value", playbackState))
        override fun onIsPlayingChanged(isPlaying: Boolean) = snapshot("isPlaying", JSONObject().put("value", isPlaying))
    }
    private val analytics = object : AnalyticsListener {
        override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) = snapshot("first-frame")
        override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) =
            snapshot("input-format", JSONObject().put("width", format.width).put("height", format.height)
                .put("mime", format.sampleMimeType).put("codec", format.codecs).put("frameRate", format.frameRate)
                .put("bitrate", format.bitrate).put("reuseResult", decoderReuseEvaluation?.result))
    }
    init {
        exo.addListener(listener); exo.addAnalyticsListener(analytics)
        exo.setVideoFrameMetadataListener { pts, releaseTimeNs, format, _ ->
            // The callback thread uses immutable IDs and callback values only, never exo getters.
            if (!closed) {
                val count = frames.incrementAndGet(); lastPtsUs.set(pts)
                emit("frame-input", JSONObject().put("frameCount", count).put("presentationTimeUs", pts)
                    .put("releaseTimeNs", releaseTimeNs).put("width", format.width).put("height", format.height))
            }
        }
    }
    fun select(id: String, selectedAtNs: Long) {
        handler.removeCallbacks(samples); active = id; selectNs = selectedAtNs; endLogged = false
        frames.set(0); lastPtsUs.set(Long.MIN_VALUE)
        emit("select", JSONObject().put("selectMonoNs", selectNs).put("deadlineMonoNs", selectNs + 25_000_000_000L).put("cadenceMs", 1000))
        handler.postDelayed(samples, 1000)
    }
    fun surface(event: String, id: String) { texture = id; emit("surface-$event") }
    fun close() {
        if (closed) return
        snapshot("close"); handler.removeCallbacks(samples)
        exo.removeListener(listener); exo.removeAnalyticsListener(analytics); closed = true
    }
}
