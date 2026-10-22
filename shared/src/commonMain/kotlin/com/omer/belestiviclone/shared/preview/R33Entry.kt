package com.omer.belestiviclone.shared.preview

object R33Entry {
    const val action = "com.omer.belestiviclone.R33_TEST_UI"
    fun isolatedExtraAllowed(debug: Boolean, present: Boolean, session: String?) =
        !present || debug && session?.matches(Regex("toplu-[a-z0-9-]{1,48}")) == true
    fun marked(session: String?, action: String?, hasCommand: Boolean) = session?.startsWith("toplu-r33") == true || action == this.action || hasCommand
    fun allowed(debug: Boolean, session: String?, action: String?, hasCommand: Boolean) =
        debug && session == R33ProfileTransition.testSession && action == this.action && !hasCommand
}
