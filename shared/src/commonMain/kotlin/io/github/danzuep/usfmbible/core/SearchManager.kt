/*
 * Copyright (C) 2023 Xizhi Zhu
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

package io.github.danzuep.usfmbible.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import io.github.danzuep.usfmbible.core.repository.BibleReadingRepository
import io.github.danzuep.usfmbible.core.repository.VerseAnnotationRepository
import io.github.danzuep.usfmbible.core.repository.remote.BOOK_CODES
import io.github.danzuep.usfmbible.utils.firstNotEmpty
import io.github.danzuep.usfmbible.utils.composeUnicode
import io.github.danzuep.usfmbible.utils.isWordCharacterAt
import io.github.danzuep.usfmbible.utils.isWordCharacterBefore
import io.github.danzuep.usfmbible.utils.lowercaseForUser

class SearchManager(
        private val bibleReadingRepository: BibleReadingRepository,
        private val bookmarkRepository: VerseAnnotationRepository<Bookmark>,
        private val highlightRepository: VerseAnnotationRepository<Highlight>,
        private val noteRepository: VerseAnnotationRepository<Note>
) {
    private val searchConfiguration: MutableStateFlow<SearchConfiguration> = MutableStateFlow(SearchConfiguration(
            includeOldTestament = true, includeNewTestament = true, includeBookmarks = true, includeHighlights = true, includeNotes = true, wholeWords = true))

    fun configuration(): Flow<SearchConfiguration> = searchConfiguration

    fun saveConfiguration(config: SearchConfiguration) {
        searchConfiguration.value = config
    }

    suspend fun search(query: String): SearchResult = withContext(Dispatchers.Default) {
        val keyword = query.normalizeQuery()
        val config = searchConfiguration.value
        val currentTranslation = bibleReadingRepository.currentTranslation.firstNotEmpty()
        if (config.searchByReference) {
            parseReference(
                keyword,
                bibleReadingRepository.readBookNames(currentTranslation),
                bibleReadingRepository.readBookShortNames(currentTranslation)
            )?.let { reference ->
                val referencedVerses = bibleReadingRepository.readVerses(currentTranslation, reference.bookIndex, reference.chapterIndex)
                        .filter { verse -> reference.verseIndexes?.contains(verse.verseIndex.verseIndex) ?: true }
                // The chapter comes straight from the repository, so the structure marks (paragraph, heading) must be removed here.
                return@withContext SearchResult(referencedVerses.map { it.stripStructure() }, emptyList(), emptyList(), emptyList(), byReference = true)
            }
        }
        val verses = async {
            bibleReadingRepository.search(
                    VerseSearchQuery(currentTranslation, keyword, config.includeOldTestament, config.includeNewTestament, config.wholeWords)
            )
        }
        val bookmarks = async {
            if (config.includeBookmarks) {
                searchAnnotations(bookmarkRepository, currentTranslation, keyword, config.wholeWords)
            } else {
                emptyList()
            }
        }
        val highlights = async {
            if (config.includeHighlights) {
                searchAnnotations(highlightRepository, currentTranslation, keyword, config.wholeWords)
            } else {
                emptyList()
            }
        }
        val notes = async {
            if (config.includeNotes) {
                searchNotes(currentTranslation, keyword, config.wholeWords)
            } else {
                emptyList()
            }
        }
        val foundVerses = verses.await()
        return@withContext SearchResult(foundVerses.take(MAX_SEARCH_RESULTS), bookmarks.await(), highlights.await(), notes.await(),
                bookIndex = keyword.trim().takeIf { it.length == 3 }?.let { code ->
                    BOOK_CODES.indexOfFirst { it.equals(code, ignoreCase = true) }.takeIf { it >= 0 }
                },
                truncated = foundVerses.size > MAX_SEARCH_RESULTS)
    }

    private suspend fun <V : VerseAnnotation> searchAnnotations(
            verseAnnotationRepository: VerseAnnotationRepository<V>, currentTranslation: String, keyword: String, wholeWords: Boolean
    ): List<Pair<V, Verse>> =
            verseAnnotationRepository.read(Constants.SORT_BY_BOOK)
                    .takeIf { it.isNotEmpty() }
                    ?.let { annotations ->
                        val keywords = keyword.toKeywords()
                        val verses = bibleReadingRepository.readVerses(currentTranslation, annotations.map { it.verseIndex })
                        annotations.mapNotNull { annotation ->
                            // Annotations on verses that are empty or missing in this translation are intentionally left out of the results.
                            verses[annotation.verseIndex]
                                    ?.takeIf { it.text.text.isNotEmpty() }
                                    ?.takeIf { verse -> verse.text.text.containsAnyKeyword(keywords, wholeWords) }
                                    ?.let { Pair(annotation, it) }
                        }
                    }
                    ?: emptyList()

    private suspend fun searchNotes(currentTranslation: String, keyword: String, wholeWords: Boolean): List<Pair<Note, Verse>> =
            noteRepository.search(keyword)
                    .takeIf { it.isNotEmpty() }
                    ?.let { notes ->
                        val verses = bibleReadingRepository.readVerses(currentTranslation, notes.map { it.verseIndex })
                        notes.mapNotNull { note ->
                            // Notes on verses that are empty or missing in this translation are intentionally left out of the results.
                            val noteMatches = !wholeWords || keyword.toKeywords().all { note.note.containsWholeWord(it) }
                            verses[note.verseIndex]?.takeIf { it.text.text.isNotEmpty() && noteMatches }?.let { Pair(note, it) }
                        }
                    }
                    ?: emptyList()
}

/** Shared matching rule for search and for filtering lists, e.g. the translation list. */
fun String.containsAnyKeyword(keywords: List<String>, wholeWords: Boolean): Boolean =
    keywords.any { term ->
        if (wholeWords) containsWholeWord(term)
        else lowercaseForUser().contains(term, false)
    }

