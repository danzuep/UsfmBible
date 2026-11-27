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

/** Moves the schema up one version without losing user data (bookmarks, notes, progress, downloaded translations). */
fun interface Migration {
    fun migrate(db: SQLiteDatabase)
}

/**
 * Opens the database at version `migrations.size + 1`: the migration at index `i` upgrades version `i + 1` to `i + 2`.
 * To change the schema, add a migration to [MIGRATIONS] and update the matching `createTable` for new installs.
 */
class AndroidDatabase(
    context: Context,
    private val migrations: List<Migration> = MIGRATIONS,
    name: String = DATABASE_NAME,
) : SQLiteOpenHelper(context, name, null, migrations.size + 1) {
    companion object {
        const val DATABASE_NAME = "DATABASE_USFMBIBLE"

        /** Version 1 is the schema as of the move to the io.github.danzuep application id. */
        val MIGRATIONS: List<Migration> = emptyList()
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

    // SQLiteOpenHelper runs this in a transaction, so a failing migration leaves the old schema and data in place.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (version in oldVersion until newVersion) migrations[version - 1].migrate(db)
    }
}

/** Every keyword must be in [column]; null when [query] has none, which matches all rows. */
fun withQuery(column: String, query: String): Where? =
    query.toKeywords().map { keyword -> column like "%$keyword%" }.reduceOrNull { all, like -> all and like }
