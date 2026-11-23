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

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.danzuep.usfmbible.core.repository.remote.CopyrightBlock
import com.github.danzuep.usfmbible.core.repository.remote.parseCopyrightMarkdown
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
        if (translation != null) {
            lifecycleScope.launch {
                val blocks = withContext(Dispatchers.IO) { copyrightBlocks(translation) }
                if (blocks.isEmpty()) finish() else show(blocks)
            }
            return
        }
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val blocks = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { parseCopyrightMarkdown(it.lineSequence()) }
                }
            }.getOrNull()
            if (blocks.isNullOrEmpty()) finish() else show(blocks)
        }
    }

    companion object {
        /** Short name of a translation whose kept copyright notice is shown instead of a file. */
        const val KEY_COPYRIGHT = "com.github.danzuep.usfmbible.KEY_COPYRIGHT"
    }

    private fun show(blocks: List<CopyrightBlock>) {
        val list = blocksView(blocks)
        ViewCompat.setOnApplyWindowInsetsListener(list) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        setContentView(list)
    }
}
