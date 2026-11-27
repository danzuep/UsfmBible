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


package io.github.danzuep.usfmbible.core.repository.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `test copyrightHtmlToMarkdown() drops a linked generator note`() {
        val page = "<p>Public domain.</p><p>HTML generated with <a href=\"https://haiola.org\">Haiola</a> by eBible.org 13 Nov 2026</p>"
        assertEquals("Public domain.", copyrightHtmlToMarkdown(page))
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

    @Test
    fun `test parseCopyrightMarkdown() reads heading levels and setext headings`() {
        assertEquals(
            listOf(
                CopyrightBlock("One", heading = true, level = 1),
                CopyrightBlock("Three", heading = true, level = 3),
                CopyrightBlock("Setext one", heading = true, level = 1),
                CopyrightBlock("Setext two", heading = true, level = 2),
                CopyrightBlock("#hashtag")
            ),
            parseCopyrightMarkdown("# One\n### Three ###\nSetext one\n===\n\nSetext two\n---\n\n#hashtag")
        )
    }

    @Test
    fun `test parseCopyrightMarkdown() joins paragraph lines and honours hard breaks`() {
        assertEquals(
            listOf(CopyrightBlock("one two"), CopyrightBlock("a\nb\nc")),
            parseCopyrightMarkdown("one\ntwo\n\na  \nb\\\nc")
        )
    }

    @Test
    fun `test parseCopyrightMarkdown() reads lists, quotes, code and rules`() {
        val markdown = "- a\n  - nested\n* b\n1. first\n2) second\n\n> quoted\n> text\n\n```kotlin\nval x = 1\n\n*not* markdown\n```\n***\n- - -"
        assertEquals(
            listOf(
                CopyrightBlock("• a"),
                CopyrightBlock("◦ nested", indent = 1),
                CopyrightBlock("• b"),
                CopyrightBlock("1. first"),
                CopyrightBlock("2) second"),
                CopyrightBlock("quoted text", quote = 1),
                CopyrightBlock("val x = 1\n\n*not* markdown", code = true),
                CopyrightBlock("", rule = true),
                CopyrightBlock("", rule = true)
            ),
            parseCopyrightMarkdown(markdown)
        )
    }

    @Test
    fun `test parseCopyrightMarkdown() splits long paragraphs`() {
        val blocks = parseCopyrightMarkdown("word ".repeat(5000))
        assertTrue(blocks.size > 1)
        assertTrue(blocks.all { it.text.length <= MAX_BLOCK_LENGTH })
        assertEquals(5000, blocks.sumOf { it.text.split(' ').size })
    }
}
