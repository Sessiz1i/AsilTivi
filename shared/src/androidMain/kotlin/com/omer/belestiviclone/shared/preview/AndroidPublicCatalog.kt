package com.omer.belestiviclone.shared.preview

import android.content.Context
import android.util.AtomicFile
import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opaque random IDs allocated once, stored atomically with the last successful catalog. No migration. */
class AndroidPublicCatalog(private val context: Context, private val cacheName: String = "public-trlist-catalog-v1") {
    suspend fun load(): List<PreviewChannel> = withContext(Dispatchers.IO) {
        val file = AtomicFile(File(context.filesDir, cacheName))
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) return@withContext decode(file.readFully().toString(Charsets.UTF_8)).also { logBinding("cache", it) }
        val text = context.assets.open("trlist.m3u").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = PublicM3u.parse(text) { raw -> runCatching { URI(PublicM3u.url).resolve(raw).toASCIIString() }.getOrNull() }
        require(parsed.rows.isNotEmpty()) { "Empty public catalog" }
        fun opaque() = UUID.randomUUID().let { OpaqueId(it.mostSignificantBits, it.leastSignificantBits) }
        val count = parsed.rows.size
        val channels = PublicM3u.catalog(parsed, IdentitySupply(List(count) { ChannelId(opaque()) }, List(count) { MembershipId(opaque()) }, List(count) { CategoryId(opaque()) }))
        val output = file.startWrite()
        try { output.write(encode(channels).toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        channels.also { logBinding("asset", it) }
    }
    private fun logBinding(origin: String, channels: List<PreviewChannel>) {
        val uris = channels.mapNotNull { it.definition?.address?.raw }.distinct().sorted()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(uris.joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        android.util.Log.i("BelesCatalog", "origin=$origin channels=${channels.size} distinctUris=${uris.size} uriSetSha256=$hash")
    }
    private fun encode(channels: List<PreviewChannel>) = PreviewCodec.pack(channels.map { ch ->
        val d = ch.definition!!
        PreviewCodec.pack(listOf(PreviewCodec.id(ch.id), ch.name, ch.logo, PreviewCodec.pack(ch.groups), ch.providerId, d.address.raw,
            PreviewCodec.pack(d.headers.flatMap { listOf(it.name, it.value) }), PreviewCodec.pack(d.unknownRequiredDirectives)))
    })
    private fun decode(text: String) = PreviewCodec.unpack(text).map { value ->
        val fields = PreviewCodec.unpack(value); require(fields.size == 8)
        val groups = PreviewCodec.unpack(fields[3]); require(groups.isNotEmpty())
        val headers = PreviewCodec.unpack(fields[6]); require(headers.size % 2 == 0)
        PreviewChannel(PreviewCodec.id(fields[0]), fields[1], groups.first(), fields[2], definition = PlaybackDefinition(Address.fromForm(fields[5]),
            headers.chunked(2).map { Header(it[0], it[1]) }, unknownRequiredDirectives = PreviewCodec.unpack(fields[7])), groups = groups, providerId = fields[4])
    }
}
