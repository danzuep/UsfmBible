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


package io.github.danzuep.usfmbible.core.repository.local.android.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** A SQL condition. Numbers are written into the SQL and text is bound, so a keyword can never change the statement. */
class Where(internal val sql: String, internal val args: List<String> = emptyList()) {
    infix fun and(other: Where): Where = Where("($sql) AND (${other.sql})", args + other.args)

    companion object {
        /** At least one of [conditions] holds, written flat so that a long list doesn't nest. */
        fun anyOf(conditions: List<Where>): Where =
                Where(conditions.joinToString(" OR ") { "(${it.sql})" }, conditions.flatMap { it.args })
    }
}

infix fun String.eq(value: Int): Where = Where("$this = $value")

infix fun String.eq(value: String): Where = Where("$this = ?", listOf(value))

infix fun String.like(pattern: String): Where = Where("$this LIKE ?", listOf(pattern))

/** A query that is run, and its rows read, only when one of the terminal functions below is called. */
class Query(private val db: SQLiteDatabase, private val table: String, private val columns: Array<String>, private val where: Where?) {
    private var orderBy: String? = null

    fun orderBy(vararg columns: String, descending: Boolean = false): Query = apply {
        orderBy = columns.joinToString(", ") { if (descending) "$it DESC" else it }
    }

    fun asCursor(): Cursor = db.query(table, columns.takeIf { it.isNotEmpty() }, where?.sql, where?.args?.toTypedArray(), null, null, orderBy)
}

inline fun SQLiteDatabase.select(table: String, vararg columns: String, where: () -> Where? = { null }): Query =
        Query(this, table, arrayOf(*columns), where())

/** Rows are read straight from the cursor, so no per-row map is allocated. */
inline fun <T> Query.toList(converter: (Cursor) -> T): List<T> = toListIndexed { _, row -> converter(row) }

inline fun <T> Query.toListIndexed(converter: (Int, Cursor) -> T): List<T> = asCursor().use { cursor ->
    ArrayList<T>(cursor.count).also { list -> while (cursor.moveToNext()) list.add(converter(list.size, cursor)) }
}

inline fun Query.forEach(action: (Cursor) -> Unit) {
    asCursor().use { cursor -> while (cursor.moveToNext()) action(cursor) }
}

inline fun <T> Query.firstOrDefault(defaultValue: T, converter: (Cursor) -> T): T =
        asCursor().use { if (it.moveToFirst()) converter(it) else defaultValue }

inline fun <T> Query.firstOrDefault(defaultValue: () -> T, converter: (Cursor) -> T): T =
        asCursor().use { if (it.moveToFirst()) converter(it) else defaultValue() }

fun Cursor.getInt(column: String): Int = getInt(getColumnIndexOrThrow(column))

fun Cursor.getLong(column: String): Long = getLong(getColumnIndexOrThrow(column))

fun Cursor.getString(column: String): String = getString(getColumnIndexOrThrow(column))

operator fun ContentValues.set(key: String, value: Int) = put(key, value)

operator fun ContentValues.set(key: String, value: Long) = put(key, value)

operator fun ContentValues.set(key: String, value: String) = put(key, value)

inline fun SQLiteDatabase.insert(table: String, conflictAlgorithm: Int, block: (ContentValues) -> Unit): Long =
        insertWithOnConflict(table, null, ContentValues().apply(block), conflictAlgorithm)

fun SQLiteDatabase.delete(table: String, where: Where): Int = delete(table, where.sql, where.args.toTypedArray())

fun SQLiteDatabase.deleteAll(table: String) {
    delete(table, null, null)
}

/** [definition] is what goes between the brackets of CREATE TABLE: the columns and a composite PRIMARY KEY, if any. */
fun SQLiteDatabase.createTable(table: String, definition: String) {
    execSQL("CREATE TABLE IF NOT EXISTS $table ($definition);")
}

fun SQLiteDatabase.hasTable(table: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND tbl_name = ? LIMIT 1", arrayOf(table)).use { it.count > 0 }

fun SQLiteDatabase.dropTable(table: String) {
    execSQL("DROP TABLE IF EXISTS $table;")
}

inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

inline fun <T> SQLiteDatabase.withTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
