/*
 * Copyright (C) 2026 Daniel Collingwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.github.danzuep.usfmbible.core.repository.remote

/**
 * A block of a markdown document: a paragraph, a heading of [level] when [heading] is set, a list item, a [code] block or a [rule].
 * [indent] is the list nesting depth and [quote] the block quote depth. Inline markup (emphasis, code, links) stays as markdown.
 */
data class CopyrightBlock(
    val text: String,
    val heading: Boolean = false,
    val level: Int = 1,
    val indent: Int = 0,
    val quote: Int = 0,
    val code: Boolean = false,
    val rule: Boolean = false
)

private val HIDDEN_HTML = Regex("""<(script|style|head|title)\b[\s\S]*?</\1\s*>|<!--[\s\S]*?-->""", RegexOption.IGNORE_CASE)
private val ANCHOR = Regex("""<a\b[^>]*?href\s*=\s*["']([^"']*)["'][^>]*>([\s\S]*?)</a\s*>""", RegexOption.IGNORE_CASE)
private val HEADING_TAG = Regex("""<h[1-6]\b[^>]*>([\s\S]*?)</h[1-6]\s*>""", RegexOption.IGNORE_CASE)
private val BLOCK_TAG = Regex("""<(?:p|div|li|tr|ul|ol|table|blockquote|hr)\b[^>]*>|</(?:p|div|li|tr|ul|ol|table|blockquote)\s*>""", RegexOption.IGNORE_CASE)
private val LIST_ITEM = Regex("""<li\b[^>]*>""", RegexOption.IGNORE_CASE)
private val LINE_BREAK = Regex("""<br\b[^>]*>""", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("""<[^>]*>""")
private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")
private val SPACES = Regex("""[ \t\r\n\u00A0]+""")
private val ABSOLUTE_LINK = Regex("""^(?:https?:|mailto:)""", RegexOption.IGNORE_CASE)
private val NAVIGATION_LABELS = setOf("^", "<", ">")
private const val GENERATOR_NOTE = "HTML generated with"
private const val HEADING_MARK = "\u0001"
private const val BLOCK_MARK = "\u0002"
private const val ITEM_MARK = "\u0003"
private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "copy" to "©", "reg" to "®",
        "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "hellip" to "…", "middot" to "·"
)

private fun decodeEntities(text: String): String = text.replace(ENTITY) { match ->
    val name = match.groupValues[1]
    when {
        name.startsWith("#x") -> name.drop(2).toIntOrNull(16)?.let(::codePointString)
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointString)
        else -> NAMED_ENTITIES[name]
    } ?: match.value
}

private fun codePointString(codePoint: Int): String? = when (codePoint) {
    in 0..0xFFFF -> codePoint.toChar().toString()
    in 0x10000..0x10FFFF -> (codePoint - 0x10000).let { charArrayOf((0xD800 + (it shr 10)).toChar(), (0xDC00 + (it and 0x3FF)).toChar()).concatToString() }
    else -> null
}

/**
 * Converts the `copr.htm` page of an eBible.org translation into markdown, so that it can be stored and shown offline:
 * headings become `#`, list items `-` and every other block of text a paragraph. Links become `[label](url)`.
 */
fun copyrightHtmlToMarkdown(html: String): String {
    val text = html.replace(HIDDEN_HTML, " ")
            .replace(ANCHOR) { match ->
                val label = match.groupValues[2].replace(ANY_TAG, "").trim()
                val href = match.groupValues[1].trim()
                if (!ABSOLUTE_LINK.containsMatchIn(href) || label.isEmpty()) label else "[$label]($href)"
            }
            .replace(HEADING_TAG) { "$BLOCK_MARK$HEADING_MARK${it.groupValues[1]}$BLOCK_MARK" }
            .replace(LIST_ITEM) { "$BLOCK_MARK$ITEM_MARK" }
            .replace(BLOCK_TAG, BLOCK_MARK)
            .replace(LINE_BREAK, " ")
            .replace(ANY_TAG, "")
    return decodeEntities(text).split(BLOCK_MARK).mapNotNull { raw ->
        val body = raw.removePrefix(HEADING_MARK).removePrefix(ITEM_MARK).replace(SPACES, " ").trim()
        when {
            body.isEmpty() || body in NAVIGATION_LABELS || body.startsWith(GENERATOR_NOTE) -> null
            raw.startsWith(HEADING_MARK) -> "# $body"
            raw.startsWith(ITEM_MARK) -> "- $body"
            else -> body
        }
    }.joinToString("\n\n")
}

/** Reads the markdown made by [copyrightHtmlToMarkdown] back into blocks of text. */
fun parseCopyrightMarkdown(markdown: String): List<CopyrightBlock> = parseCopyrightMarkdown(markdown.lineSequence())

/** The longest text of a block; longer paragraphs are split so that no single view has to lay out a huge text. */
const val MAX_BLOCK_LENGTH = 2000

