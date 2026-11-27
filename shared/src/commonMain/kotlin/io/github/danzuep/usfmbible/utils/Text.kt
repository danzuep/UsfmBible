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

/** Lower case in the language of the user, which differs from [lowercase] for languages such as Turkish. */
expect fun String.lowercaseForUser(): String

/** Composes Unicode characters (NFC) so that text typed or pasted in different forms matches. */
expect fun String.composeUnicode(): String

/** Whether the character that ends just before [index] is a letter, digit or underscore. */
expect fun String.isWordCharacterBefore(index: Int): Boolean

/** Whether the character at [index] is a letter, digit or underscore. */
expect fun String.isWordCharacterAt(index: Int): Boolean
