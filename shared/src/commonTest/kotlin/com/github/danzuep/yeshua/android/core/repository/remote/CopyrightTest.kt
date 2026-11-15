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

import kotlin.test.Test
import kotlin.test.assertEquals

class CopyrightTest {
    private val html = """
        <!DOCTYPE html><html><head><title>Copyright</title><style>p { color: red }</style></head><body>
        <h1>Copyright &amp; Permissions</h1>
        <p>This translation is in the <b>public domain</b>.<br>See <a href="https://ebible.org/">https://ebible.org/</a>
        or <a href="https://example.com/terms.htm">the terms</a> &mdash; &#169; &#x1F4D6;.</p>
        <ul><li>Share it</li><li>Print it</li></ul>
        </body></html>
    """.trimIndent()

    private val markdown = """
        # Copyright & Permissions

        This translation is in the public domain. See [https://ebible.org/](https://ebible.org/) or [the terms](https://example.com/terms.htm) — © 📖.

        - Share it

        - Print it
    """.trimIndent()

    @Test
    fun `test copyrightHtmlToMarkdown()`() {
        assertEquals(markdown, copyrightHtmlToMarkdown(html))
    }

    @Test
    fun `test copyrightHtmlToMarkdown() drops navigation links and the generator note`() {
        val page = """<ul><li><a href="index.htm">^</a></li><li><a href="a.htm">&lt;</a></li><li><a href="b.htm">&gt;</a></li></ul>
            <p>Public domain.</p><p><a href="copr.htm">Local</a></p><p>HTML generated with Haiola by eBible.org 13 Nov 2026 from source files dated 13 Nov 2026</p>"""
        assertEquals("Public domain.\n\nLocal", copyrightHtmlToMarkdown(page))
    }

    @Test
    fun `test parseCopyrightMarkdown()`() {
        assertEquals(
            listOf(
                CopyrightBlock("Copyright & Permissions", heading = true),
                CopyrightBlock("This translation is in the public domain. See [https://ebible.org/](https://ebible.org/) or [the terms](https://example.com/terms.htm) — © 📖."),
                CopyrightBlock("• Share it"),
                CopyrightBlock("• Print it")
            ),
            parseCopyrightMarkdown(markdown)
        )
    }
}
