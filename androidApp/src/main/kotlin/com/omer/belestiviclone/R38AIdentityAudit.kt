package com.omer.belestiviclone

import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*

/** Pure audit only: no allocator, store or mutation API. */
object R38AIdentityAudit {
    fun target(data: PreviewData, cache: List<PreviewChannel>, rows: List<ChannelContribution>,
        old: PlaybackDefinition, new: PlaybackDefinition): OpaqueId {
        fun must(value: Boolean, reason: String) = require(value) { reason }
        val oldKey = requireNotNull(ComparisonV1.playbackKey(old)) { "old-comparison" }
        val newKey = requireNotNull(ComparisonV1.playbackKey(new)) { "new-comparison" }
        must(oldKey != newKey, "unchanged-comparison")
        val raw = rows.single { it.sourceId == PublicM3u.source && it.rowOrder == 38L }
        must(raw.definition == old && raw.providerHint.orEmpty().isEmpty(), "raw38-full-definition-provider")
        must(rows.count { ComparisonV1.playbackKey(it.definition) == oldKey } == 1, "seed-old-multiplicity")
        must(rows.none { ComparisonV1.playbackKey(it.definition) == newKey }, "seed-new-collision")
        val original = cache.single { it.definition == old }
        must(original.providerId.isEmpty() && original.name == raw.name && original.logo == raw.logo &&
            original.groups == listOf((raw.category as? CategoryLabel.Exact)?.text ?: "Kategorisiz"), "cache-raw-metadata")
        must(cache.count { it.definition?.let(ComparisonV1::playbackKey) == oldKey } == 1, "cache-old-multiplicity")
        must(cache.none { it.definition?.let(ComparisonV1::playbackKey) == newKey }, "cache-new-collision")
        val lib = requireNotNull(data.library) { "missing-library" }
        must(lib.r33 == null && lib.active == PublicM3u.source && lib.main.kind == SourceKind.DEFAULT &&
            lib.main.parent == null && !lib.main.deleted && lib.main.address == PublicM3u.url, "main-identity")
        must(data.channels.size == 408 && cache.size == 408 && data.channels.map { it.id } == cache.map { it.id } &&
            data.channels.map { it.id }.distinct().size == 408, "408-id-order")
        must(data.channels.single { it.id == original.id } == original, "preview-old-metadata")
        must(data.channels.count { it.definition?.let(ComparisonV1::playbackKey) == oldKey } == 1 &&
            data.channels.none { it.definition?.let(ComparisonV1::playbackKey) == newKey }, "preview-collision")
        must(lib.snapshot.channels.size == 408 && lib.snapshot.channels.map { it.id.value }.toSet() == cache.map { it.id }.toSet(), "snapshot-ids")
        must(lib.snapshot.channels.single { it.id.value == original.id }.definition == old &&
            lib.snapshot.channels.count { ComparisonV1.playbackKey(it.definition) == oldKey } == 1 &&
            lib.snapshot.channels.none { ComparisonV1.playbackKey(it.definition) == newKey }, "snapshot-definition-collision")
        must(lib.records.map { it.id }.distinct().size == lib.records.size, "duplicate-source")
        val all = lib.records.flatMap { source -> source.rows.map { source to it } }
        must(all.all { (s,r) -> s.id == r.sourceId && ComparisonV1.playbackKey(r.definition) != null }, "source-row-identity-codec")
        val oldRows = all.filter { ComparisonV1.playbackKey(it.second.definition) == oldKey }
        must(oldRows.size == 1 && oldRows.single().first.id == PublicM3u.source && oldRows.single().second == raw, "saved-extra-deleted-old-sharing")
        must(all.none { ComparisonV1.playbackKey(it.second.definition) == newKey }, "saved-extra-deleted-new-collision")
        val memberships = lib.snapshot.memberships.filter { it.channelId.value == original.id }
        must(memberships.isNotEmpty() && memberships.all { it.isCurrent && it.sourceId == PublicM3u.source &&
            lib.snapshot.categories.any { category -> category.id == it.categoryId && category.label == raw.category } }, "target-memberships")
        must(lib.snapshot.sources.map { it.id }.toSet() == lib.records.map { it.id }.toSet(), "snapshot-source-identity")
        return original.id
    }
}
