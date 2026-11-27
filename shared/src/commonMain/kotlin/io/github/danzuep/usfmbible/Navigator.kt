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
package io.github.danzuep.usfmbible

enum class Screen {
    READING, SEARCH, TRANSLATIONS, READING_PROGRESS, BOOKMARKS, HIGHLIGHTS, NOTES, SETTINGS, TIMELINE, RATE_ME, WEBSITE, TRANSLATION_UPDATES
}

/** Moves between screens of the app, so screens and view models don't depend on how the platform shows them. */
interface Navigator {
    /** Opens [screen] on top of the current one; [extras] are the values the screen starts with, by key. */
    fun navigate(screen: Screen, extras: Map<String, Any?> = emptyMap())

    fun goBack()
}
