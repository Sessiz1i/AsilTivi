package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.PreviewData
import com.omer.belestiviclone.shared.preview.PreviewStore
import com.omer.belestiviclone.shared.preview.StalkerConfig
import com.omer.belestiviclone.shared.preview.StalkerPortalClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettingsPhaseP5Test {

    private class Store(var value: PreviewData = PreviewData()) : PreviewStore {
        override suspend fun load(): PreviewData = value
        override suspend fun save(data: PreviewData) { value = data }
    }

    @Test
    fun testRadioBackgroundPreferenceDefaultsAndUpdates() {
        val initialData = PreviewData()
        assertEquals("Varsayılan", initialData.pref("radioBg"))

        val neonData = initialData.withPref("radioBg", "Koyu Neon")
        assertEquals("Koyu Neon", neonData.pref("radioBg"))

        val woodData = initialData.withPref("radioBg", "Klasik Ahşap")
        assertEquals("Klasik Ahşap", woodData.pref("radioBg"))
    }

    @Test
    fun testStalkerConfigAndCustomHeaders() {
        val config = StalkerConfig(
            portalUrl = "http://mag-portal.tv:8080/c/",
            macAddress = "00:1A:79:AB:CD:EF",
            serialNumber = "SN12345678",
            deviceId = "DEV-ID-01",
            deviceId2 = "DEV-ID-02"
        )
        assertEquals("SN12345678", config.serialNumber)
        assertEquals("DEV-ID-01", config.deviceId)
        assertEquals("DEV-ID-02", config.deviceId2)

        val headers = StalkerPortalClient.buildHeaders(
            macAddress = config.macAddress,
            serialNumber = config.serialNumber,
            deviceId = config.deviceId,
            deviceId2 = config.deviceId2
        )
        assertNotNull(headers["Cookie"])
        assertEquals("SN12345678", headers["X-Serial-Number"])
        assertEquals("DEV-ID-01", headers["X-Device-Id"])
        assertEquals("DEV-ID-02", headers["X-Device-Id2"])
    }

    @Test
    fun testPreviewStateStalkerFieldsAndReset() = runBlocking {
        val s = PreviewState(Store(), this)
        s.load()
        s.formSerial = "SERIAL-99"
        s.formDeviceId = "DEVICE-88"
        s.formDeviceId2 = "DEVICE-77"
        assertEquals("SERIAL-99", s.formSerial)

        s.dismiss()
        assertEquals("", s.formSerial)
        assertEquals("", s.formDeviceId)
        assertEquals("", s.formDeviceId2)
    }

    @Test
    fun testM3uEmptyUrlTriggersSourceErrorDialog() = runBlocking {
        val s = PreviewState(Store(), this)
        s.load()
        s.dialog = "m3u"
        s.formName = "Test Listem"
        s.formAddress = "   " // boş URL

        s.saveSource()

        assertEquals("sourceError", s.dialog)
        assertEquals("Kaynağa bağlanılamadı", s.sourceErrorDialogTitle)
        assertEquals("Lütfen geçerli bir M3U çalma listesi adresi girin.", s.sourceErrorDialogMessage)
        assertEquals("m3u", s.lastSourceDialog)
    }

    @Test
    fun testSettingsOptionHeadingsContainRadioBg() {
        assertEquals("RADYO ARKA PLANI", optionHeading("radioBg"))
    }
}
