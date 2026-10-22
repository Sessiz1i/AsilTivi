package com.omer.belestiviclone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private val bitmapCache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(64, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 64
})
private val failurePolicy = LogoRequestPolicy()
private val requestLocks = Array(4) { Mutex() }
private var logoEpoch by mutableIntStateOf(0)
private data class LogoResult(val bitmap: Bitmap? = null, val failure: LogoFailureKind? = null)

internal fun clearPreviewLogoCache() {
    bitmapCache.clear()
    logoEpoch = failurePolicy.clear()
    android.util.Log.i("BelesLogo", "cache-cleared generation=$logoEpoch")
}

@Composable
internal fun channelLogoBitmap(name: String, asset: String?): Bitmap? {
    val context = LocalContext.current.applicationContext
    val key = asset?.let { "asset:$it" } ?: name
    val epoch = logoEpoch
    val bitmap by produceState<Bitmap?>(bitmapCache[key], key, epoch) {
        value = loadArtwork(context, name, asset, key, epoch)
        val failed = failurePolicy.failure(key)
        // One automatic retry per visible composition, only for transient transport failures.
        // HTTP/decode/unknown SVG failures wait for re-entry after TTL or explicit cache refresh.
        if (value == null && failed?.kind?.automaticRetry == true) {
            delay((failed.retryAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(1))
            value = loadArtwork(context, name, asset, key, epoch)
        }
    }
    return bitmap
}

private suspend fun loadArtwork(context: Context, name: String, asset: String?, key: String, epoch: Int): Bitmap? =
    requestLocks[key.hashCode().ushr(1) % requestLocks.size].withLock {
        if (epoch != logoEpoch) return@withLock null
        bitmapCache[key]?.let { return@withLock it }
        if (!failurePolicy.mayRequest(key, SystemClock.elapsedRealtime())) return@withLock null
        val result = withContext(Dispatchers.IO) { readArtwork(context, name, asset) }
        currentCoroutineContext().ensureActive()
        if (epoch != logoEpoch) return@withLock null
        result.bitmap?.also { bitmapCache[key] = it } ?: run {
            failurePolicy.record(key, result.failure ?: LogoFailureKind.DECODE, SystemClock.elapsedRealtime(), epoch)
            null
        }
    }

private fun readArtwork(context: Context, name: String, asset: String?): LogoResult {
    val hash = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    var stage = if (asset != null) "asset" else "transport"
    return try {
        val bytes = if (asset != null) context.assets.open(asset).use { it.logoBytes() } else {
            val connection = URL(name).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000; connection.readTimeout = 3000
            try {
                val code = connection.responseCode; stage = "http-$code"
                if (code !in 200..299) {
                    android.util.Log.i("BelesLogo", "uriSha256=$hash stage=$stage fallback=true")
                    return LogoResult(failure = LogoFailureKind.HTTP)
                }
                if (connection.contentType.orEmpty().substringBefore(';') == "image/svg+xml") {
                    android.util.Log.i("BelesLogo", "uriSha256=$hash stage=unsupported-svg fallback=true")
                    return LogoResult(failure = LogoFailureKind.UNSUPPORTED)
                }
                connection.inputStream.use { it.logoBytes() }
            } finally { connection.disconnect() }
        }
        stage = "decode"
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return LogoResult(failure = LogoFailureKind.DECODE)
        val options = BitmapFactory.Options().also { it.inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (BuildConfig.DEBUG && asset != null) android.util.Log.i("BelesLogo", "uriSha256=$hash stage=bundled bitmap=${bitmap?.width}x${bitmap?.height}")
        LogoResult(bitmap, if (bitmap == null) LogoFailureKind.DECODE else null)
    } catch (failure: Exception) {
        android.util.Log.i("BelesLogo", "uriSha256=$hash stage=$stage exception=${failure.javaClass.simpleName} fallback=true")
        LogoResult(failure = if (asset == null && failure is java.io.IOException && stage != "decode") LogoFailureKind.NETWORK else LogoFailureKind.DECODE)
    }
}

private fun java.io.InputStream.logoBytes(): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer); if (size < 0) break
        require(output.size() + size <= 4 * 1024 * 1024)
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
