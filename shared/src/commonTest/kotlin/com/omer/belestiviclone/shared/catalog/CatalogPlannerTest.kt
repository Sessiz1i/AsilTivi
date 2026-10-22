package com.omer.belestiviclone.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Entirely fictional fixtures; no production list, URL, credential, DB or user preferences. */
class CatalogPlannerTest {
    private fun bits(n: Long) = OpaqueId(0x1234, n)
    private val main = SourceRecord(SourceId(bits(1)), SourceRole.MAIN, true)
    private val extra = SourceRecord(SourceId(bits(2)), SourceRole.SUPPLEMENT, true, main.id)
    private val passive = SourceRecord(SourceId(bits(3)), SourceRole.MAIN, false)
    private val oldId = ChannelId(bits(10))
    private val secondId = ChannelId(bits(11))
    private val category = CategoryRecord(CategoryId(bits(20)), CategoryLabel.Exact("SPOR"), 0)
    private val oldMembership = MembershipRecord(MembershipId(bits(30)), oldId, main.id, category.id)
    private fun definition(path: String = "a") = PlaybackDefinition(Address.fromForm("https://media.invalid/$path"))
    private fun row(source: SourceRecord = main, order: Long = 0, path: String = "a", label: String = "SPOR",
        name: String? = "Kanal A", logo: String? = null) =
        ChannelContribution(source.id, order, definition(path), CategoryLabel.fromRaw(label), name, logo, "fixture-provider")
    private fun snapshot(memberships: List<MembershipRecord> = listOf(oldMembership)) = CatalogSnapshot(
        listOf(main, extra, passive), listOf(ChannelRecord(oldId, definition(), 0)), memberships, listOf(category))
    private fun supply() = IdentitySupply((100L..120L).map { ChannelId(bits(it)) },
        (200L..240L).map { MembershipId(bits(it)) }, (300L..320L).map { CategoryId(bits(it)) })
    private fun plan(rows: List<ChannelContribution>, old: CatalogSnapshot = snapshot()) = CatalogPlanner.plan(old, rows, supply())

    @Test fun sameNameAndProviderDifferentPublicationNeverRetargetsOldIdentity() {
        val result = plan(listOf(row(path = "b")))
        assertNotEquals(oldId, result.catalog.single().id)
        val old = result.channels.first { it.record.id == oldId }
        assertFalse(old.hasUsableActiveMembership)
        assertNull(old.catalogRepresentationId)
        assertEquals(oldMembership, result.memberships.first().record)
        assertFalse(result.memberships.first().isCurrentInPlan)
    }

    @Test fun sameNameDifferentMainAndSupplementPublicationsStayTwoCatalogEntries() {
        val result = plan(listOf(row(), row(extra, path = "b")))
        assertEquals(2, result.catalog.size)
        assertEquals(2, result.catalog.map { it.id }.distinct().size)
        assertTrue(result.catalog.all { it.hasUsableActiveMembership && it.name == "Kanal A" })
        assertEquals(oldId, result.catalog.first().id)
    }

    @Test fun newExactMainSupplementRowsAllocateOnlyOneChannelIdentity() {
        val old = snapshot(emptyList()).copy(channels = emptyList(), categories = emptyList())
        val result = plan(listOf(row(), row(extra, label = "Spor")), old)
        assertEquals(1, result.consumedChannelIds)
        assertEquals(1, result.catalog.size)
        assertEquals(1, result.channels.size)
        assertEquals(2, result.consumedMembershipIds)
        assertEquals(2, result.catalog.single().categoryIds.size)
        assertTrue(result.catalog.single().contributions.all { it.membership.channelId == result.catalog.single().id })
    }

    @Test fun metadataChangePreservesChannelAndMembershipIdentity() {
        val result = plan(listOf(row(name = "Yeni ad", logo = "https://image.invalid/new")))
        assertEquals(oldId, result.catalog.single().id)
        assertEquals(oldMembership.id, result.catalog.single().contributions.single().membership.id)
        assertEquals(IdentityDecision.REUSED_EXACT, result.catalog.single().contributions.single().decision)
        assertEquals("Yeni ad", result.catalog.single().name)
        assertEquals(0, result.consumedChannelIds)
    }

    @Test fun mainSupplementMergeKeepsAllOriginsAndExactCategories() {
        val result = plan(listOf(row(name = "Ana", logo = ""),
            row(extra, label = "Spor", name = "Ek", logo = "https://image.invalid/extra")))
        val item = result.catalog.single()
        assertEquals(oldId, item.id)
        assertEquals(2, item.contributions.size)
        assertEquals(2, item.categoryIds.size)
        assertEquals(setOf(main.id, extra.id), item.contributions.map { it.input.sourceId }.toSet())
        assertEquals("Ana", item.name)
        assertEquals("https://image.invalid/extra", item.logo)
    }

