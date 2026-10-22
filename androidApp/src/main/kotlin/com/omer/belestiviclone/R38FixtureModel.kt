package com.omer.belestiviclone

import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*

object R38FixtureModel {
    fun channel(rows: List<ChannelContribution>, rawIndex: Long, rawProviderHint: String,
        old: PlaybackDefinition, new: PlaybackDefinition, testId: OpaqueId): PreviewChannel {
        require(VerifiedDefinitionTransition.portable(old) && VerifiedDefinitionTransition.portable(new))
        require(rows.count { it.definition == old } == 1)
        val row = requireNotNull(rows.singleOrNull { it.sourceId == PublicM3u.source && it.rowOrder == rawIndex })
        // External research metadataId is not the source's optional tvg-id/providerHint.
        require(row.definition == old && row.providerHint.orEmpty() == rawProviderHint)
        require(rows.none { it.definition == new || ComparisonV1.playbackKey(it.definition) == ComparisonV1.playbackKey(new) })
        val label = (row.category as? CategoryLabel.Exact)?.text ?: "Kategorisiz"
        return PreviewChannel(testId, row.name.orEmpty(), label, row.logo.orEmpty(), definition = new,
            providerId = row.providerHint.orEmpty())
    }
}
