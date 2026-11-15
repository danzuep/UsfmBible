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

package com.github.danzuep.yeshua.android.core.repository.remote

import java.io.InputStream
import java.util.zip.ZipInputStream

/** The books of a USFM zip and its copyright notice (`copr.htm`) converted to markdown, if it has one. */
internal class UsfmZip(val books: Map<String, ScriptureBook>, val copyright: String?)

/** Reads a USFM zip (as published by eBible.org) into the books of [BOOK_CODES], keyed by USFM code. */
internal fun readUsfmZip(input: InputStream): UsfmZip = ZipInputStream(input).use { zip ->
    val entries = generateSequence { zip.nextEntry }.filter { !it.isDirectory }.map { it.name to zip.readBytes() }.toList()
    val copyright = entries.firstOrNull { (name, _) -> name.substringAfterLast('/').equals("copr.htm", ignoreCase = true) }
            ?.let { (_, bytes) -> copyrightHtmlToMarkdown(bytes.decodeToString()) }
            ?.ifEmpty { null }
    UsfmZip(readUsfmBooks(entries.asSequence()), copyright)
}
