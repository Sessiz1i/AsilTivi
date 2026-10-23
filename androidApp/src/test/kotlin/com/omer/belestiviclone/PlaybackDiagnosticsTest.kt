package com.omer.belestiviclone

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackDiagnosticsTest {
    @Test fun httpAndNetworkFailuresRemainDistinctFromModuleCreation() {
        assertEquals(PlaybackFailure.NETWORK, PlaybackFailure.classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
        assertEquals(PlaybackFailure.NETWORK, PlaybackFailure.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(PlaybackFailure.NETWORK.message == PlaybackFailure.MEDIA_SOURCE.message)
    }
    @Test fun malformedPlaylistIsNotReportedAsUnsupportedModule() {
        assertEquals(PlaybackFailure.PLAYLIST_SEGMENT, PlaybackFailure.classify(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED))
    }
    @Test fun decoderFailureIsNotReportedAsNetwork() {
        assertEquals(PlaybackFailure.DECODER, PlaybackFailure.classify(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertEquals(PlaybackFailure.DECODER, PlaybackFailure.classify(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(PlaybackFailure.DECODER, PlaybackFailure.classify(PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED))
        assertEquals(PlaybackFailure.DECODER, PlaybackFailure.classify(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertEquals(
            PlaybackFailure.DECODER,
            PlaybackFailure.classify(
                PlaybackException.ERROR_CODE_UNSPECIFIED,
                causeChain = "ExoPlaybackException>DecoderInitializationException>MediaCodecException"
            )
        )
    }

    @Test fun multiScreenDecoderFailureProducesFriendlyDeviceCapacityWarning() {
        val singleMsg = PlaybackFailure.DECODER.displayMessage("TRT 1", isMultiScreen = false)
        val multiMsg = PlaybackFailure.DECODER.displayMessage("TRT 1", isMultiScreen = true)
        assertEquals("TRT 1 · Video veya ses çözümlenemedi.", singleMsg)
        assertEquals("TRT 1 · Cihaz bu kadar yayını aynı anda desteklemiyor.", multiMsg)
        assertEquals("Cihaz bu kadar yayını aynı anda desteklemiyor.", PlaybackFailure.DECODER.displayMessage(null, isMultiScreen = true))
    }
    @Test fun unknownFailureRemainsUnclassifiedPlayerFailure() {
        assertEquals(PlaybackFailure.PLAYER, PlaybackFailure.classify(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
    @Test fun staleSampleCallbackCannotMatchPublicAttemptOrRetry() {
        val sample = PlaybackAttempt.create("demo", "sample", "https://sample.invalid/video.mp4")
        val real = PlaybackAttempt.create("public", "channel", "https://public.invalid/live.m3u8?token=secret")
        val retry = PlaybackAttempt.create("public", "channel", "https://public.invalid/live.m3u8?token=secret")
        assertFalse(real.accepts(sample.id))
        assertFalse(retry.accepts(real.id))
        assertFalse(real.accepts(null))
        assertTrue(real.accepts(real.id))
        assertEquals(real.uriHash, retry.uriHash)
    }
    @Test fun diagnosticHashPreservesQueryDifferencesWithoutRevealingSecrets() {
        val first = PlaybackAttempt.create("public", "channel", "https://test.invalid/live.m3u8?token=first-secret")
        val second = PlaybackAttempt.create("public", "channel", "https://test.invalid/live.m3u8?token=second-secret")
        assertFalse(first.uriHash == second.uriHash)
        assertEquals(64, first.uriHash.length)
        assertFalse(first.logContext.contains("first-secret"))
        assertFalse(first.logContext.contains("https://"))
    }
}
