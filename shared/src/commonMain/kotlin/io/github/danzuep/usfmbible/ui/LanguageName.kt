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

package io.github.danzuep.usfmbible.ui

/** The localized name of [language] (e.g. `en`, `eng` or `zh_CN`), or [language] itself if it is not a known language code. */
expect fun languageDisplayName(language: String): String

/** The English name followed by the native name in brackets (e.g. `Greek (Ελληνικά)`), or the English name alone if no other native name is known. */
expect fun languageDisplayNameWithNative(language: String): String

/** Whether the translation [language] (e.g. `en`, `eng`, `cmn` or `zh_CN`) is the language of the user, comparing ISO 639 codes. */
expect fun languageMatchesUserLocale(language: String): Boolean
