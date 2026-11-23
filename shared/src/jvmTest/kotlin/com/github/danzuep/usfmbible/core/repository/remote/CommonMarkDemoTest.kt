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

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonMarkDemoTest {
    private val blocks = checkNotNull(javaClass.getResourceAsStream("/CommonMarkDemo.md")).bufferedReader().use {
        parseCopyrightMarkdown(it.lineSequence())
    }

    @Test
    fun `test CommonMarkDemo parses to the expected blocks`() {
        assertEquals(
            listOf(
                CopyrightBlock("Heading 1", heading = true, level = 1),
                CopyrightBlock("Heading 2", heading = true, level = 2),
                CopyrightBlock("Heading 3", heading = true, level = 3),
                CopyrightBlock("This is a standard paragraph with **bold text**, *italic text*, ***bold and italic***, and ~~strikethrough~~."),
                CopyrightBlock("", rule = true),
                CopyrightBlock("Lists", heading = true, level = 3),
                CopyrightBlock("• Unordered item 1"),
                CopyrightBlock("• Unordered item 2"),
                CopyrightBlock("◦ Nested item", indent = 1),
                CopyrightBlock("1. Ordered item 1"),
                CopyrightBlock("2. Ordered item 2"),
                CopyrightBlock("☑ Completed task"),
                CopyrightBlock("☐ Incomplete task"),
                CopyrightBlock("Links & Images", heading = true, level = 3),
                CopyrightBlock("[Visit Markdown Guide](https://www.markdownguide.org)"),
                CopyrightBlock("![Alt text for image](https://placeholder.com \"Optional title\")"),
                CopyrightBlock("Blockquotes", heading = true, level = 3),
                CopyrightBlock("This is a blockquote.", quote = 1),
                CopyrightBlock("This is a nested blockquote.", quote = 2),
                CopyrightBlock("Code", heading = true, level = 3),
                CopyrightBlock("Use `inline code` inside a sentence."),
                CopyrightBlock("def greet(name):\n    return f\"Hello, {name}!\"", code = true),
                CopyrightBlock("Tables", heading = true, level = 3),
                CopyrightBlock("Feature │ Syntax │ Support"),
                CopyrightBlock("Left-aligned │ `:---` │ Yes"),
                CopyrightBlock("Center-aligned │ `:---:` │ Yes"),
                CopyrightBlock("Right-aligned │ `---:` │ Yes")
            ),
            blocks
        )
    }
}