    @Test fun mainFilledFieldsWinAndFirstSeenFilledFieldsBreakRowTies() {
        val result = plan(listOf(row(extra, name = "Ek", logo = "https://image.invalid/extra"),
            row(order = 2, name = "Sonraki", logo = "https://image.invalid/later"),
            row(order = 0, name = "", logo = null), row(order = 1, name = "İlk dolu", logo = "https://image.invalid/main")))
        assertEquals("İlk dolu", result.catalog.single().name)
        assertEquals("https://image.invalid/main", result.catalog.single().logo)
        assertEquals(4, result.catalog.single().contributions.size)
    }

    @Test fun duplicateRowsKeepMembershipsWithoutDuplicatingCatalog() {
        val result = plan(listOf(row(), row(order = 1), row(order = 2, label = "Diğer")))
        assertEquals(1, result.catalog.size)
        assertEquals(3, result.catalog.single().contributions.map { it.membership.id }.distinct().size)
        assertEquals(2, result.catalog.single().categoryIds.size)
        assertEquals(oldMembership.id, result.catalog.single().contributions.first().membership.id)
    }

    @Test fun returningSingleOrphanReusesIdentityWithoutInventingPreferences() {
        val old = snapshot(emptyList())
        val absent = plan(emptyList(), old)
        assertEquals(old.channels.single(), absent.channels.single().record)
        assertFalse(absent.channels.single().hasUsableActiveMembership)
        val returned = plan(listOf(row(name = "Geri geldi")), old)
        assertEquals(oldId, returned.catalog.single().id)
        assertTrue(returned.channels.single().hasUsableActiveMembership)
    }

    @Test fun ambiguousOrphansAllocateFreshAndRetainEveryOldReference() {
        val old = snapshot(emptyList()).copy(channels = listOf(ChannelRecord(oldId, definition(), 0),
            ChannelRecord(secondId, definition(), 1)))
        val result = plan(listOf(row()), old)
        assertEquals(3, result.channels.size)
        assertTrue(result.catalog.single().id !in setOf(oldId, secondId))
        assertEquals(IdentityDecision.AMBIGUOUS, result.catalog.single().contributions.single().decision)
        assertTrue(result.channels.filter { it.record.id in setOf(oldId, secondId) }.all {
            !it.hasUsableActiveMembership && it.catalogRepresentationId == null
        })
    }

    @Test fun convergedExistingIdentitiesHaveOneRepresentationAndSeparateReferences() {
        val other = MembershipRecord(MembershipId(bits(31)), secondId, extra.id, category.id)
        val old = snapshot(listOf(oldMembership, other)).copy(channels = listOf(
            ChannelRecord(oldId, definition(), 5), ChannelRecord(secondId, definition(), 0)))
        val result = plan(listOf(row(), row(extra)), old)
        assertEquals(1, result.catalog.size)
        assertEquals(oldId, result.catalog.single().id) // active main outranks earlier supplement ID
        assertEquals(setOf(oldId, secondId), result.catalog.single().referenceIds.toSet())
        assertEquals(old.channels.toSet(), result.channels.map { it.record }.toSet())
        assertTrue(result.channels.all { it.hasUsableActiveMembership && it.catalogRepresentationId == oldId })
        assertEquals(setOf(oldMembership, other), result.memberships.map { it.record }.toSet())
        assertEquals(0, result.consumedChannelIds)
        assertTrue(result.catalog.single().contributions.all { it.decision == IdentityDecision.REUSED_SOURCE_SCOPED_EXACT })
    }

    @Test fun multipleCandidatesWithinSameSourceDoNotUseAnArbitraryTieBreakForIdentity() {
        val other = MembershipRecord(MembershipId(bits(31)), secondId, main.id, category.id)
        val old = snapshot(listOf(oldMembership, other)).copy(channels = listOf(
            ChannelRecord(oldId, definition(), 0), ChannelRecord(secondId, definition(), 1)))
        val result = plan(listOf(row()), old)
        assertEquals(IdentityDecision.AMBIGUOUS, result.catalog.single().contributions.single().decision)
        assertTrue(result.channels.filter { it.record.id in setOf(oldId, secondId) }.none { it.hasUsableActiveMembership })
        assertEquals(2, result.memberships.count { !it.isCurrentInPlan })
    }

    @Test fun tokenChangeNeverRebindsEvenWithSameSourceAndProviderHint() {
        val result = plan(listOf(row(path = "a?token=fixture-changed")))
        assertNotEquals(oldId, result.catalog.single().id)
        assertFalse(result.channels.first { it.record.id == oldId }.hasUsableActiveMembership)
    }

    @Test fun authMimeDrmAndScopeDifferencesProduceSeparateIdentities() {
        val base = row()
        val variants = listOf(base.definition.copy(headers = listOf(Header("Authorization", "fixture"))),
            base.definition.copy(mimeType = "fixture/type"), base.definition.copy(drm = DrmDefinition("fixture-drm")),
            base.definition.copy(authorityScope = "fixture-scope"))
        val result = plan(listOf(base) + variants.mapIndexed { index, definition -> base.copy(rowOrder = index + 1L, definition = definition) })
        assertEquals(5, result.catalog.size)
        assertEquals(5, result.catalog.map { it.id }.distinct().size)
    }

