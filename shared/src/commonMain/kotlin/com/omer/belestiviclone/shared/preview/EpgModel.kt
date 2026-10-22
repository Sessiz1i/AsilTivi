package com.omer.belestiviclone.shared.preview

/**
 * EPG (Elektronik Program Rehberi) veri modelleri, Türkiye saat dilimi (UTC+3) ve
 * XMLTV / ISO-8601 tarih çözümleyicisi, akış tabanlı XMLTV ayrıştırıcısı,
 * kanal eşleme motoru ve önbellek yöneticisi.
 */
data class EpgProgramme(
    val channelId: String,
    val title: String,
    val desc: String = "",
    val startEpochMs: Long,
    val endEpochMs: Long,
) {
    fun isActiveAt(nowEpochMs: Long): Boolean = nowEpochMs in startEpochMs until endEpochMs

    fun progressAt(nowEpochMs: Long): Float {
        val duration = endEpochMs - startEpochMs
        if (duration <= 0L) return 0f
        return ((nowEpochMs - startEpochMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun progressPercentAt(nowEpochMs: Long): Int =
        (progressAt(nowEpochMs) * 100f).toInt().coerceIn(0, 100)

    fun timeRangeLabel(utcOffsetMinutes: Int = EpgDateTime.TURKEY_OFFSET_MINUTES): String =
        "${EpgDateTime.formatHhMm(startEpochMs, utcOffsetMinutes)} - ${EpgDateTime.formatHhMm(endEpochMs, utcOffsetMinutes)}"
}

data class EpgChannelInfo(
    val id: String,
    val displayNames: List<String> = emptyList(),
    val iconUrl: String = "",
)

data class EpgNowNext(
    val channelId: String? = null,
    val now: EpgProgramme? = null,
    val next: EpgProgramme? = null,
) {
    val hasData: Boolean get() = now != null || next != null

    fun progressAt(nowEpochMs: Long): Float = now?.progressAt(nowEpochMs) ?: 0f

    fun progressPercentAt(nowEpochMs: Long): Int = now?.progressPercentAt(nowEpochMs) ?: 0

    fun nowTimeRange(utcOffsetMinutes: Int = EpgDateTime.TURKEY_OFFSET_MINUTES): String =
        now?.timeRangeLabel(utcOffsetMinutes) ?: "--:-- - --:--"

    fun nextTimeRange(utcOffsetMinutes: Int = EpgDateTime.TURKEY_OFFSET_MINUTES): String =
        next?.timeRangeLabel(utcOffsetMinutes) ?: "--:-- - --:--"
}

interface EpgLoader {
    suspend fun loadSampleXml(): String
    suspend fun fetchXml(url: String): String
}

object EpgDateTime {
    const val TURKEY_OFFSET_MINUTES = 180 // UTC+3 (Türkiye saat dilimi)
    private const val DAY_MS = 86_400_000L

    /**
     * XMLTV (`yyyyMMddHHmmss Z`, `yyyyMMddHHmmss +0300`, `yyyyMMddHHmmss`) ve
     * ISO-8601 (`2026-10-23T20:00:00+03:00`, `2026-10-23T17:00:00Z`) tarihlerini epoch milisaniyeye çevirir.
     */
    fun parseEpochMs(raw: String, defaultOffsetMinutes: Int = TURKEY_OFFSET_MINUTES): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return if (text.length >= 10 && (text[4] == '-' || text[4] == '/')) {
            parseIso8601(text, defaultOffsetMinutes)
        } else {
            parseXmltv(text, defaultOffsetMinutes)
        }
    }

    private fun parseXmltv(text: String, defaultOffsetMinutes: Int): Long? {
        var digitCount = 0
        while (digitCount < text.length && text[digitCount].isDigit()) {
            digitCount++
        }
        if (digitCount < 8) return null
        val digits = text.substring(0, digitCount)
        val year = digits.substring(0, 4).toIntOrNull() ?: return null
        val month = digits.substring(4, 6).toIntOrNull() ?: return null
        val day = digits.substring(6, 8).toIntOrNull() ?: return null
        val hour = if (digitCount >= 10) digits.substring(8, 10).toIntOrNull() ?: return null else 0
        val minute = if (digitCount >= 12) digits.substring(10, 12).toIntOrNull() ?: return null else 0
        val second = if (digitCount >= 14) digits.substring(12, 14).toIntOrNull() ?: return null else 0

        val tzPart = text.substring(digitCount).trim()
        val offsetMinutes = if (tzPart.isEmpty()) defaultOffsetMinutes else parseOffsetMinutes(tzPart) ?: return null
        return civilToEpochMs(year, month, day, hour, minute, second, 0, offsetMinutes)
    }

    private fun parseIso8601(text: String, defaultOffsetMinutes: Int): Long? {
        val regex = Regex(
            "^(\\d{4})[-/](\\d{2})[-/](\\d{2})(?:[T\\s](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,3})\\d*)?)?)?\\s*(Z|UTC|GMT|[+-]\\d{2}:?\\d{2}|[+-]\\d{2})?$",
            RegexOption.IGNORE_CASE
        )
        val match = regex.matchEntire(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val minute = match.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val second = match.groupValues[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val millisRaw = match.groupValues[7]
        val millis = when (millisRaw.length) {
            0 -> 0
            1 -> (millisRaw.toIntOrNull() ?: 0) * 100
            2 -> (millisRaw.toIntOrNull() ?: 0) * 10
            else -> millisRaw.take(3).toIntOrNull() ?: 0
        }
        val tzRaw = match.groupValues[8]
        val offsetMinutes = if (tzRaw.isEmpty()) defaultOffsetMinutes else parseOffsetMinutes(tzRaw) ?: return null
        return civilToEpochMs(year, month, day, hour, minute, second, millis, offsetMinutes)
    }

    internal fun parseOffsetMinutes(tz: String): Int? {
        val cleaned = tz.trim().uppercase()
        if (cleaned == "Z" || cleaned == "UTC" || cleaned == "GMT") return 0
        if (cleaned == "TRT" || cleaned == " EET" || cleaned == "+03") return 180
        val sign = when (cleaned.firstOrNull()) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val digits = cleaned.substring(1).replace(":", "").trim()
        val hours: Int
        val minutes: Int
        when (digits.length) {
            2 -> {
                hours = digits.toIntOrNull() ?: return null
                minutes = 0
            }
            4 -> {
                hours = digits.substring(0, 2).toIntOrNull() ?: return null
                minutes = digits.substring(2, 4).toIntOrNull() ?: return null
            }
            else -> return null
        }
        if (hours !in 0..18 || minutes !in 0..59) return null
        return sign * (hours * 60 + minutes)
    }

    private fun civilToEpochMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
        offsetMinutes: Int,
    ): Long? {
        if (month !in 1..12) return null
        val maxDay = daysInMonth(year, month)
        if (day !in 1..maxDay) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        val days = daysFromCivil(year, month, day)
        val utcSeconds = days * 86_400L + hour * 3_600L + minute * 60L + second - offsetMinutes * 60L
        return utcSeconds * 1000L + millis
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = if (y >= 0) y / 400 else (y - 399) / 400
        val yoe = y - era * 400
        val m = month.toLong()
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    fun formatHhMm(epochMs: Long, offsetMinutes: Int = TURKEY_OFFSET_MINUTES): String {
        val totalMinutes = ((epochMs / 60_000L) + offsetMinutes).mod(1440L)
        val hh = (totalMinutes / 60L).toInt()
        val mm = (totalMinutes % 60L).toInt()
        return "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}"
    }

    fun startOfDayEpochMs(epochMs: Long, offsetMinutes: Int = TURKEY_OFFSET_MINUTES): Long {
        val offsetMs = offsetMinutes * 60_000L
        val localMs = epochMs + offsetMs
        val startOfLocalDay = localMs - localMs.mod(DAY_MS)
        return startOfLocalDay - offsetMs
    }
}

data class XmltvParseResult(
    val channels: List<EpgChannelInfo>,
    val programmes: List<EpgProgramme>,
)

/**
 * Belleği şişirmeden çalışan, akış tabanlı (pull/event mantığıyla satır/etiket bazlı) XMLTV ayrıştırıcısı.
 * Tüm DOM ağacını bellekte tutmaz; her `<channel>` ve `<programme>` kapanışında geri çağırma (callback) üretir.
 */
object XmltvStreamParser {
    private val attrRegex = Regex("""([a-zA-Z0-9_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun parse(
        xml: String,
        cutoffEpochMs: Long? = null,
        defaultOffsetMinutes: Int = EpgDateTime.TURKEY_OFFSET_MINUTES,
    ): XmltvParseResult = parseLines(xml.lineSequence(), cutoffEpochMs, defaultOffsetMinutes)

    fun parseLines(
        lines: Sequence<String>,
        cutoffEpochMs: Long? = null,
        defaultOffsetMinutes: Int = EpgDateTime.TURKEY_OFFSET_MINUTES,
    ): XmltvParseResult {
        val channels = mutableListOf<EpgChannelInfo>()
        val programmes = mutableListOf<EpgProgramme>()
        parseStream(
            lines = lines,
            cutoffEpochMs = cutoffEpochMs,
            defaultOffsetMinutes = defaultOffsetMinutes,
            onChannel = { channels += it },
            onProgramme = { programmes += it },
        )
        return XmltvParseResult(channels, programmes)
    }

    fun parseStream(
        lines: Sequence<String>,
        cutoffEpochMs: Long? = null,
        defaultOffsetMinutes: Int = EpgDateTime.TURKEY_OFFSET_MINUTES,
        onChannel: (EpgChannelInfo) -> Unit,
        onProgramme: (EpgProgramme) -> Unit,
    ) {
        val buffer = StringBuilder()
        var inChannel = false
        var channelId = ""
        var displayNames = mutableListOf<String>()
        var iconUrl = ""

        var inProgramme = false
        var progChannelId = ""
        var progStartMs: Long? = null
        var progEndMs: Long? = null
        var progTitle = ""
        var progDesc = ""

        var activeTag: String? = null
        val textAccumulator = StringBuilder()

        fun extractAttrs(tagContent: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (match in attrRegex.findAll(tagContent)) {
                val key = match.groupValues[1].lowercase()
                val value = if (match.groups[2] != null) match.groupValues[2] else match.groupValues[3]
                map[key] = decodeXmlEntities(value.trim())
            }
            return map
        }

        fun handleTag(rawTag: String) {
            val trimmed = rawTag.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("?") || trimmed.startsWith("!")) return
            val isClosing = trimmed.startsWith("/")
            val isSelfClosing = !isClosing && trimmed.endsWith("/")
            val core = trimmed.removePrefix("/").removeSuffix("/").trim()
            val tagName = core.takeWhile { !it.isWhitespace() }.lowercase()

            if (!isClosing) {
                when (tagName) {
                    "channel" -> {
                        val attrs = extractAttrs(core)
                        channelId = attrs["id"].orEmpty()
                        displayNames = mutableListOf()
                        iconUrl = ""
                        if (isSelfClosing) {
                            if (channelId.isNotEmpty()) onChannel(EpgChannelInfo(channelId, emptyList(), ""))
                            inChannel = false
                        } else {
                            inChannel = true
                        }
                    }
                    "display-name" -> if (inChannel && !isSelfClosing) {
                        activeTag = "display-name"
                        textAccumulator.clear()
                    }
                    "icon" -> if (inChannel) {
                        val attrs = extractAttrs(core)
                        val src = attrs["src"].orEmpty()
                        if (src.isNotEmpty() && iconUrl.isEmpty()) iconUrl = src
                    }
                    "programme", "program" -> {
                        val attrs = extractAttrs(core)
                        progChannelId = attrs["channel"].orEmpty()
                        val startRaw = attrs["start"].orEmpty()
                        val stopRaw = attrs["stop"] ?: attrs["end"].orEmpty()
                        progStartMs = EpgDateTime.parseEpochMs(startRaw, defaultOffsetMinutes)
                        progEndMs = EpgDateTime.parseEpochMs(stopRaw, defaultOffsetMinutes)
                        progTitle = ""
                        progDesc = ""
                        inProgramme = !isSelfClosing && progChannelId.isNotEmpty() && progStartMs != null && progEndMs != null
                    }
                    "title" -> if (inProgramme && !isSelfClosing) {
                        activeTag = "title"
                        textAccumulator.clear()
                    }
                    "desc", "sub-title" -> if (inProgramme && !isSelfClosing) {
                        activeTag = tagName
                        textAccumulator.clear()
                    }
                }
            } else {
                when (tagName) {
                    "display-name" -> if (activeTag == "display-name") {
                        val name = decodeXmlEntities(textAccumulator.toString()).trim()
                        if (name.isNotEmpty() && name !in displayNames) displayNames += name
                        activeTag = null
                        textAccumulator.clear()
                    }
                    "title" -> if (activeTag == "title") {
                        val title = decodeXmlEntities(textAccumulator.toString()).trim()
                        if (title.isNotEmpty() && progTitle.isEmpty()) progTitle = title
                        activeTag = null
                        textAccumulator.clear()
                    }
                    "desc", "sub-title" -> if (activeTag == tagName) {
                        val desc = decodeXmlEntities(textAccumulator.toString()).trim()
                        if (desc.isNotEmpty()) {
                            if (progDesc.isEmpty()) progDesc = desc.take(500)
                        }
                        activeTag = null
                        textAccumulator.clear()
                    }
                    "channel" -> if (inChannel) {
                        if (channelId.isNotEmpty()) {
                            onChannel(EpgChannelInfo(channelId, displayNames.toList(), iconUrl))
                        }
                        inChannel = false
                        channelId = ""
                    }
                    "programme", "program" -> if (inProgramme) {
                        val start = progStartMs
                        val end = progEndMs
                        if (progChannelId.isNotEmpty() && progTitle.isNotEmpty() && start != null && end != null && end > start) {
                            if (cutoffEpochMs == null || end > cutoffEpochMs) {
                                onProgramme(
                                    EpgProgramme(
                                        channelId = progChannelId,
                                        title = progTitle,
                                        desc = progDesc,
                                        startEpochMs = start,
                                        endEpochMs = end,
                                    )
                                )
                            }
                        }
                        inProgramme = false
                        progChannelId = ""
                        progStartMs = null
                        progEndMs = null
                    }
                }
            }
        }

        for (line in lines) {
            buffer.append(line).append('\n')
            while (true) {
                val cdataStart = buffer.indexOf("<![CDATA[")
                val lt = buffer.indexOf("<")
                if (lt < 0) {
                    if (activeTag != null && buffer.isNotEmpty()) {
                        textAccumulator.append(buffer)
                    }
                    buffer.clear()
                    break
                }
                if (lt > 0) {
                    if (activeTag != null) {
                        textAccumulator.append(buffer.substring(0, lt))
                    }
                    buffer.deleteRange(0, lt)
                }
                if (cdataStart == lt) {
                    val cdataEnd = buffer.indexOf("]]>")
                    if (cdataEnd < 0) break // wait for more lines
                    if (activeTag != null) {
                        textAccumulator.append(buffer.substring(9, cdataEnd))
                    }
                    buffer.deleteRange(0, cdataEnd + 3)
                    continue
                }
                if (buffer.startsWith("<!--")) {
                    val commentEnd = buffer.indexOf("-->")
                    if (commentEnd < 0) break
                    buffer.deleteRange(0, commentEnd + 3)
                    continue
                }
                val gt = buffer.indexOf(">")
                if (gt < 0) break // wait for closing '>' on next line
                val rawTag = buffer.substring(1, gt)
                buffer.deleteRange(0, gt + 1)
                handleTag(rawTag)
            }
        }
    }

    internal fun decodeXmlEntities(text: String): String {
        if ('&' !in text) return text
        return buildString(text.length) {
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch == '&') {
                    val semi = text.indexOf(';', i + 1)
                    if (semi > i + 1 && semi - i <= 10) {
                        val entity = text.substring(i + 1, semi)
                        val decoded = when {
                            entity == "amp" -> "&"
                            entity == "lt" -> "<"
                            entity == "gt" -> ">"
                            entity == "quot" -> "\""
                            entity == "apos" -> "'"
                            entity.startsWith("#x", ignoreCase = true) ->
                                entity.substring(2).toIntOrNull(16)?.toChar()?.toString()
                            entity.startsWith("#") ->
                                entity.substring(1).toIntOrNull(10)?.toChar()?.toString()
                            else -> null
                        }
                        if (decoded != null) {
                            append(decoded)
                            i = semi + 1
                            continue
                        }
                    }
                }
                append(ch)
                i++
            }
        }
    }
}

/**
 * Kanal eşleme motoru:
 * 1. M3U `tvg-id` eşleşmesi
 * 2. M3U `tvg-name` eşleşmesi
 * 3. Kanal adı benzerliği / normalize edilmiş isim eşleşmesi (büyük/küçük harf, Türkçe karakter toleransı)
 */
object EpgChannelMatcher {
    private val trailingNoiseTokens = setOf(
        "hd", "fhd", "uhd", "sd", "tr", "turkiye", "canli", "yayin", "official", "yedek", "tv"
    )

    fun asciiFold(input: String): String = buildString(input.length) {
        for (ch in input) {
            when (ch) {
                'Ç', 'ç' -> append('c')
                'Ğ', 'ğ' -> append('g')
                'İ', 'I', 'ı', 'i' -> append('i')
                'Ö', 'ö' -> append('o')
                'Ş', 'ş' -> append('s')
                'Ü', 'ü' -> append('u')
                'Â', 'â' -> append('a')
                'Î', 'î' -> append('i')
                'Û', 'û' -> append('u')
                'Ə', 'ə' -> append('e')
                else -> append(ch.lowercaseChar())
            }
        }
    }

    fun stripDomainSuffix(id: String): String =
        asciiFold(id.trim())
            .removeSuffix(".com.tr")
            .removeSuffix(".net.tr")
            .removeSuffix(".org.tr")
            .removeSuffix(".gov.tr")
            .removeSuffix(".tr")
            .removeSuffix(".tv")

    fun normalize(input: String): String {
        val folded = asciiFold(input)
        return buildString(folded.length) {
            for (ch in folded) {
                if (ch in 'a'..'z' || ch in '0'..'9') append(ch)
            }
        }
    }

    fun normalizeCore(input: String): String {
        val folded = stripDomainSuffix(input)
        val tokens = Regex("[a-z0-9]+").findAll(folded).map { it.value }.toMutableList()
        if (tokens.isEmpty()) return ""
        while (tokens.size > 1 && tokens.last() in trailingNoiseTokens) {
            val remainingLength = tokens.dropLast(1).sumOf { it.length }
            if (remainingLength < 2) break
            tokens.removeAt(tokens.lastIndex)
        }
        var joined = tokens.joinToString("")
        for (suffix in listOf("hd", "fhd", "uhd", "turkiye")) {
            if (joined.endsWith(suffix) && joined.length - suffix.length >= 3) {
                joined = joined.removeSuffix(suffix)
            }
        }
        if (joined.endsWith("tv") && joined.length - 2 >= 2) {
            val stem = joined.removeSuffix("tv")
            if (stem !in setOf("a", "n", "e", "k")) {
                joined = stem
            }
        }
        return joined
    }
}

/**
 * EPG Önbellek Yöneticisi (Cache Manager):
 * - Ayrıştırılan programları bellekte kanal ID'sine göre sıralı tutar
 * - Geçmiş programları (`evictExpired`) temizler
 * - 3 aşamalı öncelik sırasıyla (`tvg-id` -> `tvg-name` -> normalize kanal adı) hızlı erişim sağlar
 */
class EpgCacheManager {
    private val channelsById = mutableMapOf<String, EpgChannelInfo>()
    private val programmesByChannel = mutableMapOf<String, MutableList<EpgProgramme>>()

    private val exactIdIndex = mutableMapOf<String, String>()
    private val exactNameIndex = mutableMapOf<String, String>()
    private val normIdIndex = mutableMapOf<String, String>()
    private val normNameIndex = mutableMapOf<String, String>()
    private val coreIndex = mutableMapOf<String, String>()

    val channelCount: Int get() = channelsById.size
    val programmeCount: Int get() = programmesByChannel.values.sumOf { it.size }

    fun clear() {
        channelsById.clear()
        programmesByChannel.clear()
        exactIdIndex.clear()
        exactNameIndex.clear()
        normIdIndex.clear()
        normNameIndex.clear()
        coreIndex.clear()
    }

    fun loadSampleDefault(nowEpochMs: Long? = null) {
        loadXml(EpgSampleData.DEFAULT_XML, nowEpochMs = nowEpochMs, rebaseSampleIfOutdated = true)
    }

    fun loadXml(
        xml: String,
        nowEpochMs: Long? = null,
        rebaseSampleIfOutdated: Boolean = false,
    ) {
        loadStream(xml.lineSequence(), nowEpochMs = nowEpochMs, rebaseSampleIfOutdated = rebaseSampleIfOutdated)
    }

    fun loadStream(
        lines: Sequence<String>,
        nowEpochMs: Long? = null,
        rebaseSampleIfOutdated: Boolean = false,
    ) {
        val parsedChannels = mutableListOf<EpgChannelInfo>()
        val parsedProgrammes = mutableListOf<EpgProgramme>()
        val cutoff = if (rebaseSampleIfOutdated) null else nowEpochMs
        XmltvStreamParser.parseStream(
            lines = lines,
            cutoffEpochMs = cutoff,
            onChannel = { parsedChannels += it },
            onProgramme = { parsedProgrammes += it },
        )
        val effectiveProgrammes = if (rebaseSampleIfOutdated && nowEpochMs != null && parsedProgrammes.isNotEmpty()) {
            rebaseProgrammesToTargetTime(parsedProgrammes, nowEpochMs)
        } else {
            parsedProgrammes
        }
        putAll(parsedChannels, effectiveProgrammes, nowEpochMs)
    }

    internal fun rebaseProgrammesToTargetTime(
        programmes: List<EpgProgramme>,
        targetEpochMs: Long,
    ): List<EpgProgramme> {
        if (programmes.any { it.isActiveAt(targetEpochMs) }) return programmes
        val minStart = programmes.minOfOrNull { it.startEpochMs } ?: return programmes
        val sampleDayStart = EpgDateTime.startOfDayEpochMs(minStart)
        val targetDayStart = EpgDateTime.startOfDayEpochMs(targetEpochMs)
        val deltaMs = targetDayStart - sampleDayStart
        if (deltaMs == 0L) return programmes
        return programmes.map {
            it.copy(
                startEpochMs = it.startEpochMs + deltaMs,
                endEpochMs = it.endEpochMs + deltaMs,
            )
        }
    }

    fun putAll(
        channels: List<EpgChannelInfo>,
        programmes: List<EpgProgramme>,
        nowEpochMs: Long? = null,
    ) {
        clear()
        for (ch in channels) {
            if (ch.id.isNotBlank()) {
                channelsById[ch.id] = ch
            }
        }
        for (prog in programmes) {
            if (prog.channelId.isBlank() || prog.endEpochMs <= prog.startEpochMs) continue
            if (nowEpochMs != null && prog.endEpochMs <= nowEpochMs) continue
            if (prog.channelId !in channelsById) {
                channelsById[prog.channelId] = EpgChannelInfo(prog.channelId, listOf(prog.channelId))
            }
            val list = programmesByChannel.getOrPut(prog.channelId) { mutableListOf() }
            if (list.size < 250) {
                list += prog
            }
        }
        for (list in programmesByChannel.values) {
            list.sortBy { it.startEpochMs }
        }
        rebuildIndices()
    }

    /**
     * Geçmişte kalmış (`endEpochMs <= nowEpochMs`) programları bellekten temizler.
     */
    fun evictExpired(nowEpochMs: Long): Int {
        var removed = 0
        val iterator = programmesByChannel.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val before = entry.value.size
            entry.value.removeAll { it.endEpochMs <= nowEpochMs }
            removed += (before - entry.value.size)
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
        return removed
    }

    private fun rebuildIndices() {
        exactIdIndex.clear()
        exactNameIndex.clear()
        normIdIndex.clear()
        normNameIndex.clear()
        coreIndex.clear()

        for ((id, info) in channelsById) {
            val lowerId = id.trim().lowercase()
            exactIdIndex.putIfAbsent(lowerId, id)

            val normId = EpgChannelMatcher.normalize(EpgChannelMatcher.stripDomainSuffix(id))
            if (normId.isNotEmpty()) normIdIndex.putIfAbsent(normId, id)

            val coreId = EpgChannelMatcher.normalizeCore(id)
            if (coreId.isNotEmpty()) coreIndex.putIfAbsent(coreId, id)

            for (displayName in info.displayNames) {
                val lowerName = displayName.trim().lowercase()
                if (lowerName.isNotEmpty()) exactNameIndex.putIfAbsent(lowerName, id)

                val normName = EpgChannelMatcher.normalize(displayName)
                if (normName.isNotEmpty()) normNameIndex.putIfAbsent(normName, id)

                val coreName = EpgChannelMatcher.normalizeCore(displayName)
                if (coreName.isNotEmpty()) coreIndex.putIfAbsent(coreName, id)
            }
        }
    }

    /**
     * Kanalı öncelik sırasına göre EPG kanal ID'si ile eşler:
     * 1. `tvg-id` (`providerId`)
     * 2. `tvg-name` (`tvgName`)
     * 3. Kanal adı (`channelName`) ve normalize/Türkçe karakter toleranslı benzerlik
     */
    fun resolveChannelId(
        tvgId: String = "",
        tvgName: String = "",
        channelName: String = "",
    ): String? {
        // 1. Öncelik: M3U satırındaki tvg-id eşleşmesi
        val cleanId = tvgId.trim()
        if (cleanId.isNotEmpty()) {
            exactIdIndex[cleanId.lowercase()]?.let { return it }
            exactNameIndex[cleanId.lowercase()]?.let { return it }
            val normId = EpgChannelMatcher.normalize(EpgChannelMatcher.stripDomainSuffix(cleanId))
            if (normId.isNotEmpty()) {
                normIdIndex[normId]?.let { return it }
                normNameIndex[normId]?.let { return it }
            }
        }

        // 2. Öncelik: M3U satırındaki tvg-name eşleşmesi
        val cleanTvgName = tvgName.trim()
        if (cleanTvgName.isNotEmpty()) {
            exactIdIndex[cleanTvgName.lowercase()]?.let { return it }
            exactNameIndex[cleanTvgName.lowercase()]?.let { return it }
            val normTvgName = EpgChannelMatcher.normalize(cleanTvgName)
            if (normTvgName.isNotEmpty()) {
                normNameIndex[normTvgName]?.let { return it }
                normIdIndex[normTvgName]?.let { return it }
            }
            val coreTvgName = EpgChannelMatcher.normalizeCore(cleanTvgName)
            if (coreTvgName.isNotEmpty()) {
                coreIndex[coreTvgName]?.let { return it }
            }
        }

        // 3. Öncelik: Kanal adı benzerliği / normalize edilmiş isim eşleşmesi
        val cleanName = channelName.trim()
        if (cleanName.isNotEmpty()) {
            exactNameIndex[cleanName.lowercase()]?.let { return it }
            exactIdIndex[cleanName.lowercase()]?.let { return it }
            val normName = EpgChannelMatcher.normalize(cleanName)
            if (normName.isNotEmpty()) {
                normNameIndex[normName]?.let { return it }
                normIdIndex[normName]?.let { return it }
            }
            val coreName = EpgChannelMatcher.normalizeCore(cleanName)
            if (coreName.isNotEmpty()) {
                coreIndex[coreName]?.let { return it }
            }
        }

        return null
    }

    fun resolveChannelId(channel: PreviewChannel?): String? {
        if (channel == null) return null
        return resolveChannelId(
            tvgId = channel.providerId,
            tvgName = channel.tvgName,
            channelName = channel.name,
        )
    }

    fun programmesFor(channelId: String): List<EpgProgramme> =
        programmesByChannel[channelId]?.toList().orEmpty()

    fun nowNextForId(channelId: String?, nowEpochMs: Long): EpgNowNext {
        if (channelId.isNullOrBlank()) return EpgNowNext()
        val list = programmesByChannel[channelId] ?: return EpgNowNext(channelId = channelId)
        val nowProg = list.firstOrNull { it.isActiveAt(nowEpochMs) }
        val nextStartThreshold = nowProg?.endEpochMs ?: nowEpochMs
        val nextProg = list.firstOrNull { it.startEpochMs >= nextStartThreshold && it != nowProg }
        return EpgNowNext(channelId = channelId, now = nowProg, next = nextProg)
    }

    fun nowNext(
        tvgId: String = "",
        tvgName: String = "",
        channelName: String = "",
        nowEpochMs: Long,
    ): EpgNowNext {
        val resolvedId = resolveChannelId(tvgId, tvgName, channelName) ?: return EpgNowNext()
        return nowNextForId(resolvedId, nowEpochMs)
    }

    fun nowNext(channel: PreviewChannel?, nowEpochMs: Long): EpgNowNext {
        if (channel == null) return EpgNowNext()
        val resolvedId = resolveChannelId(channel) ?: return EpgNowNext()
        return nowNextForId(resolvedId, nowEpochMs)
    }
}

/**
 * Varsayılan / Örnek XMLTV EPG verisi (Temel ulusal, haber, spor, çocuk ve belgesel kanalları).
 */
object EpgSampleData {
    private data class ChannelTemplate(
        val id: String,
        val names: List<String>,
        val slots: List<Pair<String, String>>,
    )

    private val templates = listOf(
        ChannelTemplate(
            id = "trt1.tr",
            names = listOf("TRT 1", "TRT1", "TRT 1 HD"),
            slots = listOf(
                "Gece Sineması: Anadolu Hikayeleri" to "Türk sinemasının seçkin örnekleri.",
                "Sabah Haberleri" to "Günün ilk gelişmeleri ve hava durumu.",
                "Hayatımın Neşesi" to "Aile komedisi sabah kuşağı.",
                "Gündem Ötesi" to "Tarih, kültür ve bilim sohbetleri.",
                "Öğle Ana Haber" to "Türkiye ve dünyadan sıcak gelişmeler.",
                "Turp Gibi" to "Sağlıklı yaşam ve beslenme rehberi.",
                "Lingo Türkiye" to "Eğlenceli kelime yarışması.",
                "Ana Haber Bülteni" to "Günün öne çıkan başlıkları ve canlı bağlantılar.",
                "Gönül Dağı" to "Bozkırda bir Anadolu masalı, yeni bölüm.",
                "Pelin Çift ile Gündem Ötesi" to "Derinlemesine tarih ve kültür tartışmaları."
            )
        ),
        ChannelTemplate(
            id = "atv.tr",
            names = listOf("ATV", "ATV HD"),
            slots = listOf(
                "Gece Kuşağı: Kuruluş Osman" to "Tekrar bölümüyle ekranlarda.",
                "Kahvaltı Haberleri" to "Yeni güne başlarken sıcak başlıklar.",
                "Müge Anlı ile Tatlı Sert" to "Faili meçhul olaylar ve kayıplar aranıyor.",
                "ATV Gün Ortası" to "Öğle saatlerinde gelişmeler.",
                "Mutfak Bahane" to "Yemek ve eğlence dolu yarışma.",
                "Esra Erol'da" to "Hayatın içinden gerçek hikayeler.",
                "ATV Ana Haber" to "Cem Öğretir ile günün özeti.",
                "Kim Milyoner Olmak İster?" to "Bilgi ve heyecan dolu yarışma programı.",
                "Aldatmak" to "Dram dizisi özel bölümü.",
                "Gece Bülteni" to "Gece saatlerinde son gelişmeler."
            )
        ),
        ChannelTemplate(
            id = "kanald.tr",
            names = listOf("Kanal D", "Kanal D HD"),
            slots = listOf(
                "Yabancı Sinema" to "Aksiyon ve macera kuşağı.",
                "Çalar Saat Sabah" to "Günün ilk haberleri.",
                "Neler Oluyor Hayatta?" to "Gündemin nabzı ve yorumlar.",
                "Yaprak Dökümü" to "Unutulmaz klasik dizi.",
                "Gelinim Mutfakta" to "En iyi tabağı hazırlama mücadelesi.",
                "Arka Sokaklar (Özel)" to "Ekibin heyecan dolu operasyonları.",
                "Kanal D Ana Haber" to "Deniz Bayramoğlu ile Ana Haber.",
                "İnci Taneleri" to "Yılmaz Erdoğan'dan etkileyici bir hikaye.",
                "Arka Sokaklar" to "Polisiye ve aksiyon dolu yeni bölüm.",
                "Sıcak Gelişme" to "Gece haber özeti."
            )
        ),
        ChannelTemplate(
            id = "nowtv.tr",
            names = listOf("NOW TV", "NOW", "NOW HD", "FOX"),
            slots = listOf(
                "Gece Tekrarı: Kızıl Goncalar" to "Dram kuşağı tekrar bölümü.",
                "İlker Karagöz ile Çalar Saat" to "Sabah haberleri ve gazete manşetleri.",
                "Çağla ile Yeni Bir Gün" to "Sağlık, güzellik ve yaşam ipuçları.",
                "Yasak Elma" to "Gündüz kuşağı dizi tekrarı.",
                "En Hamarat Benim" to "Temizlik ve yemek yarışması.",
                "Fatih Savaş ile Sohbet" to "Günün anlam ve önemi.",
                "Selçuk Tepeli ile NOW Ana Haber" to "Gündemin çarpıcı dosyaları.",
                "Hudutsuz Sevda" to "Heyecan dolu yeni bölüm.",
                "Orta Sayfa" to "Ankara kulisleri ve siyaset gündemi.",
                "Gece Hattı" to "Günün son haberleri."
            )
        ),
        ChannelTemplate(
            id = "showtv.tr",
            names = listOf("Show TV", "Show", "Show TV HD"),
            slots = listOf(
                "Türk Sineması: Hababam Sınıfı" to "Yeşilçam klasikleri kuşağı.",
                "Bu Sabah" to "Günün sıcak başlıkları.",
                "Kuzey Yıldızı İlk Aşk" to "Karadeniz komedisi.",
                "Gelin Evi" to "Çeyiz ve ev dekorasyonu yarışması.",
                "Didem Arslan Yılmaz'la Vazgeçme" to "Gerçek olayların izinde.",
                "Güldür Güldür Show (Özel)" to "Kahkaha dolu skeçler.",
                "Show Ana Haber" to "Pınar Erbaş ile günün haberleri.",
                "Kızılcık Şerbeti" to "İki farklı ailenin çarpıcı hikayesi.",
                "Bahar" to "Umudun ve yeniden başlamanın öyküsü.",
                "Show Gece" to "Gece sinema kuşağı."
            )
        ),
        ChannelTemplate(
            id = "startv.tr",
            names = listOf("Star TV", "Star", "Star TV HD"),
            slots = listOf(
                "Gece Sineması" to "Dünya sinemasından seçmeler.",
                "Mesut Yar ile Bugün" to "Sabah gündemi ve konuklar.",
                "Sabahların Sultanı Seda Sayan" to "Müzik ve eğlence kuşağı.",
                "Kiralık Aşk" to "Romantik komedi dizisi.",
                "Söz" to "Aksiyon dizisi gündüz kuşağı.",
                "Çok Güzel Hareketler 2" to "Komedi skeçleri.",
                "Star Ana Haber" to "Nazlı Çelik ile Ana Haber.",
                "Yalı Çapkını" to "Antep'ten İstanbul'a uzanan hikaye.",
                "Ömer" to "Dram dizisi özel bölümü.",
                "Gece Kuşağı" to "Dizi tekrarları."
            )
        ),
        ChannelTemplate(
            id = "tv8.tr",
            names = listOf("TV8", "TV 8", "TV8 HD"),
            slots = listOf(
                "Survivor Ekstra (Tekrar)" to "Ada konseyi değerlendirmeleri.",
                "8'de Sağlık" to "Uzman doktorlarla sağlık rehberi.",
                "Müge ve Gülşen'le 2. Sayfa" to "Magazin dünyasından son dakika.",
                "MasterChef Türkiye (Özet)" to "Önceki bölümün en heyecanlı anları.",
                "Zuhal Topal'la Yemekteyiz" to "Haftanın en iyi ev sahibi yarışması.",
                "Survivor Panorama" to "Yarışmacı analizleri ve tahminler.",
                "MasterChef Türkiye: Ön Eleme" to "Şeflerin tadım değerlendirmesi.",
                "MasterChef Türkiye" to "Büyük ödül için mutfakta kıyasıya rekabet.",
                "O Ses Türkiye" to "Müzik ve eğlence yarışması.",
                "Survivor Ekstra" to "Canlı stüdyo tartışmaları."
            )
        ),
        ChannelTemplate(
            id = "kanal7.tr",
            names = listOf("Kanal 7", "Kanal 7 HD"),
            slots = listOf(
                "Gece Sohbetleri" to "Maneviyat ve huzur kuşağı.",
                "Kanal 7 Sabah" to "Günün ilk haberleri.",
                "Dr. Feridun Kunak Show" to "Doğal sağlık tarifleri.",
                "Nursel'le Evin Tadı" to "Yöresel yemek tarifleri.",
                "Bahar Kapıda" to "Anadolu'nun dört bir yanından insan hikayeleri.",
                "Yemin" to "Günlük dram dizisi.",
                "Kanal 7 Ana Haber" to "Günün gelişmeleri.",
                "Esaret" to "Heyecan dolu günlük dizi.",
                "Gündüz Gece" to "Türkü ve kültür yolculuğu.",
                "Mustafa Karataş ile Muhabbet Kapısı" to "Dini sorular ve cevaplar."
            )
        ),
        ChannelTemplate(
            id = "beyaztv.tr",
            names = listOf("Beyaz TV", "Beyaz TV HD"),
            slots = listOf(
                "Derin Futbol (Tekrar)" to "Futbol gündeminin nabzı.",
                "Uyan Türkiyem" to "Tahir Sarıkaya ile sabah haberleri.",
                "Söylemezsem Olmaz" to "Magazin gündemi.",
                "Nermin'in Enfes Mutfağı" to "Pratik ve lezzetli tarifler.",
                "Nur Viral ile Hayatta Her Şey Var" to "Uzman konuklarla yaşam rehberi.",
                "Türker Akıncı ile Şimdi Konuşalım" to "Gündem dosyaları.",
                "Beyaz Ana Haber" to "Günün öne çıkan gelişmeleri.",
                "Sinema Kuşağı" to "Aksiyon ve macera filmi.",
                "Beyaz Futbol" to "Süper Lig değerlendirmeleri ve tartışmalar.",
                "Gece Sineması" to "Yabancı sinema kuşağı."
            )
        ),
        ChannelTemplate(
            id = "360tv.tr",
            names = listOf("360 TV", "360", "TV 360"),
            slots = listOf(
                "Gece Belgeseli" to "Doğa ve keşif.",
                "Günaydın Türkiye" to "Sabah bülteni.",
                "Doktorunuz Ne Diyor?" to "Sağlık söyleşileri.",
                "Seksenler" to "Nostaljik aile komedisi.",
                "Gökyüzünden Türkiye" to "Anadolu'nun eşsiz manzaraları.",
                "Ben Bilirim (Özet)" to "Bilgi yarışması.",
                "360 Ana Haber" to "Günün haber özeti.",
                "Ben Bilirim" to "Alper Ateş ile genel kültür yarışması.",
                "Aileler Yarışıyor" to "Eğlenceli yarışma programı.",
                "Gece Kuşağı: Seksenler" to "Dizi tekrarı."
            )
        ),
        ChannelTemplate(
            id = "trthaber.tr",
            names = listOf("TRT Haber", "TRT Haber HD"),
            slots = listOf(
                "Gece Bakışı" to "Türkiye ve dünyadan gece bülteni.",
                "Dünden Yarına" to "Sabah öncesi haber turu.",
                "İlk Baskı" to "Gazete manşetleri ve sabah gündemi.",
                "Sabah Gündemi" to "Yurt genelinden canlı bağlantılar.",
                "Öğle Ana Haber" to "Günün ilk yarısında yaşananlar.",
                "Haber Stüdyosu" to "Ekonomi ve diplomasi gelişmeleri.",
                "Akşam Доsyası" to "Özel haber dosyaları.",
                "Ana Haber Bülteni" to "Günün en önemli gelişmeleri.",
                "Birinci Sayfa" to "Uzman konuklarla dış politika ve gündem analizi.",
                "Gece Ana Haber" to "Günün kapanış bülteni."
            )
        ),
        ChannelTemplate(
            id = "ahaber.tr",
            names = listOf("A Haber", "A Haber HD"),
            slots = listOf(
                "Gece Ajansı" to "Sıcak gelişmeler.",
                "Sabah Ajansı" to "Erkan Tan ile sabah gündemi.",
                "Ajans Bugün" to "Günün sıcak başlıkları.",
                "Ajans Gün Ortası" to "Öğle bülteni ve canlı bağlantılar.",
                "Memleket Meselesi" to "Gündem tartışmaları.",
                "Ajans Yeni Gün" to "Akşamüstü gelişmeleri.",
                "A Haber Ana Haber" to "Günün kapsamlı haber özeti.",
                "Arka Plan" to "Siyaset ve dış politika masası.",
                "Söz Teması" to "Özel konuklarla gündem değerlendirmesi.",
                "Gece Bülteni" to "Son dakika gelişmeleri."
            )
        ),
        ChannelTemplate(
            id = "ntv.tr",
            names = listOf("NTV", "NTV HD"),
            slots = listOf(
                "Gece Gündüz (Tekrar)" to "Kültür ve sanat rehberi.",
                "Güne Başlarken" to "Sabah haberleri ve hava durumu.",
                "Piyasa Ekranı" to "Borsa, döviz ve ekonomi açılışı.",
                "Günün İçinden" to "Sıcak gelişmeler ve muhabir bağlantıları.",
                "Öğle Bülteni" to "Gün ortası haber merkezi.",
                "Bakış" to "Ekonomi ve iş dünyası söyleşileri.",
                "NTV Akşam Haberleri" to "Günün özeti.",
                "Ana Haber Bülteni" to "Seda Öğretir ile NTV Ana Haber.",
                "Siyasi İşler" to "Ankara gündemi ve kulisler.",
                "Yüzde Yüz Futbol" to "Spor gündemi ve maç analizleri."
            )
        ),
        ChannelTemplate(
            id = "cnnturk.tr",
            names = listOf("CNN Türk", "CNN Turk", "CNN TÜRK"),
            slots = listOf(
                "Tarafsız Bölge (Tekrar)" to "Gündem tartışmaları.",
                "Yeni Gün" to "Sabah haber kuşağı.",
                "Parametre" to "Ekonomi ve piyasalar.",
                "Bugün" to "Gündemdeki sıcak gelişmeler.",
                "Gün Ortası" to "Öğle haber bülteni.",
                "CNN Türk Masası" to "Uzmanlarla gündem analizi.",
                "Akşam Haberleri" to "Günün öne çıkanları.",
                "Ana Haber" to "Fulya Öztürk ile Ana Haber.",
                "Tarafsız Bölge" to "Ahmet Hakan ile gündem tartışması.",
                "Gece Görüşü" to "Hande Fırat ile Ankara gündemi."
            )
        ),
        ChannelTemplate(
            id = "haberturk.tr",
            names = listOf("Habertürk", "Haberturk", "HaberTürk"),
            slots = listOf(
                "Teke Tek Bilim (Tekrar)" to "Bilim ve tarih sohbetleri.",
                "Gün Başlıyor" to "Sabah haber bülteni.",
                "Habertürk Manşet" to "Günün manşetleri ve analizler.",
                "Para Gündem" to "Ekonomi ve siyaset gündemi.",
                "Gün Ortası" to "Öğle bülteni.",
                "Haber Bülteni" to "Saat başı sıcak gelişmeler.",
                "Akşam Raporu" to "Günün ekonomi ve siyaset özeti.",
                "Habertürk Ana Haber" to "Mehmet Akif Ersoy ile Ana Haber.",
                "Açık ve Net" to "Canlı tartışma programı.",
                "Olaylar ve Görüşler" to "Gündem özel yayını."
            )
        ),
        ChannelTemplate(
            id = "halktv.tr",
            names = listOf("Halk TV", "Halk TV HD"),
            slots = listOf(
                "Sözüm Var (Tekrar)" to "Gece tartışma kuşağı.",
                "Yeni Bir Sabah" to "İsmail Küçükkaya ile sabah gündemi.",
                "Gündem Özel" to "Sıcak gelişmeler.",
                "Haber Masası" to "Gün ortası bağlantıları.",
                "Açıkça" to "Siyaset ve toplum dosyaları.",
                "Günün Raporu" to "Akşamüstü bülteni.",
                "Halk Ana Haber" to "Ece Üner ile Ana Haber.",
                "Kırmızı Çizgi" to "Gündem tartışmaları.",
                "Sansürsüz" to "Canlı stüdyo konuklarıyla analiz.",
                "Gece Haberleri" to "Günün son başlıkları."
            )
        ),
        ChannelTemplate(
            id = "sozcutv.tr",
            names = listOf("Sözcü TV", "Sozcu TV", "SZC TV"),
            slots = listOf(
                "Gece Özeti" to "Günün haber tekrarı.",
                "Sabah Kahvesi" to "Gazete başlıkları ve sabah haberleri.",
                "Para Politika" to "Ekonomi ve piyasa yorumları.",
                "Haber Saati" to "Gündem gelişmeleri.",
                "Gün Ortası" to "Öğle ana haber bülteni.",
                "Gündem Masası" to "Sıcak başlıklar.",
                "Öncesi Sonrası" to "Olayların perde arkası.",
                "Fatih Portakal ile Sözcü Ana Haber" to "Günün çarpıcı haberleri ve yorumlar.",
                "Arena" to "Araştırma dosyaları ve tartışma.",
                "Sözcü Gece" to "Kapanış bülteni."
            )
        ),
        ChannelTemplate(
            id = "trtspor.tr",
            names = listOf("TRT Spor", "TRT Spor HD"),
            slots = listOf(
                "Maç Özetleri" to "Avrupa ve Süper Lig özetleri.",
                "Spor Sabahı" to "Günün spor gelişmeleri.",
                "Spor Bülteni" to "Kulüplerden son haberler.",
                "Gündem Futbol" to "Antrenman notları ve transfer gündemi.",
                "Gün Ortası Spor" to "Öğle spor bülteni.",
                "Spor Stüdyosu" to "Branşlardan gelişmeler.",
                "İlk Baskı Spor" to "Akşam spor haberleri.",
                "Stadyum" to "Maç önü ve maç sonu canlı analizler.",
                "Teknik Analiz" to "Taktik tahtasında haftanın maçları.",
                "Son Sayfa" to "Taraftar mesajları ve gece spor gündemi."
            )
        ),
        ChannelTemplate(
            id = "aspor.tr",
            names = listOf("A Spor", "A Spor HD"),
            slots = listOf(
                "Son Sayfa (Canlı)" to "Gece spor gündemi.",
                "Sabah Sporu" to "Gazetelerin spor sayfaları.",
                "Spor Ajansı" to "Takımlardan canlı bağlantılar.",
                "Gün Ortası" to "Öğle spor bülteni.",
                "Transfer Raporu" to "Transfer dünyasından sıcak gelişmeler.",
                "Skor Tabelası" to "Haftanın maç programı.",
                "Ana Haber Spor" to "Günün spor özeti.",
                "Takım Oyunu" to "Süper Lig ve Avrupa kupası tartışmaları.",
                "90+1" to "Maç sonu değerlendirmeleri.",
                "Gece Hattı Spor" to "Son dakika spor haberleri."
            )
        ),
        ChannelTemplate(
            id = "trtcocuk.tr",
            names = listOf("TRT Çocuk", "TRT Cocuk"),
            slots = listOf(
                "Ninni Kuşağı" to "Minikler için uyku vakti.",
                "Ege ile Gaga" to "Doğa ve keşif dolu maceralar.",
                "Elif ve Arkadaşları" to "Okul öncesi eğitici çizgi dizi.",
                "Niloya" to "Şarkılar ve yeni keşifler.",
                "İbi" to "Matematik ve macera dolu yolculuk.",
                "Pırıl" to "Sayılarla eğlenceli problem çözme.",
                "Rafadan Tayfa" to "Mahallenin neşeli kahramanları.",
                "Akıllı Tavşan Momo" to "Ormanda yardımlaşma hikayeleri.",
                "Aslan" to "İcatlar ve eğlenceli deneyler.",
                "Masal Kuşağı" to "Uykudan önce masallar."
            )
        ),
        ChannelTemplate(
            id = "trtgenc.tr",
            names = listOf("TRT GENÇ", "TRT Genç", "TRT Genc"),
            slots = listOf(
                "Gece Kampüsü" to "Gençlik belgeseli.",
                "Sabah Enerjisi" to "Spor ve motivasyon.",
                "Bilim ve Teknoloji" to "Geleceğin teknolojileri.",
                "Dijital Dünya" to "Oyun, yazılım ve inovasyon.",
                "Kampüs Günlüğü" to "Üniversite dünyasından haberler.",
                "Kariyer Yolu" to "Meslek seçimi ve girişimcilik.",
                "E-Spor Arenası" to "Turnuva özetleri ve röportajlar.",
                "Genç Sahne" to "Müzik ve performans kuşağı.",
                "Sinema Kuşağı" to "Gençlik ve bilimkurgu filmi.",
                "Gece Frekansı" to "Müzik ve sohbet."
            )
        ),
        ChannelTemplate(
            id = "trtbelgesel.tr",
            names = listOf("TRT Belgesel", "TRT Belgesel HD"),
            slots = listOf(
                "Sessiz Tanıklar" to "Doğa tarihi belgeseli.",
                "Anadolu'nun Ustaları" to "Kaybolmaya yüz tutmuş meslekler.",
                "Yabani Hayat" to "Vahşi doğada yaşam mücadelesi.",
                "Su Savaşları" to "Dünya su kaynaklarının geleceği.",
                "Ailenin Yeni Üyesi" to "Farklı kültürlerde yaşam.",
                "Uzakların Ötesinde" to "Keşfedilmemiş coğrafyalar.",
                "Sıfır Atık" to "Çevre ve sürdürülebilirlik.",
                "Doğadaki İnsan" to "Serdar Kılıç ile doğada hayatta kalma.",
                "Savaşın Efsaneleri" to "Tarihin dönüm noktası savaşlar.",
                "Derin Okyanus" to "Deniz altı belgeseli."
            )
        ),
        ChannelTemplate(
            id = "trt4k.tr",
            names = listOf("TRT 4K", "TRT 4K UHD"),
            slots = listOf(
                "4K Doğa Manzaraları" to "Ultra HD doğa görüntüleri.",
                "Anadolu Mirası 4K" to "Türkiye'nin antik kentleri.",
                "Gökyüzünden İstanbul 4K" to "Boğaz ve tarihi yarımada.",
                "Mavi Tutku 4K" to "Ege ve Akdeniz kıyıları.",
                "Dünya Mirası 4K" to "UNESCO kültür varlıkları.",
                "Yeşil Anadolu 4K" to "Karadeniz yaylaları.",
                "Klasik Müzik Konseri 4K" to "Cumhurbaşkanlığı Senfoni Orkestrası.",
                "UEFA Maç Kuşağı 4K" to "Ultra HD stadyum heyecanı.",
                "Vahşi Yaşam 4K" to "Afrika savanları belgeseli.",
                "Gece Senfonisi 4K" to "Görsel şölen."
            )
        ),
        ChannelTemplate(
            id = "tlc.tr",
            names = listOf("TLC", "TLC Türkiye"),
            slots = listOf(
                "Suç Dosyası" to "Gerçek polisiye hikayeler.",
                "Evim Şahane" to "Dekorasyon ve yenileme.",
                "Pasta Kralı" to "Muhteşem pasta tasarımları.",
                "Hayalimdeki Gelinlik" to "Gelinlik seçimi heyecanı.",
                "Emlak Avcıları" to "Yeni ev arayışı.",
                "Büyük Değişim" to "Ev yenileme maratonu.",
                "Ağır Yaşamlar" to "Sağlıklı yaşama dönüş hikayeleri.",
                "Yabancı Sinema" to "TLC sinema kuşağı.",
                "Temizlik Avcıları" to "Zorlu evlerin dönüşümü.",
                "Gece Sineması" to "Gerilim ve gizem filmi."
            )
        ),
        ChannelTemplate(
            id = "dmax.tr",
            names = listOf("DMAX", "DMAX Türkiye"),
            slots = listOf(
                "Gece Tamircileri" to "Klasik araç restorasyonu.",
                "Nasıl Yapılır?" to "Endüstriyel üretim sırları.",
                "Hurda Avcıları" to "Antika ve hurda ticareti.",
                "Büyük Makineler" to "Dev mühendislik araçları.",
                "Depo Savaşları" to "Açık artırma heyecanı.",
                "Tamirat Tadilat" to "Klasik otomobillerin yeniden doğuşu.",
                "Vahşi Alaska" to "Zorlu doğa koşullarında yaşam.",
                "Altın Peşinde" to "Klondike altın madencileri.",
                "Ed Stafford ile Kurtuluş" to "Issız coğrafyalarda hayatta kalma.",
                "Aksiyon Kuşağı" to "Belgesel serisi."
            )
        ),
    )

    // 24 saat + ertesi gün 06:00'ya kadar 10 dilim:
    private val slotRanges = listOf(
        "20261023000000 +0300" to "20261023030000 +0300",
        "20261023030000 +0300" to "20261023060000 +0300",
        "20261023060000 +0300" to "20261023090000 +0300",
        "20261023090000 +0300" to "20261023120000 +0300",
        "20261023120000 +0300" to "20261023150000 +0300",
        "20261023150000 +0300" to "20261023180000 +0300",
        "20261023180000 +0300" to "20261023200000 +0300",
        "20261023200000 +0300" to "20261023223000 +0300",
        "20261023223000 +0300" to "20261024003000 +0300",
        "20261024003000 +0300" to "20261024060000 +0300",
    )

    val DEFAULT_XML: String by lazy {
        buildString(48_000) {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<tv generator-info-name="BelesTiviClone-F4">""")
            for (ch in templates) {
                appendLine("""  <channel id="${ch.id}">""")
                for (name in ch.names) {
                    appendLine("""    <display-name lang="tr">$name</display-name>""")
                }
                appendLine("""  </channel>""")
            }
            for (ch in templates) {
                for (i in slotRanges.indices) {
                    val (start, stop) = slotRanges[i]
                    val (title, desc) = ch.slots[i % ch.slots.size]
                    appendLine("""  <programme start="$start" stop="$stop" channel="${ch.id}">""")
                    appendLine("""    <title lang="tr">$title</title>""")
                    appendLine("""    <desc lang="tr">$desc</desc>""")
                    appendLine("""  </programme>""")
                }
            }
            appendLine("""</tv>""")
        }
    }
}
