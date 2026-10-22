package com.omer.belestiviclone

import kotlin.test.*

class StreamTypeTest {
    @Test fun redirectEndpointAndQueryAreNotMistakenForPathExtensions() {
        assertTrue(StreamType.needsProbe("http://source.invalid/live?target=stream.m3u8&token=secret", null))
        assertFalse(StreamType.needsProbe("https://source.invalid/live.m3u8?token=secret", null))
        assertFalse(StreamType.needsProbe("https://source.invalid/audio.mp3", null))
        assertFalse(StreamType.needsProbe("https://source.invalid/live", StreamType.HLS))
    }
    @Test fun hlsResponseSelectsHlsButRadioHtmlAndPlainM3uDoNot() {
        assertEquals(StreamType.HLS, StreamType.fromResponse("application/vnd.apple.mpegurl; charset=utf-8", byteArrayOf()))
        assertEquals(StreamType.HLS, StreamType.fromResponse("text/plain", "\uFEFF#EXTM3U\n#EXT-X-TARGETDURATION:6".toByteArray()))
        assertNull(StreamType.fromResponse("audio/mpeg", byteArrayOf(0xff.toByte(), 0xfb.toByte())))
        assertNull(StreamType.fromResponse("text/html", "<html>Forbidden</html>".toByteArray()))
        assertNull(StreamType.fromResponse("text/plain", "#EXTM3U\n#EXTINF:-1,Radio\nhttp://example.invalid".toByteArray()))
    }
}
