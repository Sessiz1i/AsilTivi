package com.omer.belestiviclone

/** Exact, single-candidate debug admission. Checked before asset or store access. */
object R38AEntry {
    const val action = "com.omer.belestiviclone.R38A_PLAY"
    const val activity = "com.omer.belestiviclone.R38APlaybackActivity"
    const val session = "toplu-r38a-20261016-c013-a"
    const val candidate = "R20-C013"
    const val database = "$session.db"
    fun allowed(debug: Boolean, candidate: String?, session: String?, action: String?, command: String?,
        database: String?, key: Int, className: String) = debug && candidate == this.candidate &&
        session == this.session && action == this.action && command == "play" &&
        database == this.database && key == 2 && className == activity
}
