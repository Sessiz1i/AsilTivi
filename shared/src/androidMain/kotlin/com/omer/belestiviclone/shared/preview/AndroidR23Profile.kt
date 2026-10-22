package com.omer.belestiviclone.shared.preview

import android.content.Context
import com.omer.belestiviclone.shared.catalog.*
import java.security.MessageDigest

data class R23Request(val admission: R23Admission, val context: PilotContext)
data class R23Prepared(val before: PreviewData, val after: PreviewData, val expected: PreviewRow, val next: PreviewRow)

/** Existing-cache-only evidence; neither load() nor an initializer is called. */
suspend fun androidR23Request(context: Context, admission: R23Admission): R23Request {
    if (!R23ProfileTransition.allowed(admission)) throw TransitionProblem("r23-disabled-profile")
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val asset = context.assets.open("trlist.m3u").use { it.readBytes() }
    require(hash(asset) == VerifiedDefinitionTransition.seedSha256) { "seed-binding" }
    require(hash(context.assets.open("r21-transition.private.json").use { it.readBytes() }) == ApprovedR21.manifest.digest) { "manifest-binding" }
    val cache = if (admission.session == R23ProfileTransition.normalSession) "public-trlist-catalog-v1" else "${R23ProfileTransition.testSession}-catalog"
    val seed = AndroidPublicCatalog(context, cache).readExisting()
    val parsed = PublicM3u.parse(asset.toString(Charsets.UTF_8))
    val n = parsed.rows.size
    val reference = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(1, it.toLong())) },
        List(n) { MembershipId(OpaqueId(2, it.toLong())) }, List(n) { CategoryId(OpaqueId(3, it.toLong())) }))
    require(seed.map { it.copy(id = OpaqueId(0, 0)) } == reference.map { it.copy(id = OpaqueId(0, 0)) }) { "cache-seed-mismatch" }
    val source = SourceOperations { throw TransitionProblem("unexpected-allocation") }.parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, asset.toString(Charsets.UTF_8))
    return R23Request(admission, PilotContext(requireNotNull(admission.session), hash(asset), seed, source))
}
