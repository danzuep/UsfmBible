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
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

/** The books of a USFM zip and its copyright notice (`copr.htm`, else a `LICENCE` file) converted to markdown, if it has one. */
internal class UsfmZip(val books: Map<String, ScriptureBook>, val copyright: String?)

/**
 * Reads a USFM zip (as published by eBible.org), or a single plain USFM book (`.usfm`/`.sfm`), into the books of
 * [BOOK_CODES], keyed by USFM code.
 */
internal fun readUsfmZip(input: InputStream): UsfmZip {
    val stream = if (input.markSupported()) input else BufferedInputStream(input)
    stream.mark(2)
    val signature = ByteArray(2)
    val read = stream.read(signature)
    stream.reset()
    // Zip files start with the "PK" signature, anything else is treated as a single USFM book.
    if (!(read == 2 && signature[0] == 'P'.code.toByte() && signature[1] == 'K'.code.toByte())) {
        return UsfmZip(readUsfmBooks(sequenceOf("book.usfm" to stream.readBytes())), null)
    }
    fun baseName(name: String) = name.substringAfterLast('/')
    // Entries are parsed as they are read, so only the parsed books and one entry at a time are held in memory.
    val books = hashMapOf<String, ScriptureBook>()
    var copyrightPage: Pair<String, ByteArray>? = null
    var licenceFile: Pair<String, ByteArray>? = null
    ZipInputStream(stream).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = baseName(entry.name)
            val isCopyrightPage = name.equals("copr.htm", ignoreCase = true)
            // Prefer the copyright page, then any LICENCE/LICENSE file (usually markdown).
            val isLicence = name.startsWith("licence", true) || name.startsWith("license", true)
            val content = zip.readBytes()
            if (isCopyrightPage) { if (copyrightPage == null) copyrightPage = entry.name to content }
            else if (isLicence) { if (licenceFile == null) licenceFile = entry.name to content }
            else readUsfmBook(entry.name, content)?.let { books[it.code] = it }
        }
    }
    val copyright = (copyrightPage ?: licenceFile)?.let { (name, bytes) ->
        val text = bytes.decodeToString()
        if (baseName(name).endsWith(".htm", true) || baseName(name).endsWith(".html", true)) copyrightHtmlToMarkdown(text) else text.trim()
    }?.ifEmpty { null }
    return UsfmZip(books, copyright)
}
