package com.omer.belestiviclone

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.TextureView

/** Observation only. Never owns a SurfaceTextureListener or changes playback/thread dispatch. */
class RenderTrace(private val activity: Activity, val session: String?, private val database: String) {
    private val instance = identity(activity)
    internal fun immutableContext(): String = "pid=${android.os.Process.myPid()} task=${activity.taskId} activity=$instance session=$session db=$database key=2"
    fun event(name: String, player: TracePlayerMetadata? = null, texture: TextureView? = null, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        val c = activity.resources.configuration
        val view = activity.window.decorView
        val currentLooper = identity(Looper.myLooper())
        Log.i("BelesRender", "monoNs=${SystemClock.elapsedRealtimeNanos()} pid=${android.os.Process.myPid()} task=${activity.taskId} activity=$instance session=${session ?: "normal"} db=$database key=2 player=${player?.identity ?: "none"} playerSeq=${player?.sequence} appLooper=${player?.applicationLooperIdentity} texture=${identity(texture)} thread=${Thread.currentThread().id} appThread=${player?.applicationThreadId} main=${Looper.myLooper() == Looper.getMainLooper()} applicationThread=${player == null || player.onApplicationLooper(currentLooper)} event=$name uiMode=${c.uiMode} requestedOrientation=${activity.requestedOrientation} orientation=${c.orientation} config=${c.screenWidthDp}x${c.screenHeightDp} density=${c.densityDpi} window=${view.width}x${view.height} focus=${view.hasWindowFocus()} visible=${view.windowVisibility} multiWindow=${android.os.Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode} attached=${texture?.isAttachedToWindow} available=${texture?.isAvailable} viewport=${texture?.width}x${texture?.height} $detail")
    }
    companion object {
        fun identity(value: Any?) = value?.let { Integer.toHexString(System.identityHashCode(it)) } ?: "none"
    }
}
