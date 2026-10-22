package com.omer.belestiviclone.shared.catalog

data class ChannelContribution(
    val sourceId: SourceId,
    val rowOrder: Long,
    val definition: PlaybackDefinition,
    val category: CategoryLabel = CategoryLabel.Uncategorized,
    val name: String? = null,
    val logo: String? = null,
    val providerHint: String? = null,
    val tvgName: String = "",
) {
    override fun toString(): String = "ChannelContribution([redacted])"
}

enum class IdentityDecision { REUSED_EXACT, REUSED_SOURCE_SCOPED_EXACT, NEW, AMBIGUOUS, BLOCKED }

data class PlannedContribution(
    val input: ChannelContribution,
    val membership: MembershipRecord,
    val decision: IdentityDecision,
)

/** The original record is retained even when it has no current catalog representation. */
data class ChannelReference(
    val record: ChannelRecord,
    val catalogRepresentationId: ChannelId?,
    val hasUsableActiveMembership: Boolean,
)

data class MembershipReference(val record: MembershipRecord, val isCurrentInPlan: Boolean)

data class CatalogRepresentation(
    val id: ChannelId,
    val referenceIds: List<ChannelId>,
    val contributions: List<PlannedContribution>,
    val categoryIds: List<CategoryId>,
    val name: String?,
    val logo: String?,
    val hasUsableActiveMembership: Boolean,
) {
    override fun toString(): String = "CatalogRepresentation([redacted])"
}

data class MappingPlan(
    val catalog: List<CatalogRepresentation>,
    val channels: List<ChannelReference>,
    val memberships: List<MembershipReference>,
    val categories: List<CategoryRecord>,
    val consumedChannelIds: Int,
    val consumedMembershipIds: Int,
    val consumedCategoryIds: Int,
)

/**
 * Pure replacement plan over the complete desired contribution set (including passive sources).
 * No DB/user preference writes, source selection, provider matching, or playback effects.
 * hasUsableActiveMembership only describes supplied definitions in the effective set; it is
 * neither a successful media/network check nor permission to start a player.
 */
object CatalogPlanner {
    fun plan(
        old: CatalogSnapshot,
        incoming: List<ChannelContribution>,
        identities: IdentitySupply,
    ): MappingPlan {
        validate(old, incoming, identities)
        val sources = old.sources.associateBy { it.id }
        val ordered = incoming.sortedWith(compareBy<ChannelContribution>(
            { if (sources.getValue(it.sourceId).inActiveSet) 0 else 1 },
            { sources.getValue(it.sourceId).role.ordinal },
            { it.sourceId.value }, { it.rowOrder },
        ))
        val channels = old.channels.toMutableList()
        val categories = old.categories.sortedWith(compareBy({ it.firstSeen }, { it.id.value })).toMutableList()
        val oldKeys = old.channels.associate { it.id to ComparisonV1.playbackKey(it.definition) }
        val categoryByLabel = categories.associateBy { it.label }.toMutableMap()
        var channelCount = 0
        var membershipCount = 0
        var categoryCount = 0
        var nextChannelOrder = nextOrder(channels.map { it.firstSeen })
        var nextCategoryOrder = nextOrder(categories.map { it.firstSeen })

        fun newChannel(definition: PlaybackDefinition): ChannelId {
            val id = identities.channels.getOrNull(channelCount++)
            require(id != null) { "Insufficient channel identity supply" }
            channels += ChannelRecord(id, definition, nextChannelOrder++)
            return id
        }

        fun categoryId(label: CategoryLabel): CategoryId = categoryByLabel.getOrPut(label) {
            val id = identities.categories.getOrNull(categoryCount++)
            require(id != null) { "Insufficient category identity supply" }
            CategoryRecord(id, label, nextCategoryOrder++).also { categories += it }
        }.id

        // A null key never groups with another row, even if its raw input looks identical.
        val groups = mutableListOf<MutableList<ChannelContribution>>()
        val groupByKey = mutableMapOf<PlaybackKey, MutableList<ChannelContribution>>()
        ordered.forEach { row ->
            val key = ComparisonV1.playbackKey(row.definition)
            val group = if (key == null) mutableListOf<ChannelContribution>().also { groups += it }
            else groupByKey.getOrPut(key) { mutableListOf<ChannelContribution>().also { groups += it } }
            group += row
        }
        val usedMemberships = mutableSetOf<MembershipId>()
        val planned = mutableListOf<PlannedContribution>()
        val catalog = groups.map { rows ->
            val key = ComparisonV1.playbackKey(rows.first().definition)
            val candidates = if (key == null) emptyList() else old.channels.filter { oldKeys[it.id] == key }
            var freshId: ChannelId? = null
            val groupContributions = rows.map { row ->
                // Existing distinct IDs can continue only with unique SAME-source provenance
                // AND full equality. A source/provider/name/row hint alone is never enough.
                val scoped = if (candidates.size > 1) candidates.filter { channel ->
                    old.memberships.any { it.isCurrent && it.sourceId == row.sourceId && it.channelId == channel.id }
                } else emptyList()
                val candidate = candidates.singleOrNull() ?: scoped.singleOrNull()
                val decision = when {
                    key == null -> IdentityDecision.BLOCKED
                    candidates.size == 1 -> IdentityDecision.REUSED_EXACT
                    candidate != null -> IdentityDecision.REUSED_SOURCE_SCOPED_EXACT
                    candidates.size > 1 -> IdentityDecision.AMBIGUOUS
                    else -> IdentityDecision.NEW
                }
                val channelId = candidate?.id ?: freshId ?: newChannel(row.definition).also { freshId = it }
                val category = categoryId(row.category)
                val membershipCandidates = old.memberships.filter {
                    it.sourceId == row.sourceId && it.channelId == channelId && it.categoryId == category
                }
                val retained = membershipCandidates.singleOrNull()?.takeIf { it.id !in usedMemberships }
                val membership = retained ?: run {
                    val id = identities.memberships.getOrNull(membershipCount++)
                    require(id != null) { "Insufficient membership identity supply" }
                    MembershipRecord(id, channelId, row.sourceId, category)
                }
                usedMemberships += membership.id
                PlannedContribution(row, membership, decision).also { planned += it }
            }
            val referenceIds = groupContributions.map { it.membership.channelId }.distinct()
            val records = channels.associateBy { it.id }
            val representative = groupContributions.minWith(compareBy<PlannedContribution>(
                { if (sources.getValue(it.input.sourceId).inActiveSet) 0 else 1 },
                { sources.getValue(it.input.sourceId).role.ordinal },
                { records.getValue(it.membership.channelId).firstSeen },
                { it.membership.channelId.value },
            )).membership.channelId
            // Only effective contributions may supply active catalog metadata/categories.
            val active = groupContributions.filter { sources.getValue(it.input.sourceId).inActiveSet }
            val visible = active.ifEmpty { groupContributions }
            CatalogRepresentation(
                representative, referenceIds, groupContributions,
                visible.map { it.membership.categoryId }.distinct(),
                visible.firstOrNull { !it.input.name.isNullOrEmpty() }?.input?.name,
                visible.firstOrNull { !it.input.logo.isNullOrEmpty() }?.input?.logo,
                key != null && active.isNotEmpty(),
            )
        }
        val representationByReference = catalog.flatMap { item -> item.referenceIds.map { it to item } }.toMap()
        val channelReferences = channels.sortedWith(compareBy({ it.firstSeen }, { it.id.value })).map { record ->
            val representation = representationByReference[record.id]
            ChannelReference(record, representation?.id, representation?.hasUsableActiveMembership == true)
        }
        val newMemberships = planned.map { it.membership }.filter { entry -> old.memberships.none { it.id == entry.id } }
        return MappingPlan(catalog, channelReferences,
            old.memberships.map { MembershipReference(it, it.id in usedMemberships) } +
                newMemberships.map { MembershipReference(it, true) },
            categories.toList(), channelCount, membershipCount, categoryCount)
    }

