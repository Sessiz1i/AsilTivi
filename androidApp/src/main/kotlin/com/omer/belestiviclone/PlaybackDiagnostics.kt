package com.omer.belestiviclone

import androidx.media3.common.PlaybackException
import java.security.MessageDigest

/** No exception messages, URI values or HTTP headers enter diagnostics. */
internal enum class PlaybackFailure(val message: String, val multiStreamMessage: String = message) {
    HEADERS("Oynatma başlıkları kayıpsız desteklenemiyor."),
    MEDIA_SOURCE("Oynatma kaynağı oluşturulamadı."),
    NETWORK("Yayına ağ üzerinden erişilemedi."),
    PLAYLIST_SEGMENT("Yayın listesi veya medya parçası okunamadı."),
    DECODER(
        message = "Video veya ses çözümlenemedi.",
        multiStreamMessage = "Cihaz bu kadar yayını aynı anda desteklemiyor."
    ),
    PLAYER("Oynatma tamamlanamadı.");

    fun displayMessage(channelName: String?, isMultiScreen: Boolean = false): String {
        val base = if (isMultiScreen) multiStreamMessage else message
        val prefix = channelName?.takeIf { it.isNotBlank() }
        return if (prefix != null) "$prefix · $base" else base
    }

    companion object {
        private val codecResourceFailureClassKeywords = listOf(
            "DecoderInit",
            "MediaCodec",
            "AudioTrack",
            "OutOfMemory",
            "InsufficientResources",
            "ResourceBusy",
            "CodecException"
        )

        fun isBehindLiveWindow(code: Int, causeChain: String? = null): Boolean =
            code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                (causeChain != null && causeChain.contains("BehindLiveWindowException", ignoreCase = true))

        fun classify(code: Int, httpStatus: Int? = null, causeChain: String? = null): PlaybackFailure = when {
            httpStatus != null -> NETWORK
            code in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> NETWORK
            code in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PLAYLIST_SEGMENT
            code in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> DECODER
            code in PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED..PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED -> DECODER
            causeChain != null && codecResourceFailureClassKeywords.any { causeChain.contains(it, ignoreCase = true) } -> DECODER
            else -> PLAYER
        }
    }
}

internal data class PlaybackAttempt(val id: String, val profile: String, val channelId: String, val uriHash: String) {
    fun accepts(mediaId: String?) = mediaId == id
    val logContext get() = "attempt=$id profile=$profile channel=$channelId uriSha256=$uriHash"

    companion object {
        fun create(profile: String, channelId: String, uri: String) = PlaybackAttempt(
            java.util.UUID.randomUUID().toString(), profile, channelId,
            MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
        )
    }
}

internal fun causeClasses(error: Throwable): String = generateSequence(error) { it.cause }.take(6).joinToString(">") { it.javaClass.simpleName }