private val ATX_HEADING = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$""")
private val CODE_FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
private val THEMATIC_BREAK = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
private val SETEXT_UNDERLINE = Regex("""^ {0,3}(=+|-+)[ \t]*$""")
private val LIST_ITEM_START = Regex("""^( *)([-*+]|\d{1,9}[.)])[ \t]+(\S.*)$""")
private val TASK_ITEM = Regex("""^\[([ xX])\] +(.*)$""")
private val TABLE_DELIMITER = Regex("""^\|(?:[ \t]*:?-+:?[ \t]*\|)+$""")
private val QUOTE_MARKER = Regex("""^ {0,3}> ?""")

/**
 * Reads [lines] one at a time, so large files never need to be held as a single string or laid out as a single view.
 * Covers the everyday CommonMark blocks: ATX and setext headings, paragraphs (lines are joined), bullet and numbered
 * lists (with task checkboxes), nested block quotes, fenced code and thematic breaks. Table rows become one line each
 * with cells separated by "│". Indented code, HTML and reference definitions are left as text.
 */
fun parseCopyrightMarkdown(lines: Sequence<String>): List<CopyrightBlock> {
    val blocks = mutableListOf<CopyrightBlock>()
    var text: StringBuilder? = null
    var template = CopyrightBlock("")
    var plainParagraph = false
    var fence: String? = null
    var hardBreak = false
    val code = StringBuilder()

    fun flush() {
        text?.let { blocks += splitBlock(it.toString(), template) }
        text = null
    }

    fun flushCode() {
        if (code.isNotEmpty()) blocks += splitBlock(code.toString().trimEnd('\n'), CopyrightBlock("", code = true))
        code.clear()
    }

    for (line in lines) {
        val open = fence
        if (open != null) {
            val trimmed = line.trim()
            if (trimmed.length >= open.length && trimmed.all { it == open[0] }) {
                fence = null
                flushCode()
            } else code.append(line.trimEnd()).append('\n')
            continue
        }
        if (line.isBlank()) {
            flush()
            continue
        }
        var content = line
        var quote = 0
        while (true) {
            val marker = QUOTE_MARKER.find(content) ?: break
            content = content.substring(marker.range.last + 1)
            quote++
        }
        if (content.isBlank()) {
            flush()
            continue
        }
        if (text != null && template.quote != quote) flush()
        val trimmed = content.trim()
        val underline = SETEXT_UNDERLINE.find(content)
        val atx = ATX_HEADING.find(content)
        val list = LIST_ITEM_START.find(content)
        when {
            CODE_FENCE.containsMatchIn(content) -> {
                flush()
                fence = CODE_FENCE.find(content)!!.groupValues[1]
            }
            atx != null -> {
                flush()
                if (atx.groupValues[2].isNotEmpty()) blocks += splitBlock(atx.groupValues[2], CopyrightBlock("", heading = true, level = atx.groupValues[1].length, quote = quote))
            }
            underline != null && plainParagraph && text != null -> {
                val level = if (underline.groupValues[1][0] == '=') 1 else 2
                template = template.copy(heading = true, level = level)
                flush()
            }
            THEMATIC_BREAK.containsMatchIn(content) -> {
                flush()
                blocks += CopyrightBlock("", rule = true, quote = quote)
            }
            trimmed.length > 1 && trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                flush()
                if (!TABLE_DELIMITER.matches(trimmed)) {
                    val cells = trimmed.trim('|').split('|').joinToString(" │ ") { it.trim() }
                    blocks += splitBlock(cells, CopyrightBlock("", quote = quote))
                }
            }
            list != null -> {
                flush()
                val marker = list.groupValues[2]
                val indent = minOf(list.groupValues[1].length / 2, MAX_INDENT)
                val task = TASK_ITEM.find(list.groupValues[3].trim())
                val bullet = when {
                    task != null -> if (task.groupValues[1] == " ") "☐ " else "☑ "
                    marker[0].isDigit() -> "$marker "
                    indent == 0 -> "• "
                    else -> "◦ "
                }
                text = StringBuilder(bullet).append(task?.groupValues?.get(2) ?: list.groupValues[3].trim())
                template = CopyrightBlock("", indent = indent, quote = quote)
                plainParagraph = false
            }
            else -> {
                val current = text
                if (current == null) {
                    text = StringBuilder(trimmed)
                    template = CopyrightBlock("", quote = quote)
                    plainParagraph = true
                } else {
                    val backslashBreak = current.endsWith("\\")
                    if (backslashBreak) current.setLength(current.length - 1)
                    current.append(if (backslashBreak || hardBreak) "\n" else " ").append(trimmed)
                }
            }
        }
        hardBreak = line.endsWith("  ")
    }
    flush()
    flushCode()
    return blocks
}

private const val MAX_INDENT = 3

private fun splitBlock(text: String, template: CopyrightBlock): List<CopyrightBlock> {
    if (text.length <= MAX_BLOCK_LENGTH) return listOf(template.copy(text = text))
    val parts = mutableListOf<CopyrightBlock>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + MAX_BLOCK_LENGTH, text.length)
        // Break after a space or line break if there is one, so words (and short links) stay whole.
        if (end < text.length) text.lastIndexOfAny(charArrayOf(' ', '\n'), end).takeIf { it > start }?.let { end = it + 1 }
        parts += template.copy(text = text.substring(start, end).trim())
        start = end
    }
    return parts.filter { it.text.isNotEmpty() }
}
