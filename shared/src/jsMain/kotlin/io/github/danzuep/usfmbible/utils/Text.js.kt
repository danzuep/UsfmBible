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

actual fun String.lowercaseForUser(): String = lowercase()

actual fun String.composeUnicode(): String = asDynamic().normalize("NFC") as String

actual fun String.isWordCharacterBefore(index: Int): Boolean = this[index - 1].let { it.isLetterOrDigit() || it == '_' }

actual fun String.isWordCharacterAt(index: Int): Boolean = this[index].let { it.isLetterOrDigit() || it == '_' }
