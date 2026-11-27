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

package io.github.danzuep.usfmbible.tests

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.danzuep.usfmbible.appModules
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** Plain application for Robolectric, so the real [io.github.danzuep.usfmbible.App] does not start its own Koin graph. */
class TestApp : Application()

/** Starts the app's real graph with [overrides] on top, and stops it after each test so singletons never leak between tests. */
class KoinRule(private val overrides: () -> Module) : TestWatcher() {
    override fun starting(description: Description) {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Application>())
            modules(appModules + overrides())
        }
    }

    override fun finished(description: Description) {
        stopKoin()
    }
}

abstract class BaseKoinTest : BaseUnitTest() {
    @get:org.junit.Rule
    val koinRule = KoinRule { overrides() }

    /** Evaluated when the test starts, so it can reference the test's own mocks. */
    protected open fun overrides(): Module = module {}
}
