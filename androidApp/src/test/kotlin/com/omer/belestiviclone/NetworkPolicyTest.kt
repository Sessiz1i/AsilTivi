package com.omer.belestiviclone

import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*

class NetworkPolicyTest {
    @Test fun cleartextIsLimitedToExactCatalogueHostsAndObservedRedirect() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/network_security_config.xml"))
        val base = doc.getElementsByTagName("base-config").item(0) as org.w3c.dom.Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val nodes = doc.getElementsByTagName("domain")
        val allowed = (0 until nodes.length).map {
            val element = nodes.item(it) as org.w3c.dom.Element
            assertEquals("false", element.getAttribute("includeSubdomains"))
            element.textContent
        }.toSet()
        val catalogue = File("src/main/assets/TiviListe.m3u").readLines().filter { it.trim().startsWith("http://") }
            .map { URI(it.trim()).host }.toSet()
        val radioHttpHosts = setOf("46.20.3.201", "46.20.3.204", "46.20.3.229", "46.20.7.126", "turkmedya.radyotvonline.com")
        assertEquals(catalogue + "dogusdyg-star.lg.mncdn.com" + radioHttpHosts, allowed)
        assertEquals(0, doc.getElementsByTagName("trust-anchors").length)
        assertEquals(0, doc.getElementsByTagName("debug-overrides").length)
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
    }
}

