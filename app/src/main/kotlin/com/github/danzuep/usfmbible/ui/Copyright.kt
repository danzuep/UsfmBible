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
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
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

// Escapes, code, images and links, autolinks, then strong and emphasis; groups 8-11 are the strong and emphasis texts.
private val INLINE_MARKDOWN = Regex(
    """\\([!-/:-@\[-`{-~])|(`+)(.+?)\2|(!?)\[([^\]]*)]\(\s*<?([^)\s>]*)>?(?:\s+"[^"]*")?\s*\)|<((?:https?|mailto):[^>\s]+)>""" +
        """|\*\*(?=\S)(.+?)(?<=\S)\*\*|(?<!\w)__(?=\S)(.+?)(?<=\S)__(?!\w)|\*(?=\S)(.+?)(?<=\S)\*|(?<!\w)_(?=\S)(.+?)(?<=\S)_(?!\w)""",
    RegexOption.DOT_MATCHES_ALL
)

private val HEADING_SIZES = floatArrayOf(1.6f, 1.4f, 1.2f, 1.1f, 1f, 0.9f)
private val MUTED = 0xFF888888.toInt()
private const val CODE_BACKGROUND = 0x22888888

private fun SpannableStringBuilder.appendInline(markdown: String) {
    var cursor = 0
    for (match in INLINE_MARKDOWN.findAll(markdown)) {
        append(markdown, cursor, match.range.first)
        cursor = match.range.last + 1
        val groups = match.groups
        val start = length
        var span: Any? = null
        when {
            groups[1] != null -> append(groups[1]!!.value)
            groups[2] != null -> {
                append(groups[3]!!.value.trim())
                span = TypefaceSpan("monospace")
            }
            groups[5] != null -> {
                val url = groups[6]!!.value
                appendInline(groups[5]!!.value.ifEmpty { url })
                if (url.isNotEmpty()) span = URLSpan(url)
            }
            groups[7] != null -> {
                append(groups[7]!!.value)
                span = URLSpan(groups[7]!!.value)
            }
            groups[8] != null || groups[9] != null -> {
                appendInline((groups[8] ?: groups[9])!!.value)
                span = StyleSpan(Typeface.BOLD)
            }
            else -> {
                appendInline((groups[10] ?: groups[11])!!.value)
                span = StyleSpan(Typeface.ITALIC)
            }
        }
        if (span != null && length > start) setSpan(span, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    append(markdown, cursor, markdown.length)
}

private fun CopyrightBlock.toText(): CharSequence = SpannableStringBuilder().also { out ->
    when {
        rule -> {
            out.append("─".repeat(24))
            out.setSpan(ForegroundColorSpan(MUTED), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        code -> {
            out.append(text)
            out.setSpan(TypefaceSpan("monospace"), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(BackgroundColorSpan(CODE_BACKGROUND), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        else -> {
            out.appendInline(text)
            if (heading) {
                out.setSpan(StyleSpan(Typeface.BOLD), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(HEADING_SIZES[(level - 1).coerceIn(0, HEADING_SIZES.lastIndex)]), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    if (quote && out.isNotEmpty()) out.setSpan(QuoteSpan(MUTED), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

/** A scrollable list (with a scroll bar) that only lays out the blocks on screen, so documents of any length stay responsive. */
fun Context.blocksView(blocks: List<CopyrightBlock>): RecyclerView {
    val padding = (24 * resources.displayMetrics.density).toInt()
    val indentStep = (16 * resources.displayMetrics.density).toInt()
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
                val block = blocks[position]
                (holder.itemView as TextView).apply {
                    text = block.toText()
                    setPaddingRelative(block.indent * indentStep, 0, 0, padding / 2)
                }
            }
        }
    }
}
