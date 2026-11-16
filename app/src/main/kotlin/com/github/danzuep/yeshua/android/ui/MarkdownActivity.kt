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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.danzuep.yeshua.android.R

/** Shows a markdown file opened from another app (e.g. a licence) in a dialog, without leaving that app's screen behind. */
class MarkdownActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val markdown = runCatching {
            // Read one byte past the limit to tell a file that is too big from one that is exactly at it.
            intent.data?.let { uri -> contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES + 1) } }
        }.getOrNull()
        if (markdown != null && markdown.size > MAX_BYTES) {
            toast(R.string.toast_file_too_large)
            finish()
            return
        }
        showMarkdownOrFinish(markdown?.decodeToString())
    }

    private fun showMarkdownOrFinish(markdown: String?) {
        if (markdown.isNullOrBlank()) {
            finish()
            return
        }
        showMarkdown(markdown) { finish() }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
