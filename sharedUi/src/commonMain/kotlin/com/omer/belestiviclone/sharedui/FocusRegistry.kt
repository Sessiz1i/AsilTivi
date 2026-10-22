package com.omer.belestiviclone.sharedui

/** Subpages temporarily unmount their opener. Keep its identity until it is mounted again. */
internal class FocusRegistry<T> {
    private val entries = mutableMapOf<String, T>()
    fun requester(key: String, create: () -> T): T = entries.getOrPut(key, create)
    fun returnTarget(opener: T?, active: List<T>): T? = opener?.takeIf { it in active } ?: active.firstOrNull()
    fun entryTarget(key: String, active: List<T>): T? = returnTarget(entries[key], active)
}
