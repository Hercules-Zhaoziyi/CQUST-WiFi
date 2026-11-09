package top.msfxp.schedule.util

// 课程地点解析与规范化工具
object CourseLocationFormatter {

    const val CAMPUS_HUXI = "虎溪校区"

    // 金凤校区常量预留，后续明确其楼栋格式后再接入映射
    const val CAMPUS_JINFENG = "金凤校区"

    // 虎溪校区字母楼栋与标准楼名映射
    private val BUILDING_NAME_BY_LETTER = mapOf(
        'A' to "教学主楼",
        'B' to "教学主楼",
        'C' to "教学主楼",
        'D' to "教学主楼",
        'K' to "笃行楼",
        'L' to "砺志楼",
        'M' to "知行楼",
        'E' to "博学楼",
        'F' to "博学楼",
        'G' to "厚德楼",
        'H' to "厚德楼",
        'I' to "逸夫楼"
    )

    private val KNOWN_BUILDING_NAMES = BUILDING_NAME_BY_LETTER.values
        .distinct()
        .sortedByDescending { it.length }

    private val LETTER_ROOM_REGEX = Regex("""^([A-Za-z])(?:栋)?([A-Za-z]?\d+.*)$""")

    // 仅匹配开头的校区前缀，避免地点文本中其他“校区”字样造成误判
    private val CAMPUS_PREFIX_REGEX = Regex("""^(虎溪|金凤)\s*(?:校区)?""")

    // 解析后的地点结构
    data class ParsedLocation(
        val campus: String = "",
        val building: String = "",
        val room: String = "",
        val detail: String = ""
    ) {
        // 完整展示文本
        val displayText: String
            get() = if (building.isNotBlank() || room.isNotBlank()) {
                listOf(campus, building, room).filter { it.isNotBlank() }.joinToString(" ")
            } else {
                listOf(campus, detail).filter { it.isNotBlank() }.joinToString(" ")
            }
    }

    // 解析原始地点文本（仅虎溪校区做解析，其他校区保持原始文本）
    fun parse(raw: String): ParsedLocation {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "待定" || trimmed.contains("未安排")) {
            return ParsedLocation(detail = trimmed)
        }

        val campusMatch = CAMPUS_PREFIX_REGEX.find(trimmed)
            ?: return ParsedLocation(detail = trimmed)
        val campus = "${campusMatch.groupValues[1]}校区"
        if (campus != CAMPUS_HUXI) return ParsedLocation(detail = trimmed)

        val remainder = trimmed.substring(campusMatch.range.last + 1).trim()
        if (remainder.isBlank()) return ParsedLocation(campus = campus)

        val knownBuilding = KNOWN_BUILDING_NAMES.firstOrNull { remainder.startsWith(it) }
        if (knownBuilding != null) {
            return ParsedLocation(
                campus = campus,
                building = knownBuilding,
                room = remainder.removePrefix(knownBuilding).trim()
            )
        }

        val match = LETTER_ROOM_REGEX.find(remainder)
        if (match != null) {
            val letter = match.groupValues[1].uppercase().first()
            val room = letter + match.groupValues[2]
                .removePrefix(letter.toString())
                .removePrefix(letter.toString().lowercase())
            return ParsedLocation(
                campus = campus,
                building = BUILDING_NAME_BY_LETTER[letter].orEmpty(),
                room = room
            )
        }

        return ParsedLocation(campus = campus, detail = remainder)
    }

    // 规范化为“校区 楼栋 教室”存储文本（仅处理虎溪校区，其余保持原始内容）
    fun format(raw: String): String {
        val trimmed = raw.trim()
        val parsed = parse(trimmed)
        if (parsed.campus != CAMPUS_HUXI) return trimmed
        return parsed.displayText
    }

    // 常规展示文本（虎溪校区省略前缀，其余保持原始内容）
    fun formatDisplay(raw: String): String {
        return format(raw).removePrefix("$CAMPUS_HUXI ").trim()
    }
}
