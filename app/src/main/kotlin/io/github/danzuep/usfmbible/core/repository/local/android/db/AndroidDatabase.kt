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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.danzuep.usfmbible.core.toKeywords

class AndroidDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "DATABASE_USFMBIBLE"
        const val DATABASE_VERSION = 1
    }

    val bookmarkDao = BookmarkDao(this)
    val bookNamesDao = BookNamesDao(this)
    val highlightDao = HighlightDao(this)
    val metadataDao = MetadataDao(this)
    val noteDao = NoteDao(this)
    val readingProgressDao = ReadingProgressDao(this)
    val translationDao = TranslationDao(this)
    val translationInfoDao = TranslationInfoDao(this)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Write-ahead logging makes writes (e.g. installing a translation) faster and lets reads continue during them.
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.transaction {
            bookmarkDao.createTable(db)
            bookNamesDao.createTable(db)
            highlightDao.createTable(db)
            metadataDao.createTable(db)
            noteDao.createTable(db)
            readingProgressDao.createTable(db)
            translationInfoDao.createTable(db)
        }
    }

    // Version 1 is the schema as of the move to the io.github.danzuep application id, so there is nothing to upgrade from.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

/** Every keyword must be in [column]; null when [query] has none, which matches all rows. */
fun withQuery(column: String, query: String): Where? =
    query.toKeywords().map { keyword -> column like "%$keyword%" }.reduceOrNull { all, like -> all and like }
