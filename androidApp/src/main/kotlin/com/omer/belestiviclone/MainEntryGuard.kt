package com.omer.belestiviclone

/** Reject experimental markers on the product route before any store/catalog constructor. */
object MainEntryGuard {
    fun marked(session: String?, action: String?, keys: Set<String>): Boolean =
        R42Entry.marked(session, action, keys, "") || session?.startsWith("toplu-r38") == true || session?.startsWith("toplu-r39", true) == true || session?.startsWith("toplu-r40", true) == true ||
            action?.startsWith("com.omer.belestiviclone.R38") == true || action?.startsWith("com.omer.belestiviclone.R39", true) == true || action?.startsWith("com.omer.belestiviclone.R40", true) == true ||
            keys.any { it.startsWith("r38", ignoreCase = true) || it.startsWith("r39", true) || it.startsWith("r40", true) }

    fun <T> product(session: String?, action: String?, keys: Set<String>, construct: () -> T): T? =
        if (marked(session, action, keys)) null else construct()
}
