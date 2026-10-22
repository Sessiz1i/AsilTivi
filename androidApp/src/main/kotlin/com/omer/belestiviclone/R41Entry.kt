package com.omer.belestiviclone

internal object R41Entry {
    const val action = "com.omer.belestiviclone.R41_TRACKS"
    const val activity = "com.omer.belestiviclone.R41TracksActivity"
    const val candidate = "SHAKA-ANGEL-ONE"
    val sessions = setOf("toplu-r41-20261017-a", "toplu-r41-20261017-b")
    fun marked(session: String?, action: String?, keys: Set<String>, clazz: String) =
        session?.startsWith("toplu-r41",true) == true || action?.startsWith("com.omer.belestiviclone.R41",true) == true ||
            keys.any { it.startsWith("r41",true) } || clazz == activity
    fun admit(debug: Boolean,candidate: String?,session: String?,action: String?,command: String?,database: String?,
        key: Int,clazz: String,restored: Boolean): String? = session?.takeIf {
        debug && !restored && it in sessions && candidate == this.candidate && action == this.action &&
            command == "open" && database == "$it.db" && key == 2 && clazz == activity
    }
}
