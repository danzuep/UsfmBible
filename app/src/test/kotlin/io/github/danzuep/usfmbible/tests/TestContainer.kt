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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.danzuep.usfmbible.AppContainer
import io.github.danzuep.usfmbible.ContainerHolder
import io.github.danzuep.usfmbible.Navigator
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Plain application for Robolectric, so the real [io.github.danzuep.usfmbible.App] does not build its own graph. */
class TestApp : Application(), ContainerHolder {
    override val container: AppContainer by lazy { factory?.invoke(this) ?: AppContainer(this) }

    companion object {
        @JvmStatic
        var factory: ((Application) -> AppContainer)? = null
    }
}

/** The real graph with the navigator and/or view models replaced by the given test doubles. */
class TestContainer(
    application: Application,
    private val navigatorOverride: Navigator? = null,
    private vararg val viewModelOverrides: ViewModel,
) : AppContainer(application) {
    override val navigator: Navigator get() = navigatorOverride ?: super.navigator

    override val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            viewModelOverrides.firstOrNull { modelClass.isInstance(it) }?.let { modelClass.cast(it)!! }
                ?: super@TestContainer.viewModelFactory.create(modelClass, extras)
    }
}

/** Installs [factory] for the application of each test, and removes it afterwards so containers never leak between tests. */
class ContainerRule(private val factory: (Application) -> AppContainer) : TestWatcher() {
    override fun starting(description: Description) {
        TestApp.factory = factory
    }

    override fun finished(description: Description) {
        TestApp.factory = null
    }
}

abstract class BaseContainerTest : BaseUnitTest() {
    @get:org.junit.Rule
    val containerRule = ContainerRule { createContainer(it) }

    /** Evaluated when the test first needs the graph, so it can reference the test's own mocks. */
    protected open fun createContainer(application: Application): AppContainer = AppContainer(application)
}