    @Test fun blockedDefinitionsNeverMergeOrBecomeUsable() {
        val row = row().let { it.copy(definition = it.definition.copy(unknownRequiredDirectives = listOf("fixture-unknown"))) }
        val result = plan(listOf(row, row.copy(rowOrder = 1)))
        assertEquals(2, result.catalog.size)
        assertTrue(result.catalog.all { !it.hasUsableActiveMembership })
        assertTrue(result.catalog.flatMap { it.contributions }.all { it.decision == IdentityDecision.BLOCKED })
        assertFalse(result.channels.first { it.record.id == oldId }.hasUsableActiveMembership)
    }

    @Test fun passiveMembershipDoesNotImplyUsableActiveMembershipOrSourceSelection() {
        val result = plan(listOf(row(passive)))
        assertEquals(oldId, result.catalog.single().id)
        assertFalse(result.catalog.single().hasUsableActiveMembership)
        assertFalse(result.channels.single().hasUsableActiveMembership)
        assertEquals(passive.id, result.catalog.single().contributions.single().input.sourceId)
    }

    @Test fun passiveMetadataCannotFillEffectiveCatalogFields() {
        val result = plan(listOf(row(name = "Ana", logo = null), row(passive, name = "Pasif", logo = "https://image.invalid/passive")))
        assertTrue(result.catalog.single().hasUsableActiveMembership)
        assertNull(result.catalog.single().logo)
        assertEquals(2, result.catalog.single().contributions.size)
    }

    @Test fun removingSupplementRecomputesFieldsAndKeepsReferenceRecords() {
        val extraMembership = MembershipRecord(MembershipId(bits(31)), oldId, extra.id, category.id)
        val result = plan(listOf(row(name = "Ana", logo = null)), snapshot(listOf(oldMembership, extraMembership)))
        assertNull(result.catalog.single().logo)
        assertTrue(result.catalog.single().hasUsableActiveMembership)
        assertFalse(result.memberships.first { it.record.id == extraMembership.id }.isCurrentInPlan)
    }

    @Test fun categoriesPreserveExactUnicodeSpacingAndExistingOrder() {
        val labels = listOf("SPOR", "Spor", "Ş", "S", "é", "e\u0301", "A B", "A  B", "", " ")
        val result = plan(labels.mapIndexed { index, label -> row(order = index.toLong(), label = label) })
        assertEquals(labels.size, result.categories.size)
        assertEquals(category, result.categories.first())
        assertEquals(labels.map { CategoryLabel.fromRaw(it) }.toSet(), result.categories.map { it.label }.toSet())
        val gone = plan(emptyList())
        assertEquals(listOf(category), gone.categories)
        assertEquals(category.id, plan(listOf(row())).catalog.single().categoryIds.single())
    }

    @Test fun sameInputAndSupplyAreDeterministicAndInputsRemainUnchanged() {
        val old = snapshot()
        val rows = mutableListOf(row(extra, label = "Diğer"), row(path = "b"), row(order = 1))
        val ids = supply()
        val before = rows.toList()
        val oldBefore = old.copy(sources = old.sources.toList(), channels = old.channels.toList(),
            memberships = old.memberships.toList(), categories = old.categories.toList())
        val idsBefore = ids.copy(channels = ids.channels.toList(), memberships = ids.memberships.toList(), categories = ids.categories.toList())
        val result = CatalogPlanner.plan(old, rows, ids)
        assertEquals(result, CatalogPlanner.plan(old, rows, ids))
        assertEquals(result, CatalogPlanner.plan(old, rows.reversed(), ids))
        assertEquals(oldBefore, old)
        assertEquals(before, rows)
        assertEquals(idsBefore, ids)
    }

    @Test fun invalidScopeDuplicateIdentitiesAndInsufficientSupplyFailClosed() {
        assertFailsWith<IllegalArgumentException> { CatalogPlanner.plan(snapshot(), listOf(row(path = "b")), IdentitySupply()) }
        assertFailsWith<IllegalArgumentException> { plan(listOf(row(), row())) }
        assertFailsWith<IllegalArgumentException> { plan(listOf(row()), snapshot().copy(channels = snapshot().channels + snapshot().channels)) }
        assertFailsWith<IllegalArgumentException> { plan(listOf(row()), snapshot().copy(sources = listOf(main, extra.copy(parentSourceId = passive.id)))) }
        assertFailsWith<IllegalArgumentException> { CatalogPlanner.plan(snapshot(), listOf(row()), supply().copy(channels = listOf(oldId))) }
    }

    @Test fun opaqueIdentityOrderingDoesNotDependOnVisibleMetadata() {
        assertTrue(OpaqueId(0, -1) < OpaqueId(1, 0))
        assertTrue(OpaqueId(0, 1) < OpaqueId(0, -1))
        assertNotEquals(ChannelId(bits(10)), ChannelId(bits(11)))
    }
}
