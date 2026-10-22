package com.omer.belestiviclone.shared.preview

import android.content.Context
import com.omer.belestiviclone.shared.catalog.*
import java.security.MessageDigest

/** Caller supplies the exact debug-only isolated session. Neither the real cache nor DB is read. */
suspend fun androidR21Pilot(context: Context, session: String, catalog: AndroidPublicCatalog, rollback: Boolean, legacyExit: Boolean = false): PilotRequest {
    require(session in setOf(VerifiedDefinitionTransition.session, VerifiedDefinitionTransition.compatibilitySession))
    require(!legacyExit || session == VerifiedDefinitionTransition.compatibilitySession)
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val asset = context.assets.open("trlist.m3u").use { it.readBytes() }
    require(hash(asset) == VerifiedDefinitionTransition.seedSha256)
    val manifest = context.assets.open("r21-transition.private.json").use { it.readBytes() }
    require(hash(manifest) == ApprovedR21.manifest.digest)
    val seed = catalog.load()
    val parsed = PublicM3u.parse(asset.toString(Charsets.UTF_8))
    val n = parsed.rows.size
    val reference = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(1, it.toLong())) },
        List(n) { MembershipId(OpaqueId(2, it.toLong())) }, List(n) { CategoryId(OpaqueId(3, it.toLong())) }))
    require(seed.map { it.copy(id = OpaqueId(0, 0)) } == reference.map { it.copy(id = OpaqueId(0, 0)) }) { "cache-seed-mismatch" }
    val source = SourceOperations { error("No allocation during parse") }.parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, asset.toString(Charsets.UTF_8))
    return PilotRequest(PilotContext(session, hash(asset), seed, source), rollback, legacyExit)
}
