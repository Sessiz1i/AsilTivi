package com.omer.belestiviclone

/** Pure, exact admission; evaluated before store/catalog construction on every route. */
internal object R39Entry {
    const val action = "com.omer.belestiviclone.R39_RENDER"
    const val activity = "com.omer.belestiviclone.R39RenderActivity"
    val sessions = setOf("toplu-r39-20261016-a", "toplu-r39-20261016-b")
    fun marked(session: String?, action: String?, keys: Set<String>, className: String) =
        session?.startsWith("toplu-r39", true) == true ||
            action?.startsWith("com.omer.belestiviclone.R39", true) == true ||
            keys.any { it.startsWith("r39", true) } || className == activity
    fun admit(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?,
        database: String?, key: Int, className: String, restored: Boolean): String? =
        session?.takeIf { debug && !restored && it in sessions && candidate == "R20-C013" &&
            action == this.action && command == "open" && database == "$it.db" && key == 2 && className == activity }
}
