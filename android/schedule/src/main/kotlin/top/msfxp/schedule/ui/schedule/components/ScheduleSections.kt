package top.msfxp.schedule.ui.schedule.components

// Keep the timeline, grid and measured content at the same height. A class can
// extend past the last configured bell time (for example sections 11–12).
internal fun scheduleSectionCount(slotNumbers: List<Int>, endSections: List<Int>): Int =
    maxOf(11, slotNumbers.maxOrNull() ?: 0, endSections.maxOrNull() ?: 0).coerceAtMost(24)
