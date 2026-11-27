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


package io.github.danzuep.usfmbible.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.repository.remote.parseCopyrightMarkdown
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.danzuep.usfmbible.utils.toTranslationDisplayName
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows a markdown file opened from another app (e.g. a licence) full screen, on its own without the rest of this app.
 * The file is read line by line off the main thread and shown as a scrollable list of short blocks, so its size is not limited.
 */
class MarkdownActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val translation = intent.getStringExtra(KEY_COPYRIGHT)
        val uri = intent.data
        if (translation == null && uri == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_markdown)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = translation?.toTranslationDisplayName() ?: uri?.let { fileName(it) }
            setNavigationOnClickListener { finish() }
        }
        val list = findViewById<RecyclerView>(R.id.blocks).apply { layoutManager = LinearLayoutManager(context) }
        lifecycleScope.launch {
            val blocks = runCatching {
                withContext(Dispatchers.IO) {
                    if (translation != null) copyrightBlocks(translation)
                    else contentResolver.openInputStream(uri!!)?.bufferedReader()?.use { parseCopyrightMarkdown(it.lineSequence()) }
                }
            }.getOrNull()
            if (blocks.isNullOrEmpty()) finish() else list.adapter = blocksAdapter(blocks)
        }
    }

    private fun fileName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        /** Short name of a translation whose kept copyright notice is shown instead of a file. */
        const val KEY_COPYRIGHT = "io.github.danzuep.usfmbible.KEY_COPYRIGHT"
    }
}
