package com.omer.belestiviclone.shared.preview

import android.content.Context
import com.omer.belestiviclone.shared.catalog.*
import java.security.MessageDigest
import java.io.File

data class R26Request(val admission: R26Admission, val context: PilotContext)
data class R26Prepared(val before: PreviewData, val after: PreviewData, val expected: PreviewRow, val next: PreviewRow)

/** Read-existing cache only. Test fixture creation is a separate debug command. */
suspend fun androidR26Request(context: Context, a: R26Admission): R26Request {
    if (!R26ProfileTransition.allowed(a)) throw TransitionProblem("r26-disabled-profile")
    if (a.session == R26ProfileTransition.normalSession && !r26RuntimeGate(context)) throw TransitionProblem("r26-test-gate")
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val asset = context.assets.open("trlist.m3u").use { it.readBytes() }
    require(hash(asset) == VerifiedDefinitionTransition.seedSha256) { "seed-binding" }
    require(hash(context.assets.open("r21-transition.private.json").use { it.readBytes() }) == ApprovedR21.manifest.digest) { "manifest-binding" }
    require(hash(context.assets.open("r26-transition.private.json").use { it.readBytes() }) == R26ManifestBinding.sha256) { "r26-manifest-binding" }
    val cache = if (a.session == R26ProfileTransition.normalSession) "public-trlist-catalog-v1" else "${R26ProfileTransition.testSession}-catalog"
    val seed = AndroidPublicCatalog(context, cache).readExisting()
    val parsed = PublicM3u.parse(asset.toString(Charsets.UTF_8)); val n = parsed.rows.size
    val reference = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(1, it.toLong())) },
        List(n) { MembershipId(OpaqueId(2, it.toLong())) }, List(n) { CategoryId(OpaqueId(3, it.toLong())) }))
    require(seed.map { it.copy(id = OpaqueId(0, 0)) } == reference.map { it.copy(id = OpaqueId(0, 0)) }) { "cache-seed-mismatch" }
    val source = SourceOperations { throw TransitionProblem("unexpected-allocation") }
        .parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, asset.toString(Charsets.UTF_8))
    return R26Request(a, PilotContext(requireNotNull(a.session), hash(asset), seed, source))
}

@Suppress("DEPRECATION")
fun r26RuntimeProof(context: Context): String = PreviewCodec.pack(listOf(R26ManifestBinding.sha256,
    R26OldReaderBinding.dexSha256, context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()))
fun r26RuntimeGate(context: Context): Boolean = listOf("suite", "media").all {
    val file = File(context.filesDir, "${R26ProfileTransition.testSession}-$it-proof")
    file.isFile && file.readText() == r26RuntimeProof(context)
}
