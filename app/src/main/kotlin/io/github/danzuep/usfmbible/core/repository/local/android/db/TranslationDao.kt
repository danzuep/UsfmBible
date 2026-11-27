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

package io.github.danzuep.usfmbible.core.repository.local.android.db

import io.github.danzuep.usfmbible.core.stripStructure
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.WorkerThread
import me.xizzhu.android.ask.db.*
import io.github.danzuep.usfmbible.core.Bible
import io.github.danzuep.usfmbible.core.Verse
import io.github.danzuep.usfmbible.core.VerseIndex
import io.github.danzuep.usfmbible.core.VerseSearchQuery
import io.github.danzuep.usfmbible.core.containsWholeWord
import io.github.danzuep.usfmbible.core.toKeywords
import co.touchlab.kermit.Logger
import kotlin.math.max

class TranslationDao(sqliteHelper: SQLiteOpenHelper) {
    companion object {
        private const val COLUMN_BOOK_INDEX = "bookIndex"
        private const val COLUMN_CHAPTER_INDEX = "chapterIndex"
        private const val COLUMN_VERSE_INDEX = "verseIndex"
        private const val COLUMN_TEXT = "text"

        private val TAG: String = TranslationDao::class.java.simpleName

        /** A table name can't start with a digit (e.g. "63_1JN" from a file name), so those get a prefix; other names are unchanged so installed translations stay readable. */
        internal fun tableName(translationShortName: String): String =
            if (translationShortName.firstOrNull()?.isDigit() == true) "tr_$translationShortName" else translationShortName
    }

    private val db by lazy { sqliteHelper.writableDatabase }

    @WorkerThread
    fun read(translationShortName: String, bookIndex: Int, chapterIndex: Int): List<Verse> = db.withTransaction {
        if (!hasTableAndLogIfNoTable(translationShortName)) return@withTransaction emptyList()

        select(tableName(translationShortName), COLUMN_TEXT) {
            (COLUMN_BOOK_INDEX eq bookIndex) and (COLUMN_CHAPTER_INDEX eq chapterIndex)
        }.orderBy(COLUMN_VERSE_INDEX).toListIndexed { index, row ->
            Verse(VerseIndex(bookIndex, chapterIndex, index),
                    Verse.Text(translationShortName, row.getString(COLUMN_TEXT)), emptyList())
        }
    }

    @WorkerThread
    fun readBookIndicesWithVerses(translationShortName: String): Set<Int> = db.withTransaction {
        if (!hasTableAndLogIfNoTable(translationShortName)) return@withTransaction emptySet()

        select(tableName(translationShortName), COLUMN_BOOK_INDEX) { noOp() }.toList { row -> row.getInt(COLUMN_BOOK_INDEX) }.toSet()
    }

    @WorkerThread
    private fun SQLiteDatabase.hasTableAndLogIfNoTable(table: String): Boolean = hasTable(tableName(table))
            .also { hasTable -> if (!hasTable) Logger.withTag(TAG).e { "Missing translation $table" } }

    @WorkerThread
    fun read(translationShortName: String, parallelTranslations: List<String>,
             bookIndex: Int, chapterIndex: Int): List<Verse> = db.withTransaction {
        if (!hasTableAndLogIfNoTable(translationShortName)) return emptyList()

        val primaryTexts = readVerseTexts(translationShortName, bookIndex, chapterIndex)
        var versesCount = primaryTexts.size
        val parallelTexts = ArrayList<List<Verse.Text>>(parallelTranslations.size).apply {
            val countVerses: (List<Verse.Text>) -> Unit = { versesCount = max(versesCount, it.size) }
            parallelTranslations.forEach { add(readVerseTexts(it, bookIndex, chapterIndex).also(countVerses)) }
        }

        val verses = ArrayList<Verse>(versesCount)
        for (verseIndex in 0 until versesCount) {
            val primary = if (primaryTexts.size > verseIndex) primaryTexts[verseIndex] else Verse.Text(translationShortName, "")

            val parallel = ArrayList<Verse.Text>(parallelTranslations.size)
            for ((i, translation) in parallelTranslations.withIndex()) {
                parallel.add(parallelTexts[i].let {
                    return@let if (it.size > verseIndex) it[verseIndex] else Verse.Text(translation, "")
                })
            }

            verses.add(Verse(VerseIndex(bookIndex, chapterIndex, verseIndex), primary, parallel))
        }

        return verses
    }

    @WorkerThread
    private fun SQLiteDatabase.readVerseTexts(translation: String,
                                              bookIndex: Int, chapterIndex: Int): List<Verse.Text> =
            if (hasTableAndLogIfNoTable(translation)) {
                select(tableName(translation), COLUMN_TEXT) {
                    (COLUMN_BOOK_INDEX eq bookIndex) and (COLUMN_CHAPTER_INDEX eq chapterIndex)
                }.orderBy(COLUMN_VERSE_INDEX).toList { Verse.Text(translation, it.getString(COLUMN_TEXT).stripStructure()) }
            } else {
                emptyList()
            }