fun String.containsWholeWord(keyword: String): Boolean {
    val text = lowercaseForUser()
    val term = keyword.lowercaseForUser()
    if (term.isEmpty()) return false

    var start = text.indexOf(term)
    while (start >= 0) {
        val end = start + term.length
        val leftBoundary = start == 0 || !text.isWordCharacterBefore(start)
        val rightBoundary = end == text.length || !text.isWordCharacterAt(end)
        if (leftBoundary && rightBoundary) return true
        start = text.indexOf(term, start + 1)
    }
    return false
}

private val REFERENCE_REGEX = Regex("""(.*?\S)\s*(\d+)(?:(?:[:.]|\s+)(\d+)(?:\s*[-\u2010-\u2015\u2212]\s*(\d+))?)?""")

/**
 * Parses references such as "John 1", "John 1:4", "John 1 4", "1 John 3:2-5", "Gen. 1:1", "JHN1.1" or "jhn 1" against the book names
 * (or short names, or 3-letter book codes) of a translation. Returns null if the query is not a reference, so it can be searched as text.
 */
fun parseReference(query: String, bookNames: List<String>, bookShortNames: List<String>): BibleReference? {
    val match = REFERENCE_REGEX.matchEntire(query.trim()) ?: return null
    val (name, chapter, verseStart, verseEnd) = match.destructured

    val bookName = name.normalizeBookName()
    val bookIndex = bookNames.indexOfFirst { it.normalizeBookName() == bookName }
            .takeIf { it >= 0 }
            ?: bookShortNames.indexOfFirst { it.normalizeBookName() == bookName }
                    .takeIf { it >= 0 }
            ?: BOOK_CODES.indexOfFirst { it.lowercaseForUser() == bookName.replace(" ", "") }
                    .takeIf { it >= 0 }
            ?: bookNames.indexOfFirst { bookName.length >= 3 && it.normalizeBookName().startsWith(bookName) }
                    .takeIf { it >= 0 }
            ?: bookShortNames.indexOfFirst { bookName.length >= 3 && it.normalizeBookName().startsWith(bookName) }
    if (bookIndex < 0) return null

    val chapterIndex = chapter.toIntOrNull()?.takeIf { it >= 1 }?.minus(1) ?: return null
    if (verseStart.isEmpty()) return BibleReference(bookIndex, chapterIndex)

    val start = verseStart.toIntOrNull()?.takeIf { it >= 1 } ?: return null
    val end = if (verseEnd.isEmpty()) start else verseEnd.toIntOrNull()?.takeIf { it >= start } ?: return null
    return BibleReference(bookIndex, chapterIndex, (start - 1)..(end - 1))
}

/**
 * Prepares pasted or typed text for searching: composes Unicode (NFC), turns non-breaking and other Unicode spaces into plain spaces,
 * removes invisible formatting characters (zero-width, soft hyphen, direction marks, BOM) and collapses whitespace.
 */
internal fun String.normalizeQuery(): String =
        composeUnicode()
                .replace(Regex("[\\p{Cf}\\u00AD]"), "")
                .replace(Regex("[\\p{Z}\\s]+"), " ")
                .trim()

private fun String.normalizeBookName(): String =
        lowercaseForUser().replace(".", "").trim().split(Regex("\\s+")).joinToString(" ")

fun String.toKeywords(): List<String> {
    if (isEmpty()) return emptyList()

    val results = arrayListOf<String>()
    val sb = StringBuilder()
    var doubleQuoteStarted = false
    lowercaseForUser().forEach { c ->
        if (c.isDoubleQuote()) {
            results.addAndClear(sb)
            doubleQuoteStarted = !doubleQuoteStarted
            return@forEach
        }

        if (c.isWhitespace() && !doubleQuoteStarted) {
            results.addAndClear(sb)
            return@forEach
        }

        sb.append(c)
    }
    if (sb.isNotEmpty()) {
        results.add(sb.toString())
    }
    return results
}

private fun ArrayList<String>.addAndClear(sb: StringBuilder) {
    if (sb.isNotEmpty()) {
        add(sb.toString())
        sb.clear()
    }
}

private fun Char.isDoubleQuote(): Boolean =
        this == Typography.quote
                || this == Typography.leftDoubleQuote || this == Typography.rightDoubleQuote
                || this == Typography.lowDoubleQuote || this == Typography.lowSingleQuote
