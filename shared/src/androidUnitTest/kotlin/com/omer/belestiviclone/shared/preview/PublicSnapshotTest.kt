package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/** The exact acquired public file is checked separately from synthetic parser fixtures. */
class PublicSnapshotTest {
    @Test fun packagedSnapshotHashAndProductionCatalogCountsMatchAcquisition() {
        val file = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }
        val bytes = file.readBytes()
        assertEquals("ff4490b2563117b522e12f751ecf4dca936b180dd01837487fdd3ea8dd170a2c", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val parsed = PublicM3u.parse(bytes.toString(Charsets.UTF_8))
        assertEquals(411, parsed.rawRecords); assertEquals(411, parsed.rows.size); assertTrue(parsed.skipped.isEmpty())
        val n = parsed.rows.size
        val catalog = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(1, it.toLong())) }, List(n) { MembershipId(OpaqueId(2, it.toLong())) }, List(n) { CategoryId(OpaqueId(3, it.toLong())) }))
        assertEquals(408, catalog.size); assertEquals(18, catalog.flatMap { it.groups }.distinct().size)
        assertEquals(411, parsed.rows.size); assertTrue(catalog.all { playbackUri(it) == it.definition!!.address.raw })
        assertTrue(catalog.any { "Çocuk" in it.groups }); assertTrue(catalog.any { "Müzik" in it.groups })
        assertTrue(catalog.none { it.id in PreviewData().favorites })
    }
}
