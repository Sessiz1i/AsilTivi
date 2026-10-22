package com.omer.belestiviclone

/** Exact shell-only measurement admission; product startup never opens an R38 store. */
object R38Entry {
    const val action = "com.omer.belestiviclone.R38_PLAY"
    const val activity = "com.omer.belestiviclone.R38PlaybackActivity"
    val candidates = setOf(13, 18, 22, 33, 34, 48, 49, 50, 57, 58, 60, 61, 69, 71, 73,
        74, 76, 77, 78, 79, 85, 86, 89, 93, 94, 95, 103, 104, 105, 106).map { "R20-C%03d".format(it) }.toSet()
    fun session(candidate: String) = "toplu-r38-20261016-c-${candidate.lowercase()}"
    fun marked(session: String?, action: String?, hasCommand: Boolean) =
        hasCommand || action == this.action || session?.startsWith("toplu-r38") == true
    fun allowed(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?,
        database: String?, key: Int, className: String) = debug && candidate in candidates &&
        session == session(candidate!!) && action == this.action && command == "play" &&
        database == "$session.db" && key == 2 && className == activity
}
