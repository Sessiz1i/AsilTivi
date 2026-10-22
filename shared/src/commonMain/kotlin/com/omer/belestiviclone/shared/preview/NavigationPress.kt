package com.omer.belestiviclone.shared.preview

data class NavigationPressResult(val consumed: Boolean, val perform: Boolean = false)

/** One action per physical press. Repeats and the matching release never enqueue another action. */
class NavigationPress {
    private val held = mutableSetOf<Int>()
    fun event(key: Int, down: Boolean, repeat: Boolean, eligible: Boolean): NavigationPressResult {
        if (!down) return NavigationPressResult(held.remove(key))
        if (key in held) return NavigationPressResult(true)
        if (!eligible) return NavigationPressResult(false)
        if (repeat) return NavigationPressResult(true)
        held += key
        return NavigationPressResult(true, true)
    }
    fun reset() = held.clear()
}
