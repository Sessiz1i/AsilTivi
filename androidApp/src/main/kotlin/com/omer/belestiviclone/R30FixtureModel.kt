package com.omer.belestiviclone

import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.preview.*

/** New synthetic identities; no migration/proof API and no stored preferences are consumed. */
internal object R30FixtureModel {
    fun create(session: String, seed: List<PreviewChannel>): PreviewData {
        require(session in R30Entry.sessions)
        val a = session == "toplu-r30-render-a-20261015"
        val entry = if (a) R28ProfileTransition.entry else ApprovedR21.manifest.entries.single { it.candidate == "R20-C003" }
        val original = seed.single { it.definition == entry.old }
        val channel = original.copy(id = OpaqueId(if (a) 0x52333041 else 0x52333042, 1), definition = entry.new,
            sourceReferences = emptyList(), logo = "ikon_tv")
        return PreviewData(channels = listOf(channel), profileId = PublicM3u.profile, favorites = listOf(channel.id),
            history = emptyList(), hidden = emptySet(), hiddenCategories = emptySet(), categories = channel.groups,
            sources = listOf("R30 synthetic ${if (a) "A" else "B"}"))
            .withPref("history", "Kapalı").withPref("startup", "Açık").withPref("format", "Sığdır (Fit)")
            .withPref("lastChannel", PreviewCodec.id(channel.id))
    }
}