    @WorkerThread
    fun read(translationShortName: String, verseIndexes: List<VerseIndex>): Map<VerseIndex, Verse> = db.withTransaction {
        if (!hasTableAndLogIfNoTable(translationShortName)) return@withTransaction emptyMap()

        val verses = hashMapOf<VerseIndex, Verse>()
        // Work-around until https://github.com/xizzhu/ask/issues/8 is solved.
        verseIndexes.chunked(333).forEach { chunkedVerseIndexes ->
            select(tableName(translationShortName)) {
                var condition: Condition = noOp()
                chunkedVerseIndexes.forEach { verseIndex ->
                    ((COLUMN_BOOK_INDEX eq verseIndex.bookIndex) and
                            (COLUMN_CHAPTER_INDEX eq verseIndex.chapterIndex) and
                            (COLUMN_VERSE_INDEX eq verseIndex.verseIndex)).run {
                        condition = if (condition == Condition.NoOp) this else condition or this
                    }
                }
                condition
            }.forEach { row ->
                val verseIndex = VerseIndex(row.getInt(COLUMN_BOOK_INDEX), row.getInt(COLUMN_CHAPTER_INDEX), row.getInt(COLUMN_VERSE_INDEX))
                verses[verseIndex] = Verse(verseIndex, Verse.Text(translationShortName, row.getString(COLUMN_TEXT).stripStructure()), emptyList())
            }
        }
        verses
    }

    @WorkerThread
    fun search(query: VerseSearchQuery): List<Verse> = db.withTransaction {
        if (query.keyword.isBlank()
                || (!query.includeOldTestament && !query.includeNewTestament)
                || !hasTableAndLogIfNoTable(query.translation)) {
            return@withTransaction emptyList()
        }

        val keywords = query.keyword.toKeywords()
        val bookRange = when {
            query.includeOldTestament && !query.includeNewTestament -> "$COLUMN_BOOK_INDEX < ${Bible.OLD_TESTAMENT_COUNT} AND "
            !query.includeOldTestament && query.includeNewTestament -> "$COLUMN_BOOK_INDEX >= ${Bible.OLD_TESTAMENT_COUNT} AND $COLUMN_BOOK_INDEX < ${Bible.CANON_BOOK_COUNT} AND "
            else -> ""
        }
        val quotedTableName = "\"" + tableName(query.translation).replace("\"", "\"\"") + "\""
        val sql = "SELECT $COLUMN_BOOK_INDEX, $COLUMN_CHAPTER_INDEX, $COLUMN_VERSE_INDEX, $COLUMN_TEXT FROM $quotedTableName WHERE $bookRange" +
                keywords.joinToString(" AND ") { "$COLUMN_TEXT LIKE ?" } +
                " ORDER BY $COLUMN_BOOK_INDEX, $COLUMN_CHAPTER_INDEX, $COLUMN_VERSE_INDEX"
        val verses = ArrayList<Verse>()
        // Rows are read one at a time and dropped as soon as they don't match, so only the matches are kept in memory.
        db.rawQuery(sql, keywords.map { "%$it%" }.toTypedArray()).use { cursor ->
            val bookColumn = cursor.getColumnIndexOrThrow(COLUMN_BOOK_INDEX)
            val chapterColumn = cursor.getColumnIndexOrThrow(COLUMN_CHAPTER_INDEX)
            val verseColumn = cursor.getColumnIndexOrThrow(COLUMN_VERSE_INDEX)
            val textColumn = cursor.getColumnIndexOrThrow(COLUMN_TEXT)
            while (verses.size <= query.limit && cursor.moveToNext()) {
                // Headings and notes are stored with the text but not searchable, so each keyword must match the displayed text.
                val text = cursor.getString(textColumn).stripStructure()
                if (keywords.all { text.contains(it, ignoreCase = true) }
                        && (!query.wholeWords || keywords.all { text.containsWholeWord(it) })) {
                    verses.add(Verse(
                            VerseIndex(cursor.getInt(bookColumn), cursor.getInt(chapterColumn), cursor.getInt(verseColumn)),
                            Verse.Text(query.translation, text), emptyList()
                    ))
                }
            }
        }
        verses
    }

    @WorkerThread
    fun save(translationShortName: String, verses: Map<Pair<Int, Int>, List<String>>) = db.withTransaction {
        if (hasTable(tableName(translationShortName))) {
            Logger.withTag(TAG).e { "Translation $translationShortName already installed" }
            remove(translationShortName)
        }
        createTable(tableName(translationShortName)) {
            it[COLUMN_BOOK_INDEX] = INTEGER + PRIMARY_KEY + NOT_NULL
            it[COLUMN_CHAPTER_INDEX] = INTEGER + PRIMARY_KEY + NOT_NULL
            it[COLUMN_VERSE_INDEX] = INTEGER + PRIMARY_KEY + NOT_NULL
            it[COLUMN_TEXT] = TEXT + NOT_NULL
        }

        // One compiled statement is reused for every verse, which is much cheaper than building a new insert each time.
        val quotedTableName = tableName(translationShortName).replace("\"", "\"\"")
        db.compileStatement(
            "INSERT OR REPLACE INTO \"$quotedTableName\" ($COLUMN_BOOK_INDEX, $COLUMN_CHAPTER_INDEX, $COLUMN_VERSE_INDEX, $COLUMN_TEXT) VALUES (?, ?, ?, ?)"
        ).use { statement ->
            verses.forEach { (index, verseList) ->
                verseList.forEachIndexed { verseIndex, verse ->
                    statement.clearBindings()
                    statement.bindLong(1, index.first.toLong())
                    statement.bindLong(2, index.second.toLong())
                    statement.bindLong(3, verseIndex.toLong())
                    statement.bindString(4, verse)
                    statement.executeInsert()
                }
            }
        }
    }

    @WorkerThread
    fun remove(translationShortName: String) {
        db.dropTable(tableName(translationShortName))
    }
}
