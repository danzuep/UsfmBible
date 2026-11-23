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


package com.github.danzuep.usfmbible.ui

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import com.github.danzuep.usfmbible.R
import com.github.danzuep.usfmbible.core.repository.remote.CopyrightBlock
import com.github.danzuep.usfmbible.core.repository.remote.parseCopyrightMarkdown
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

/** The kept copyright notice of [translationShortName] followed by its name, or empty when there is none. */
internal fun Context.copyrightBlocks(translationShortName: String): List<CopyrightBlock> {
    val file = copyrightFile(translationShortName)
    val blocks = if (file.exists()) parseCopyrightMarkdown(file.readText()) else emptyList()
    return if (blocks.isEmpty()) emptyList() else blocks + CopyrightBlock(translationShortName)
}

/** Shows the copyright notice kept for [translationShortName] full screen, or a placeholder that searches the web for translations that have none. */
fun Context.showCopyright(translationShortName: String) {
    if (copyrightBlocks(translationShortName).isEmpty()) {
        showCopyrightSearch(translationShortName)
        return
    }
    val intent = Intent(this, MarkdownActivity::class.java).putExtra(MarkdownActivity.KEY_COPYRIGHT, translationShortName)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/** Placeholder for a missing notice, offering a search with the default search provider. */
private fun Context.showCopyrightSearch(translationShortName: String) {
    AlertDialog.Builder(this)
            .setTitle(translationShortName)
            .setMessage(getString(R.string.copyright_not_found, translationShortName))
            .setPositiveButton(R.string.copyright_search) { _, _ ->
                val search = Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, "${translationShortName.replace('_', '-')} Bible translation copyright")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(search)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.copyright_search_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
}

/** Shows [markdown] (headings, bullets, links and paragraphs) in a dialog, e.g. a licence file, and calls [onDismiss] when it closes. */
fun Context.showMarkdown(markdown: String, onDismiss: () -> Unit = {}) =
        showBlocks(parseCopyrightMarkdown(markdown), onDismiss)

/** A scrollable list (with a scroll bar) that only lays out the blocks on screen, so documents of any length stay responsive. */
fun Context.blocksView(blocks: List<CopyrightBlock>): RecyclerView {
    val padding = (24 * resources.displayMetrics.density).toInt()
    return RecyclerView(this).apply {
        isVerticalScrollBarEnabled = true
        scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        layoutManager = LinearLayoutManager(context)
        setPadding(padding, padding / 3, padding, 0)
        clipToPadding = false
        adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = blocks.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(
                TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setTextAppearance(android.R.style.TextAppearance_Material_Medium)
                    setPadding(0, 0, 0, padding / 2)
                    setTextIsSelectable(true)
                    movementMethod = LinkMovementMethod.getInstance()
                }
            ) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = listOf(blocks[position]).toText()
            }
        }
    }
}

/** Shows [blocks] in a dialog. */
fun Context.showBlocks(blocks: List<CopyrightBlock>, onDismiss: () -> Unit = {}) {
    AlertDialog.Builder(this)
            .setView(blocksView(blocks))
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { onDismiss() }
            .show()
}
