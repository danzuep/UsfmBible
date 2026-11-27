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

import com.github.danzuep.usfmbible.core.HEADING
import com.github.danzuep.usfmbible.core.NOTE_MARK
import com.github.danzuep.usfmbible.core.PARAGRAPH
import com.github.danzuep.usfmbible.core.RUBY_MARK
import com.github.danzuep.usfmbible.core.RUBY_SEPARATOR
import com.github.danzuep.usfmbible.core.removeRuby

/** The 66 book canon, in the app's order (0-based book index = position in this list). */
val BOOK_CODES: List<String> = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG ISA JER LAM " +
        "EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL " +
        "1TH 2TH 1TI 2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split(" ")

/** A book of scripture, independent of the markup (USFM, USX, ...) it was read from. Verse numbers are 1-based. */
class ScriptureBook(val code: String, val name: String?, val chapters: Map<Int, Map<Int, String>>)

private val NOTE = Regex("""\\(f|fe|x|ef|ex)\b[\s\S]*?\\\1\*""")
private val NOTE_CALLER = Regex("""^\s*[^\s\\]*\s*""")
private val WORD_ATTRIBUTES = Regex("""\|[^\\]*?(?=\\\+?w\*)""")
private val RUBY = Regex("""\\rb\s+([\s\S]*?)(?:\|([^\\]*))?\\rb\*""")
private val ATTRIBUTE = Regex("""\w+="[^"]*"(?:\s+\w+="[^"]*")*""")
private val MARKER = Regex("""\\\+?[a-z0-9-]+\*|\\\+?[a-z0-9-]+\s?""")
private val WHITESPACE = Regex("""\s+""")
private val VERSE = Regex("""\\v\s+(\d+)(?:-(\d+))?[a-z]?\s*""")
private val NON_SCRIPTURE = Regex("""^\\(?:ms\d?|mr|s\d?|sr|r|d|sp|cl|cd|cp|qa|rem|h|toc\d|mt\d?|imt\d?|is\d?|ip|id|b)\b""")

/** Strips word attributes and markers, keeping the scripture text. Ruby (\rb base|reading\rb*) becomes [RUBY_MARK]ed text. Notes become [NOTE_MARK]ed footnotes or cross-references. */
fun stripUsfmMarkup(text: String): String =
        text.replace(NOTE) { noteMarkup(it) }.removeMarkers()

private fun String.removeMarkers(): String =
        replace(RUBY) { rubyMarkup(it) }.replace(WORD_ATTRIBUTES, "").replace(MARKER, "").replace(WHITESPACE, " ").trim()

/** The base with its reading, or just the base when the reading is empty or attributes (e.g. strong="H430"). */
private fun rubyMarkup(match: MatchResult): String {
    val base = match.groupValues[1]
    val reading = match.groupValues[2].trim()
    return if (base.isBlank() || reading.isEmpty() || ATTRIBUTE.matches(reading)) base else "$RUBY_MARK$base$RUBY_SEPARATOR$reading$RUBY_MARK"
}

/** A footnote (\f) or cross-reference (\x) without its caller, e.g. "1:1 Some text." */
private fun noteMarkup(match: MatchResult): String {
    val marker = match.groupValues[1]
    val body = match.value.removePrefix("\\$marker").removeSuffix("\\$marker*")
            .replace(NOTE_CALLER, "").removeMarkers().removeRuby()
    if (body.isEmpty()) return ""
    val kind = if (marker.contains('x')) 'x' else 'f'
    return "$NOTE_MARK$kind$body$NOTE_MARK"
}

private val HEADING_LINE = Regex("""^\\(?:s\d?|ms\d?)\s+(.*)""")
private val PARAGRAPH_LINE = Regex("""^\\(?:p|m|pi\d?|mi|pm[a-z]?|po|pr|cls|li\d?|lim\d?|ph\d?)(?:\s+(.*))?$""")
private val POETRY_LINE = Regex("""^\\q(\d?)(?:\s+(.*))?$""")

/** What a USFM file says about its translation; any part may be missing. */
class UsfmMetadata(val name: String?, val shortName: String?, val language: String?)

private val LANGUAGE_CODE = Regex("""[A-Za-z]{2,3}""")

/**
 * Reads the translation's abbreviation and language from the `\id` line, e.g. `\id GEN EN_ULT en_English_ltr` (the
 * Door43/Paratext convention) gives ULT and `en`. USFM has no marker for the full name, so the name is the abbreviation.
 * Returns null when the file defines neither.
 */
