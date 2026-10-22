package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

/** Admission is independent from the isolated R21/R22 gates and from normal load(). */
data class R23Admission(val debug: Boolean, val key: Int, val databaseName: String, val session: String?, val action: String?)

object R23ProfileTransition {
    const val normalSession = "r23-normal-approved-20261014"
    const val testSession = "toplu-r23-normal-20261014"
    const val normalDatabase = "belestivi_preview_v1.db"
    const val defaultLabel = "Özel M3U · trlist · 8 Ekim 2026"
    fun allowed(a: R23Admission): Boolean = a.debug && a.key == 2 &&
        a.action in setOf("preflight", "apply", "rollback", "legacy-exit") &&
        ((a.session == normalSession && a.databaseName == normalDatabase) ||
            (a.session == testSession && a.databaseName == "$testSession.db"))

    fun prepare(data: PreviewData, context: PilotContext, admission: R23Admission,
        manifest: TransitionManifest, allocate: () -> OpaqueId): PreviewData {
        fun requireProof(value: Boolean, code: String) { if (!value) throw TransitionProblem(code) }
        requireProof(data.library?.r38c == null, "r38c-layer-protected")
        requireProof(data.library?.r38b == null, "r38b-layer-must-rollback-first")
        requireProof(allowed(admission) && context.session == admission.session, "r23-disabled-profile")
        requireProof(manifest == ApprovedR21.manifest, "unapproved-manifest")
        requireProof(data.profileId == PublicM3u.profile, "disabled-profile")
        requireProof(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408, "cache-identity")
        data.library?.transition?.let { requireProof(it.sourceBefore == context.seedSource, "journal-source-binding") }
        // Bind the persisted targets to the already-existing old cache IDs, including no-op/exit.
        manifest.entries.forEach { entry ->
            val seed = context.seed.singleOrNull { it.definition == entry.old } ?: throw TransitionProblem("cache-target")
            val current = data.channels.singleOrNull { it.id == seed.id } ?: throw TransitionProblem("cache-id-binding")
            requireProof(current.available && current.sourceReferences.isEmpty() && current.definition in listOf(entry.old, entry.new), "cache-id-binding")
        }
        if (data.library == null) requireProof(data.sources == listOf(defaultLabel) && data.channels == context.seed, "unproven-legacy-source")
        return when (admission.action) {
            "preflight", "apply" -> VerifiedDefinitionTransition.apply(data, context, manifest, allocate)
            "rollback" -> VerifiedDefinitionTransition.rollback(data, context, manifest)
            "legacy-exit" -> VerifiedDefinitionTransition.legacyExit(data, context, manifest)
            else -> throw TransitionProblem("r23-disabled-profile")
        }
    }
}
