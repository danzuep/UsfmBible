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

package io.github.danzuep.usfmbible.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates the baseline profile: `./gradlew :app:generateReleaseBaselineProfile` with a device or emulator connected. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // Scroll the reader to exercise the verse list.
        device.findObject(By.scrollable(true))?.let {
            it.setGestureMargin(device.displayWidth / 5)
            it.scroll(Direction.DOWN, 1f)
            it.scroll(Direction.UP, 1f)
        }
        // Open a book directly, as the smoke test does.
        startActivityAndWait(Intent().apply {
            setClassName(PACKAGE_NAME, "$PACKAGE_NAME.reading.ReadingActivity")
            putExtra("$PACKAGE_NAME.KEY_OPEN_BOOK", 42)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "io.github.danzuep.usfmbible"
    }
}
