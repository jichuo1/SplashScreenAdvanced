package com.SplashScreenAdvanced.xposedmodule.utils.update

import java.net.URI

/** Release notes use a bounded Markdown subset, rendered by native Views rather than remote HTML. */
internal object ReleaseNotesMarkdown {
    enum class Kind { PARAGRAPH, HEADING, LIST, QUOTE, CODE, RULE, TABLE }
    enum class Style { BOLD, ITALIC, STRIKE, CODE, LINK }
    data class Range(val start: Int, val end: Int, val style: Style, val destination: String? = null)
    data class Inline(val text: String, val ranges: List<Range> = emptyList())
    data class Block(
        val kind: Kind,
        val content: Inline = Inline(""),
        val level: Int = 0,
        val centered: Boolean = false,
        val rows: List<List<Inline>> = emptyList()
    )

    fun parse(source: String): List<Block> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<Block>()
        var centered = false
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> index++
                trimmed.matches(Regex("<div\\s+align=[\"']center[\"']\\s*>", RegexOption.IGNORE_CASE)) -> {
                    centered = true; index++
                }
                trimmed.equals("</div>", true) -> { centered = false; index++ }
                trimmed.equals("<details>", true) || trimmed.equals("</details>", true) -> index++
                trimmed.startsWith("<summary>", true) -> {
                    blocks += Block(Kind.HEADING, inline(trimmed.removeSurrounding("<summary>", "</summary>")), 3)
                    index++
                }
                fence.find(line) != null -> {
                    val marker = fence.find(line)!!.groupValues[1]
                    val code = mutableListOf<String>()
                    index++
                    while (index < lines.size && !closesFence(lines[index], marker)) code += lines[index++]
                    if (index < lines.size) index++
                    blocks += Block(Kind.CODE, Inline(code.joinToString("\n")))
                }
                index + 1 < lines.size && line.contains('|') && tableSeparator(lines[index + 1]) -> {
                    val rows = mutableListOf(tableCells(line).map(::inline))
                    index += 2
                    while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) {
                        rows += tableCells(lines[index++]).map(::inline)
                    }
                    blocks += Block(Kind.TABLE, rows = rows)
                }
                heading.find(line) != null -> {
                    val match = heading.find(line)!!
                    blocks += Block(Kind.HEADING, inline(match.groupValues[2].replace(Regex("\\s+#+\\s*$"), "")),
                        match.groupValues[1].length, centered)
                    index++
                }
                isRule(trimmed) -> { blocks += Block(Kind.RULE); index++ }
                list.find(line) != null -> {
                    val match = list.find(line)!!
                    val marker = match.groupValues[2]
                    var content = match.groupValues[3]
                    val task = Regex("^\\[([ xX])]\\s+").find(content)
                    val prefix = when {
                        task != null -> if (task.groupValues[1].equals("x", true)) "☑ " else "☐ "
                        marker.first().isDigit() -> marker.trimEnd(')', '.') + ". "
                        else -> "• "
                    }
                    if (task != null) content = content.drop(task.value.length)
                    blocks += Block(Kind.LIST, inline(prefix + content), match.groupValues[1].length / 2)
                    index++
                }
                trimmed.startsWith('>') -> {
                    val quote = mutableListOf<String>()
                    while (index < lines.size && lines[index].trimStart().startsWith('>')) {
                        quote += lines[index++].trimStart().drop(1).trimStart()
                    }
                    val alert = alertMarker.matchEntire(quote.first())?.groupValues?.get(1)
                    if (alert != null) quote[0] = "**${if (alert == "TIP") "💡" else "ℹ"} $alert**"
                    blocks += Block(Kind.QUOTE, inline(quote.joinToString("\n")))
                }
                else -> {
                    val paragraph = mutableListOf(line)
                    index++
                    while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines, index)) {
                        paragraph += lines[index++]
                    }
                    blocks += Block(Kind.PARAGRAPH, inline(paragraph.joinToString("\n")), centered = centered)
                }
            }
        }
        return blocks
    }

    fun inline(source: String): Inline = parseInline(source, 0)

    private fun parseInline(source: String, depth: Int): Inline {
        if (depth >= 12) return Inline(source)
        val output = StringBuilder()
        val ranges = mutableListOf<Range>()
        fun append(value: Inline) {
            val offset = output.length
            output.append(value.text)
            ranges += value.ranges.map { it.copy(start = it.start + offset, end = it.end + offset) }
        }
        // Recursive children carry their own ranges, so links can contain code or emphasis.
        fun child(value: String) = parseInline(value, depth + 1)
        var index = 0
        while (index < source.length) {
            if (source[index] == '\\' && source.getOrNull(index + 1) in escapable) {
                output.append(source[index + 1]); index += 2; continue
            }
            var codeLength = 0
            while (source.getOrNull(index + codeLength) == '`') codeLength++
            if (codeLength > 0) {
                val marker = "`".repeat(codeLength)
                val end = source.indexOf(marker, index + codeLength)
                if (end >= 0) {
                    val start = output.length
                    output.append(source.substring(index + codeLength, end))
                    ranges += Range(start, output.length, Style.CODE)
                    index = end + codeLength; continue
                }
                output.append(marker)
                index += codeLength; continue
            }
            val image = source.startsWith("![", index)
            if (source[index] == '[' || image) {
                val labelStart = index + if (image) 2 else 1
                val labelEnd = source.indexOf("](", labelStart)
                val urlEnd = if (labelEnd >= 0) linkEnd(source, labelEnd + 2) else -1
                if (urlEnd >= 0) {
                    val start = output.length
                    append(child(source.substring(labelStart, labelEnd)))
                    val destination = safeLink(source.substring(labelEnd + 2, urlEnd))
                    if (destination != null && !image) ranges += Range(start, output.length, Style.LINK, destination)
                    index = urlEnd + 1; continue
                }
            }
            val marker = emphasisMarkers.firstOrNull { source.startsWith(it, index) &&
                !(it == "_" && source.getOrNull(index - 1)?.isLetterOrDigit() == true) }
            if (marker != null) {
                val end = emphasisEnd(source, marker, index + marker.length)
                if (end > index + marker.length && !source[index + marker.length].isWhitespace()) {
                    val start = output.length
                    append(child(source.substring(index + marker.length, end)))
                    ranges += Range(start, output.length, when (marker) {
                        "***", "___", "**", "__" -> Style.BOLD
                        "~~" -> Style.STRIKE
                        else -> Style.ITALIC
                    })
                    if (marker.length == 3) ranges += Range(start, output.length, Style.ITALIC)
                    index = end + marker.length; continue
                }
            }
            if (source[index] == '<') {
                val end = source.indexOf('>', index + 1)
                if (end >= 0) {
                    val tag = source.substring(index + 1, end)
                    val destination = safeLink(tag)
                    if (destination != null) {
                        val start = output.length; output.append(tag)
                        ranges += Range(start, output.length, Style.LINK, destination)
                        index = end + 1; continue
                    }
                    if (tag.lowercase() in setOf("sub", "/sub", "br", "br/", "br /")) {
                        if (tag.startsWith("br", true)) output.append('\n')
                        index = end + 1; continue
                    }
                }
            }
            output.append(source[index++])
        }
        return Inline(output.toString(), ranges.filter { it.end > it.start })
    }

    internal fun safeLink(value: String): String? {
        val candidate = value.trim().removeSurrounding("<", ">")
        return runCatching {
            val uri = URI(candidate)
            candidate.takeIf { uri.scheme?.lowercase() in setOf("https", "http") &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null }
        }.getOrNull()
    }

    private fun linkEnd(value: String, start: Int): Int {
        var depth = 0
        var escaped = false
        for (index in start until value.length) {
            val character = value[index]
            if (!escaped) when (character) {
                '(' -> depth++
                ')' -> if (depth == 0) return index else depth--
            }
            escaped = !escaped && character == '\\'
        }
        return -1
    }

    private fun emphasisEnd(value: String, marker: String, start: Int): Int {
        var index = start
        while (index < value.length) {
            if (value[index] == '\\') { index += 2; continue }
            if (value[index] != marker.first()) { index++; continue }
            var count = 1
            while (value.getOrNull(index + count) == marker.first()) count++
            val end = index + count - marker.length
            val inWord = marker.first() == '_' && value.getOrNull(index - 1)?.isLetterOrDigit() == true &&
                value.getOrNull(index + count)?.isLetterOrDigit() == true
            if (count >= marker.length && !(marker.length == 1 && count == 2) && !inWord &&
                value.getOrNull(end - 1)?.isWhitespace() == false) return end
            index += count
        }
        return -1
    }

    private fun tableCells(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var code = false
        var escaped = false
        line.trim().removePrefix("|").removeSuffix("|").forEach { character ->
            if (character == '`' && !escaped) code = !code
            if (character == '|' && !code && !escaped) { cells += cell.toString().trim(); cell.clear() }
            else cell.append(character)
            escaped = !escaped && character == '\\'
        }
        cells += cell.toString().trim()
        return cells
    }

    private fun tableSeparator(line: String) = tableCells(line).let { cells ->
        cells.size >= 2 && cells.all { it.matches(Regex(":?-{3,}:?")) }
    }
    private fun closesFence(line: String, marker: String): Boolean {
        val value = line.trim()
        return value.length >= marker.length && value.all { it == marker.first() }
    }
    private fun startsBlock(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        return heading.containsMatchIn(line) || list.containsMatchIn(line) || fence.containsMatchIn(line) ||
            isRule(line.trim()) || line.trimStart().startsWith('>') || line.trimStart().startsWith('<') ||
            (index + 1 < lines.size && line.contains('|') && tableSeparator(lines[index + 1]))
    }
    private val escapable = "\\`*_{}[]()#+-.!|>~".toSet()
    private val emphasisMarkers = listOf("***", "___", "**", "__", "~~", "*", "_")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})")
    private val heading = Regex("^ {0,3}(#{1,6})\\s+(.+)$")
    private val list = Regex("^(\\s*)([-+*]|\\d+[.)])\\s+(.+)$")
    private val alertMarker = Regex("\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]")
    private fun isRule(value: String): Boolean {
        val marker = value.firstOrNull() ?: return false
        return marker in "-*_" && value.count { it == marker } >= 3 && value.all { it == marker || it.isWhitespace() }
    }
}