    private fun nextOrder(values: List<Long>): Long {
        val max = values.maxOrNull() ?: -1
        require(max < Long.MAX_VALUE) { "Creation order exhausted" }
        return max + 1
    }

    private fun validate(old: CatalogSnapshot, incoming: List<ChannelContribution>, supply: IdentitySupply) {
        fun <T> unique(values: List<T>) = require(values.distinct().size == values.size) { "Duplicate identity or scope" }
        unique(old.sources.map { it.id })
        unique(old.channels.map { it.id })
        unique(old.memberships.map { it.id })
        unique(old.categories.map { it.id })
        unique(old.categories.map { it.label })
        unique(old.channels.map { it.id } + supply.channels)
        unique(old.memberships.map { it.id } + supply.memberships)
        unique(old.categories.map { it.id } + supply.categories)
        unique(incoming.map { it.sourceId to it.rowOrder })
        val sources = old.sources.associateBy { it.id }
        val channels = old.channels.map { it.id }.toSet()
        val categories = old.categories.map { it.id }.toSet()
        old.sources.filter { it.role == SourceRole.SUPPLEMENT }.also { supplements ->
            unique(supplements.map { it.parentSourceId })
            supplements.forEach {
                val parent = sources[it.parentSourceId]
                require(parent?.role == SourceRole.MAIN && (!it.inActiveSet || parent.inActiveSet)) { "Invalid supplement scope" }
            }
        }
        require(old.sources.count { it.role == SourceRole.MAIN && it.inActiveSet } <= 1) { "Invalid active source scope" }
        require(old.channels.all { it.firstSeen >= 0 } && old.categories.all { it.firstSeen >= 0 }) { "Invalid creation order" }
        require(incoming.all { it.sourceId in sources && it.rowOrder >= 0 }) { "Invalid contribution scope" }
        require(old.memberships.all { it.sourceId in sources && it.channelId in channels && it.categoryId in categories }) {
            "Invalid membership reference"
        }
        require((old.channels.maxOfOrNull { it.firstSeen } ?: -1) <= Long.MAX_VALUE - incoming.size - 1 &&
            (old.categories.maxOfOrNull { it.firstSeen } ?: -1) <= Long.MAX_VALUE - incoming.size - 1) { "Creation order exhausted" }
    }
}
