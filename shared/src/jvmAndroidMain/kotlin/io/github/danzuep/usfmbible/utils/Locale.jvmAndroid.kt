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

package io.github.danzuep.usfmbible.utils

import java.text.NumberFormat
import java.util.Locale

// Arabic is left out on purpose: its default numbering system is already native.
private val NATIVE_NUMBERING_SYSTEMS = mapOf(
    "th" to "thai",
    "hi" to "deva", "mr" to "deva", "ne" to "deva",
    "bn" to "beng", "as" to "beng",
    "gu" to "gujr",
    "pa" to "guru",
    "kn" to "knda",
    "ml" to "mlym",
    "ta" to "tamldec",
    "te" to "telu",
    "or" to "orya",
    "my" to "mymr",
    "km" to "khmr",
    "lo" to "laoo",
    "fa" to "arabext", "ps" to "arabext", "ur" to "arabext",
)

/** Adds the language's native numbering system, unless the locale already picks one or has no known system. */
fun Locale.withNativeDigits(): Locale {
    if (getUnicodeLocaleType("nu") != null) return this
    val system = NATIVE_NUMBERING_SYSTEMS[language] ?: return this
    return Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("nu", system).build()
}

actual fun Int.localized(): String = NumberFormat.getIntegerInstance(Locale.getDefault().withNativeDigits()).apply { isGroupingUsed = false }.format(this)

actual fun userLanguageName(): String = Locale.getDefault().displayLanguage
