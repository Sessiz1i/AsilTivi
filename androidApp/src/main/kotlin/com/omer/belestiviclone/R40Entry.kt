package com.omer.belestiviclone

internal object R40Entry {
    const val action = "com.omer.belestiviclone.R40_QUALITY"
    const val activity = "com.omer.belestiviclone.R40QualityActivity"
    val sessions = setOf("toplu-r40-20261016-a", "toplu-r40-20261016-b")
    fun marked(session: String?, action: String?, keys: Set<String>, clazz: String) =
        session?.startsWith("toplu-r40", true) == true || action?.startsWith("com.omer.belestiviclone.R40", true) == true ||
            keys.any { it.startsWith("r40", true) } || clazz == activity
    fun admit(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?, database: String?,
        key: Int, clazz: String, restored: Boolean): String? = session?.takeIf {
        debug && !restored && it in sessions && candidate == "R20-C013" && action == this.action &&
            command == "open" && database == "$it.db" && key == 2 && clazz == activity
    }
}
internal fun r40Hash(value: String) = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Same complete field order as the frozen admission fixture; only a digest leaves the process. */
internal fun r40FullHash(d: com.omer.belestiviclone.shared.catalog.PlaybackDefinition): String? {
    if (d.drm != null) return null
    return r40Hash(com.omer.belestiviclone.shared.preview.PreviewCodec.pack(listOf(d.address.raw,
        com.omer.belestiviclone.shared.preview.PreviewCodec.pack(d.headers.flatMap { listOf(it.name, it.value) }),
        d.mimeType.orEmpty(), d.protocol.orEmpty(), "", d.authorityScope.orEmpty(),
        com.omer.belestiviclone.shared.preview.PreviewCodec.pack(d.options.flatMap { listOf(it.name, it.value) }),
        com.omer.belestiviclone.shared.preview.PreviewCodec.pack(d.unknownRequiredDirectives), d.isLossless.toString())))
}
