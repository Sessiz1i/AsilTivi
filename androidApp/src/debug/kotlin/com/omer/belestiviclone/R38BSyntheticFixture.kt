package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

/** Static trlist and generated synthetic identities only; never reads a device profile/cache. */
class R38BSyntheticFixture(text: String, val session: String) {
    private var seq = 50000L
    private fun allocate() = OpaqueId(38016, seq++)
    val ops = SourceOperations(::allocate)
    val seed = PublicM3u.catalog(PublicM3u.parse(text), IdentitySupply(
        List(411) { ChannelId(OpaqueId(38016, it.toLong())) },
        List(411) { MembershipId(OpaqueId(38017, it.toLong())) },
        List(411) { CategoryId(OpaqueId(38018, it.toLong())) }))
    val context = PilotContext(session, VerifiedDefinitionTransition.seedSha256, seed,
        ops.parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, text))
    val admission = R38BAdmission(true, 2, "$session.db", session, "apply")
    val request = R38BRequest(admission, context)
    val target = R38BProfileTransition.targetId
    fun base(): PreviewData {
        check(seed.single { it.definition == R38BProfileTransition.entry.old }.id == target)
        val data = PreviewData(channels = seed, profileId = PublicM3u.profile,
            sources = listOf(R23ProfileTransition.defaultLabel), favorites = listOf(target, seed.last().id), history = listOf(target),
            hidden = setOf(seed.last().id), hiddenCategories = setOf("Spor"), categories = seed.flatMap { it.groups }.distinct())
            .withPref("lastChannel", PreviewCodec.id(target)).withPref("history", "Kapalı").withPref("startup", "Kapalı")
        val initial = data.copy(library = ops.bootstrap(data).copy(records = listOf(context.seedSource)))
        val r23 = VerifiedDefinitionTransition.apply(initial, context.copy(session = R23ProfileTransition.testSession), ApprovedR21.manifest, ::allocate)
        val c26 = context.copy(session = R26ProfileTransition.testSession)
        val r26 = R26ProfileTransition.prepare(r23, c26, R26Admission(true, 2, "${c26.session}.db", c26.session, "apply"))
        val c28 = context.copy(session = R28ProfileTransition.testSession)
        return R28ProfileTransition.prepare(r26, c28, R28Admission(true, 2, "${c28.session}.db", c28.session, "apply"))
    }
    fun intervening(data: PreviewData) = data.copy(favorites = data.favorites.reversed(), history = listOf(seed.last().id, target),
        hidden = setOf(target), categories = data.categories.reversed(), hiddenCategories = setOf(data.categories.first()))
        .withPref("lastChannel", PreviewCodec.id(seed.last().id)).withPref("format", "Sığdır (Fit)").withPref("opacity", "49")
}
