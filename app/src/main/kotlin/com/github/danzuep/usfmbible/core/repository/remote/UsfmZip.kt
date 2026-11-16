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

package com.github.danzuep.usfmbible.core.repository.remote

import java.io.InputStream
import java.util.zip.ZipInputStream

/** The books of a USFM zip and its copyright notice (`copr.htm`, else a `LICENCE` file) converted to markdown, if it has one. */
internal class UsfmZip(val books: Map<String, ScriptureBook>, val copyright: String?)

/**
 * Reads a USFM zip (as published by eBible.org), or a single plain USFM book (`.usfm`/`.sfm`), into the books of
 * [BOOK_CODES], keyed by USFM code.
 */
internal fun readUsfmZip(input: InputStream): UsfmZip {
    val bytes = input.readBytes()
    // Zip files start with the "PK" signature, anything else is treated as a single USFM book.
    if (!(bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())) {
        return UsfmZip(readUsfmBooks(sequenceOf("book.usfm" to bytes)), null)
    }
    return ZipInputStream(bytes.inputStream()).use { zip ->
        val entries = generateSequence { zip.nextEntry }.filter { !it.isDirectory }.map { it.name to zip.readBytes() }.toList()
        fun baseName(name: String) = name.substringAfterLast('/')
        // Prefer the copyright page, then any LICENCE/LICENSE file (usually markdown).
        val page = entries.firstOrNull { (name, _) -> baseName(name).equals("copr.htm", ignoreCase = true) }
                ?: entries.firstOrNull { (name, _) -> baseName(name).let { it.startsWith("licence", true) || it.startsWith("license", true) } }
        val copyright = page?.let { (name, bytes) ->
            val text = bytes.decodeToString()
            if (baseName(name).endsWith(".htm", true) || baseName(name).endsWith(".html", true)) copyrightHtmlToMarkdown(text) else text.trim()
        }?.ifEmpty { null }
        UsfmZip(readUsfmBooks(entries.asSequence()), copyright)
    }
}
