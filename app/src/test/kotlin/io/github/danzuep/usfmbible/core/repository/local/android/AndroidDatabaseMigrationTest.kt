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
package io.github.danzuep.usfmbible.core.repository.local.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.danzuep.usfmbible.core.repository.local.android.db.AndroidDatabase
import io.github.danzuep.usfmbible.core.repository.local.android.db.Migration
import io.github.danzuep.usfmbible.core.repository.local.android.db.MetadataDao
import io.github.danzuep.usfmbible.tests.BaseUnitTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AndroidDatabaseMigrationTest : BaseUnitTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "MIGRATION_TEST"

    @Test
    fun testUpgradeKeepsExistingData() {
        context.deleteDatabase(name)
        AndroidDatabase(context, emptyList(), name).also {
            it.metadataDao.save(MetadataDao.KEY_CURRENT_TRANSLATION, "engwebp")
            it.close()
        }

        val addColumn = Migration { it.execSQL("ALTER TABLE metadata ADD COLUMN extra TEXT") }
        val upgraded = AndroidDatabase(context, listOf(addColumn), name)
        try {
            assertEquals(2, upgraded.readableDatabase.version)
            assertEquals("engwebp", upgraded.metadataDao.read(MetadataDao.KEY_CURRENT_TRANSLATION, ""))
            upgraded.readableDatabase.rawQuery("SELECT extra FROM metadata", null).use { assertEquals(1, it.count) }
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
