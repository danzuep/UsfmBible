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

import java.text.Normalizer
import java.util.Locale

actual fun String.lowercaseForUser(): String = lowercase(Locale.getDefault())

actual fun String.composeUnicode(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

actual fun String.isWordCharacterBefore(index: Int): Boolean =
        Character.isLetterOrDigit(Character.codePointBefore(this, index)) || Character.codePointBefore(this, index) == '_'.code

actual fun String.isWordCharacterAt(index: Int): Boolean =
        Character.isLetterOrDigit(Character.codePointAt(this, index)) || Character.codePointAt(this, index) == '_'.code
