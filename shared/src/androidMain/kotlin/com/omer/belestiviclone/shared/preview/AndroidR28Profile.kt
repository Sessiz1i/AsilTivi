package com.omer.belestiviclone.shared.preview

import android.content.Context
import com.omer.belestiviclone.shared.catalog.*
import java.security.MessageDigest
import java.io.File

data class R28Request(val admission: R28Admission, val context: PilotContext)
data class R28Prepared(val before: PreviewData, val after: PreviewData, val expected: PreviewRow, val next: PreviewRow)

/** Read-existing cache only. Test fixture creation is a separate debug command. */
suspend fun androidR28Request(context: Context, a: R28Admission): R28Request {
    if (!R28ProfileTransition.allowed(a)) throw TransitionProblem("r28-disabled-profile")
    if (a.session == R28ProfileTransition.normalSession && !r28RuntimeGate(context)) throw TransitionProblem("r28-test-gate")
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val asset = context.assets.open("trlist.m3u").use { it.readBytes() }
    require(hash(asset) == VerifiedDefinitionTransition.seedSha256) { "seed-binding" }
    require(hash(context.assets.open("r21-transition.private.json").use { it.readBytes() }) == ApprovedR21.manifest.digest) { "manifest-binding" }
    require(hash(context.assets.open("r28-transition.private.json").use { it.readBytes() }) == R28ManifestBinding.sha256) { "r28-manifest-binding" }
    require(hash(context.assets.open("r26-transition.private.json").use { it.readBytes() }) == R26ManifestBinding.sha256) { "r26-manifest-binding" }
    val cache = if (a.session == R28ProfileTransition.normalSession) "public-trlist-catalog-v1" else "${R28ProfileTransition.testSession}-catalog"
    val seed = AndroidPublicCatalog(context, cache).readExisting()
    val parsed = PublicM3u.parse(asset.toString(Charsets.UTF_8)); val n = parsed.rows.size
    val reference = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(1, it.toLong())) },
        List(n) { MembershipId(OpaqueId(2, it.toLong())) }, List(n) { CategoryId(OpaqueId(3, it.toLong())) }))
    require(seed.map { it.copy(id = OpaqueId(0, 0)) } == reference.map { it.copy(id = OpaqueId(0, 0)) }) { "cache-seed-mismatch" }
    val source = SourceOperations { throw TransitionProblem("unexpected-allocation") }
        .parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, asset.toString(Charsets.UTF_8))
    return R28Request(a, PilotContext(requireNotNull(a.session), hash(asset), seed, source))
}

@Suppress("DEPRECATION")
fun r28RuntimeProof(context: Context): String = PreviewCodec.pack(listOf(R28ProfileTransition.approval, ApprovedR21.manifest.digest, R26ManifestBinding.sha256, R28ManifestBinding.sha256,
    R28OldReaderBinding.dexSha256, context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()))
fun r28RuntimeGate(context: Context): Boolean = listOf("suite", "media").all {
    val file = File(context.filesDir, "${R28ProfileTransition.testSession}-$it-proof")
    file.isFile && file.readText() == r28RuntimeProof(context)
}
