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

package io.github.danzuep.usfmbible.reading.speech

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class SpeechNotificationTest {
    @Test
    fun `known duration republishes notification but position ticks do not`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val notification = SpeechNotification(context, {}, {}, {}, {}, {})
        try {
            notification.update("John 1:1", true, 0, 0, true, true)
            val initial = assertNotNull(manager.getNotification(1))
            notification.update("John 1:1", true, 10000, 0, true, true)
            val loaded = assertNotNull(manager.getNotification(1))
            assertNotSame(initial, loaded)
            notification.updatePosition(true, 1000)
            assertSame(loaded, manager.getNotification(1))
            notification.update("John 1:1", true, 10000, 2000, true, true)
            assertSame(loaded, manager.getNotification(1))
        } finally {
            notification.release()
        }
    }
}
