package com.omer.belestiviclone

import kotlin.test.*

class CaptionGeometryTest {
    @Test fun bottomCuesReserveMeasuredInfoCardWithoutChangingVideoHeight() {
        assertEquals(710f,captionBottomTop(900,100f,0),.01f)
        val infoTop=580
        val top=captionBottomTop(900,100f,900-infoTop)
        assertEquals(422f,top,.01f);assertTrue(top+100 <= infoTop)
        assertEquals(900,captionAvailableHeight(900,0))
    }
    @Test fun negativeCaptionLinesZeroViewportAndOversizedInsetsStayBounded() {
        assertEquals(480f,captionBottomTop(900,100f,320,-1f,52f))
        assertEquals(428f,captionBottomTop(900,100f,320,-2f,52f))
        assertEquals(0f,captionBottomTop(0,100f,0))
        assertEquals(0f,captionBottomTop(900,100f,1800))
        assertEquals(710f,captionBottomTop(900,100f,-100))
    }
}
