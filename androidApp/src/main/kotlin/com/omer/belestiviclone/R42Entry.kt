package com.omer.belestiviclone

/** Admission is checked before any product or isolated store is constructed. */
internal object R42Entry {
    const val action = "com.omer.belestiviclone.R42_FOUR"
    const val activity = "com.omer.belestiviclone.R42FourActivity"
    const val candidate = "R42-FOUR-REAL-V1"
    val sessions = setOf("toplu-r42-20261017-a", "toplu-r42-20261017-b")
    fun marked(session: String?, action: String?, keys: Set<String>, clazz: String) =
        session?.startsWith("toplu-r42", true) == true || action?.startsWith("com.omer.belestiviclone.R42", true) == true ||
            keys.any { it.startsWith("r42", true) } || clazz == activity
    fun admit(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?, database: String?,
        key: Int, clazz: String, restored: Boolean): String? = session?.takeIf {
        debug && !restored && it in sessions && candidate == this.candidate && action == this.action &&
            command == "open" && database == "$it.db" && key == 2 && clazz == activity
    }
}
