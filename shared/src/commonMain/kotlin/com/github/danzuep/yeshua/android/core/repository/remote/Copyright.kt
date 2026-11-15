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


package com.github.danzuep.yeshua.android.core.repository.remote

/** A paragraph of a translation's copyright notice, or a heading when [heading] is set. Links stay as markdown, `[label](url)`. */
data class CopyrightBlock(val text: String, val heading: Boolean = false)

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
fun parseCopyrightMarkdown(markdown: String): List<CopyrightBlock> = markdown.lines().mapNotNull { line ->
    val text = line.trim()
    when {
        text.isEmpty() -> null
        text.startsWith("#") -> CopyrightBlock(text.trimStart('#').trim(), heading = true)
        text.startsWith("- ") -> CopyrightBlock("• ${text.drop(2)}")
        else -> CopyrightBlock(text)
    }
}