fun parseUsfmMetadata(content: String): UsfmMetadata? {
    val id = content.removePrefix("\uFEFF").lineSequence().map { it.trim() }.firstOrNull { it.startsWith("\\id ") } ?: return null
    val tokens = id.split(WHITESPACE).drop(2)
    val project = tokens.getOrNull(0)?.split('_', '-')?.takeIf { it.size >= 2 && LANGUAGE_CODE.matches(it[0]) }
    val languageTag = tokens.getOrNull(1)?.split('_', '-')?.takeIf { it.size >= 2 }?.get(0)?.takeIf { LANGUAGE_CODE.matches(it) }
    val language = (languageTag ?: project?.get(0))?.lowercase()
    val shortName = project?.drop(1)?.joinToString("_")?.takeIf { Regex("[A-Za-z0-9_]+").matches(it) }
    if (language == null && shortName == null) return null
    return UsfmMetadata(shortName, shortName, language)
}

private val FILE_NAME_SEPARATORS = charArrayOf('_', '-', ' ', '.')
private val CAMEL_CASE_NAME = Regex("""([a-z]{2,3})([A-Za-z0-9]+)""")
private val LOCALE_PART = Regex("""[A-Za-z]{2}|[A-Za-z]{4}|\d{3}""")

/** Replaces characters a table name can't hold with underscores, then drops leading digits and underscores, e.g. "63_1JN" becomes "JN". */
fun cleanShortName(shortName: String): String =
    shortName.trim().replace(Regex("[^A-Za-z0-9_]"), "_").trimStart { it.isDigit() || it == '_' }

/**
 * Guesses the language and abbreviation from a file name (without extension) such as `cmn_ncvt_zh-HK`, `cmn-ncvt` or
 * `engWebp`: the first part is the language code, an optional locale (`zh-HK`) may end the name, and what is between
 * is the abbreviation, upper-cased. Returns null when the name does not fit.
 */
fun parseFileNameMetadata(fileName: String): UsfmMetadata? {
    val parts = fileName.split(*FILE_NAME_SEPARATORS).filter { it.isNotEmpty() }
    val language: String
    var middle: List<String>
    if (parts.size >= 2) {
        language = parts[0]
        middle = parts.drop(1)
        // A trailing locale is a language and a region, or just a region.
        if (middle.size >= 3 && LANGUAGE_CODE.matches(middle[middle.size - 2]) && LOCALE_PART.matches(middle.last())) middle = middle.dropLast(2)
        else if (middle.size >= 2 && LOCALE_PART.matches(middle.last())) middle = middle.dropLast(1)
    } else {
        val match = parts.firstOrNull()?.let { CAMEL_CASE_NAME.matchEntire(it) } ?: return null
        language = match.groupValues[1]
        middle = listOf(match.groupValues[2])
    }
    if (!LANGUAGE_CODE.matches(language)) return null
    val abbreviation = middle.joinToString("_").uppercase().takeIf { it.isNotEmpty() } ?: return null
    return UsfmMetadata(abbreviation, abbreviation, language.lowercase())
}

/**
 * Parses USFM into verses that keep their structure, see [PARAGRAPH] and [HEADING]: paragraph markers, section
 * headings and poetry lines are stored in the verse text, which readers that don't care remove with `stripStructure()`.
 */
