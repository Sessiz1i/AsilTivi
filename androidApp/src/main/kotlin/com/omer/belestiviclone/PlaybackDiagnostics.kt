package com.omer.belestiviclone

import androidx.media3.common.PlaybackException
import java.security.MessageDigest

/** No exception messages, URI values or HTTP headers enter diagnostics. */
internal enum class PlaybackFailure(val message: String) {
    HEADERS("Oynatma başlıkları kayıpsız desteklenemiyor."),
    MEDIA_SOURCE("Oynatma kaynağı oluşturulamadı."),
    NETWORK("Yayına ağ üzerinden erişilemedi."),
    PLAYLIST_SEGMENT("Yayın listesi veya medya parçası okunamadı."),
    DECODER("Video veya ses çözümlenemedi."),
    PLAYER("Oynatma tamamlanamadı.");

    companion object {
        fun classify(code: Int, httpStatus: Int? = null): PlaybackFailure = when {
            httpStatus != null -> NETWORK
            code in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> NETWORK
            code in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PLAYLIST_SEGMENT
            code in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> DECODER
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
