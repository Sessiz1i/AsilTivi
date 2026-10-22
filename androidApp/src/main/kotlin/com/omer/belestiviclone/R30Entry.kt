package com.omer.belestiviclone

/** Pure admission: resolve before constructing any store or cache. The debug subclass has DUMP permission. */
internal object R30Entry {
    const val action = "com.omer.belestiviclone.R30_RENDER"
    const val activity = "com.omer.belestiviclone.R30RenderActivity"
    val sessions = setOf("toplu-r30-render-a-20261015", "toplu-r30-render-b-20261015")
    fun marked(session: String?, intentAction: String?, hasCommand: Boolean, className: String) =
        hasCommand || intentAction == action || session?.startsWith("toplu-r30") == true || className == activity
    fun admit(debug: Boolean, session: String?, intentAction: String?, command: String?, className: String): String? =
        session?.takeIf { debug && it in sessions && intentAction == action && command == "open" && className == activity }
}