fun parseUsfm(content: String): ScriptureBook {
    var code: String? = null
    var name: String? = null
    val chapters = mutableMapOf<Int, MutableMap<Int, String>>()
    var chapter = 0
    var verse = -1
    // The break and heading that go in front of the next text.
    var pendingBreak = ""
    var pendingHeading: String? = null

    for (rawLine in content.removePrefix("\uFEFF").lines()) {
        var line = rawLine.trim()
        when {
            line.isEmpty() -> continue
            line.startsWith("\\id ") -> { code = line.split(WHITESPACE)[1].uppercase(); continue }
            line.startsWith("\\toc2 ") || (line.startsWith("\\h ") && name == null) -> {
                name = stripUsfmMarkup(line.substringAfter(' ')).removeRuby().ifEmpty { name }
                continue
            }
            line.startsWith("\\c ") -> {
                chapter = line.split(WHITESPACE)[1].toInt()
                verse = -1
                pendingBreak = ""
                chapters.getOrPut(chapter) { mutableMapOf() }
                continue
            }
        }

        val headingLine = HEADING_LINE.find(line)
        if (headingLine != null) {
            pendingHeading = stripUsfmMarkup(headingLine.groupValues[1]).ifEmpty { null }
            pendingBreak = PARAGRAPH.toString()
            continue
        }
        if (NON_SCRIPTURE.containsMatchIn(line) || chapter == 0) continue

        val paragraph = PARAGRAPH_LINE.find(line)
        val poetry = POETRY_LINE.find(line)
        if (paragraph != null) {
            pendingBreak = PARAGRAPH.toString()
            line = paragraph.groupValues[1]
        } else if (poetry != null) {
            if (pendingBreak != PARAGRAPH.toString()) {
                pendingBreak = "\n" + "\u2003".repeat(((poetry.groupValues[1].toIntOrNull() ?: 1) - 1).coerceAtLeast(0))
            }
            line = poetry.groupValues[2]
        }

        val verses = chapters.getValue(chapter)
        fun append(text: String) {
            val cleaned = stripUsfmMarkup(text)
            if (verse < 0 || cleaned.isEmpty()) return

            val heading = pendingHeading?.let { "$HEADING$it$HEADING" } ?: ""
            val existing = verses.getValue(verse)
            verses[verse] = when {
                existing.isEmpty() -> heading + pendingBreak + cleaned
                else -> existing + heading + pendingBreak.ifEmpty { " " } + cleaned
            }
            pendingHeading = null
            pendingBreak = ""
        }

        var cursor = 0
        for (match in VERSE.findAll(line)) {
            append(line.substring(cursor, match.range.first))
            verse = match.groupValues[1].toInt()
            verses[verse] = ""
            // Bridged verses (e.g. 2-3): the text lives in the first verse.
            val end = match.groupValues[2].ifEmpty { null }?.toInt() ?: verse
            for (bridged in verse + 1..end) verses[bridged] = ""
            cursor = match.range.last + 1
        }
        append(line.substring(cursor))
    }
    return ScriptureBook(requireNotNull(code) { "Missing \\id marker" }, name, chapters)
}

/** Parses a zip entry as a USFM book of the canon in [BOOK_CODES], or returns null if it is none. */
fun readUsfmBook(name: String, content: ByteArray): ScriptureBook? {
    val lowercaseName = name.lowercase()
    // Zips made on a Mac carry "__MACOSX/._*.usfm" resource forks, and any file that is not valid USFM must not fail the rest.
    if (!(lowercaseName.endsWith(".usfm") || lowercaseName.endsWith(".sfm")) || lowercaseName.startsWith("__macosx/") || lowercaseName.substringAfterLast('/').startsWith("._")) return null
    val book = runCatching { parseUsfm(content.decodeToString()) }.getOrNull() ?: return null
    return book.takeIf { it.code in BOOK_CODES }
}

/** Reads the USFM books out of the (file name, bytes) entries of a zip as published by eBible.org, keyed by USFM code. Only books of the canon in [BOOK_CODES] are kept. */
fun readUsfmBooks(entries: Sequence<Pair<String, ByteArray>>): Map<String, ScriptureBook> {
    val books = hashMapOf<String, ScriptureBook>()
    for ((name, content) in entries) readUsfmBook(name, content)?.let { books[it.code] = it }
    return books
}

/**
 * Builds a [RemoteTranslation] from [books]. Books and chapters missing from the source (e.g. New Testament only
 * translations) are left empty. Verses missing inside a chapter (e.g. omitted by a critical text) become empty strings
 * to keep the numbering aligned.
 */
fun toRemoteTranslation(translationInfo: RemoteTranslationInfo, books: Map<String, ScriptureBook>): RemoteTranslation {
    require(books.isNotEmpty()) { "No books found for ${translationInfo.shortName}" }

    val bookNames = BOOK_CODES.map { code -> books[code]?.name ?: code }
    val verses = hashMapOf<Pair<Int, Int>, List<String>>()
    BOOK_CODES.forEachIndexed { bookIndex, code ->
        books[code]?.chapters?.forEach { (chapter, chapterVerses) ->
            val last = chapterVerses.keys.maxOrNull() ?: return@forEach
            verses[Pair(bookIndex, chapter - 1)] = (1..last).map { chapterVerses[it] ?: "" }
        }
    }
    return RemoteTranslation(translationInfo, bookNames, bookNames, verses)
}
