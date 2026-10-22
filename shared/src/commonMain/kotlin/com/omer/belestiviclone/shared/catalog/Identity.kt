package com.omer.belestiviclone.shared.catalog

/** Opaque 128 bits supplied by a future platform allocator, never derived from metadata. */
data class OpaqueId(val high: Long, val low: Long) : Comparable<OpaqueId> {
    override fun compareTo(other: OpaqueId): Int {
        val highOrder = high.toULong().compareTo(other.high.toULong())
        return if (highOrder != 0) highOrder else low.toULong().compareTo(other.low.toULong())
    }
}

data class SourceId(val value: OpaqueId)
data class ChannelId(val value: OpaqueId)
data class MembershipId(val value: OpaqueId)
data class CategoryId(val value: OpaqueId)

enum class SourceRole { MAIN, SUPPLEMENT }

/** Effective membership scope is supplied by the caller; this model never selects a source. */
data class SourceRecord(
    val id: SourceId,
    val role: SourceRole,
    val inActiveSet: Boolean,
    val parentSourceId: SourceId? = null,
) {
    init {
        require((role == SourceRole.SUPPLEMENT) == (parentSourceId != null)) { "Invalid source role" }
    }
}

sealed interface CategoryLabel {
    data class Exact(val text: String) : CategoryLabel {
        init { require(text.isNotEmpty()) { "Empty category uses Uncategorized" } }
    }

    data object Uncategorized : CategoryLabel

    companion object {
        fun fromRaw(text: String): CategoryLabel = if (text.isEmpty()) Uncategorized else Exact(text)
    }
}

data class CategoryRecord(val id: CategoryId, val label: CategoryLabel, val firstSeen: Long)
data class ChannelRecord(val id: ChannelId, val definition: PlaybackDefinition, val firstSeen: Long)

/** Repeated rows remain distinct memberships; row position is never a persistent identity. */
data class MembershipRecord(
    val id: MembershipId,
    val channelId: ChannelId,
    val sourceId: SourceId,
    val categoryId: CategoryId,
    val isCurrent: Boolean = true,
)

data class CatalogSnapshot(
    val sources: List<SourceRecord> = emptyList(),
    val channels: List<ChannelRecord> = emptyList(),
    val memberships: List<MembershipRecord> = emptyList(),
    val categories: List<CategoryRecord> = emptyList(),
)

/** Values, rather than a mutable/random generator, keep planning deterministic and side effect free. */
data class IdentitySupply(
    val channels: List<ChannelId> = emptyList(),
    val memberships: List<MembershipId> = emptyList(),
    val categories: List<CategoryId> = emptyList(),
)
