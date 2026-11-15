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


package com.github.danzuep.yeshua.android.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.danzuep.yeshua.android.R
import com.github.danzuep.yeshua.android.core.repository.remote.CopyrightBlock
import com.github.danzuep.yeshua.android.core.repository.remote.parseCopyrightMarkdown
import java.io.File

private fun Context.copyrightFile(translationShortName: String) =
        File(File(filesDir, "copyright"), translationShortName.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".md")

/** Keeps the copyright notice of a translation, as markdown, so that it can be shown offline. */
fun Context.saveCopyright(translationShortName: String, markdown: String) {
    copyrightFile(translationShortName).apply { parentFile?.mkdirs() }.writeText(markdown)
}

fun Context.deleteCopyright(translationShortName: String) {
    copyrightFile(translationShortName).delete()
}

private val MARKDOWN_LINK = Regex("""\[([^\]]*)]\(([^)]*)\)""")

private fun List<CopyrightBlock>.toText(): CharSequence = SpannableStringBuilder().also { text ->
    forEach { block ->
        if (text.isNotEmpty()) text.append("\n\n")
        val start = text.length
        var cursor = 0
        for (link in MARKDOWN_LINK.findAll(block.text)) {
            text.append(block.text, cursor, link.range.first)
            val linkStart = text.length
            text.append(link.groupValues[1])
            text.setSpan(URLSpan(link.groupValues[2]), linkStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = link.range.last + 1
        }
        text.append(block.text, cursor, block.text.length)
        if (block.heading) {
            text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(RelativeSizeSpan(1.2f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

/** Shows the copyright notice kept for [translationShortName], or opens eBible.org's page for translations that have none. */
fun Context.showCopyright(translationShortName: String) {
    val file = copyrightFile(translationShortName)
    val blocks = if (file.exists()) parseCopyrightMarkdown(file.readText()) else emptyList()
    if (blocks.isEmpty()) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ebible.org/$translationShortName/copr.htm")))
        return
    }
    showBlocks(blocks + CopyrightBlock(translationShortName))
}

/** Shows [markdown] (headings, bullets, links and paragraphs) in a dialog, e.g. a licence file, and calls [onDismiss] when it closes. */
fun Context.showMarkdown(markdown: String, onDismiss: () -> Unit = {}) =
        showBlocks(parseCopyrightMarkdown(markdown), onDismiss)

private fun Context.showBlocks(blocks: List<CopyrightBlock>, onDismiss: () -> Unit = {}) {
    val content = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_translation_details, null)
    content.findViewById<TextView>(R.id.details_text).apply {
        text = blocks.toText()
        movementMethod = LinkMovementMethod.getInstance()
    }
    AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { onDismiss() }
            .show()
}
