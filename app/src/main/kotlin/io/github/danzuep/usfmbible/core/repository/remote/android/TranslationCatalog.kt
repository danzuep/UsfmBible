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

package io.github.danzuep.usfmbible.core.repository.remote.android

import android.content.Context
import io.github.danzuep.usfmbible.core.repository.remote.TranslationTableRow
import io.github.danzuep.usfmbible.core.repository.remote.parseTranslationCsv
import java.util.concurrent.ConcurrentHashMap

/** The bundled `translations.csv`, parsed once per [pageUrl] and kept for the life of the process. */
internal object TranslationCatalog {
    private val rowsByPageUrl = ConcurrentHashMap<String, List<TranslationTableRow>>()

    fun rows(context: Context, pageUrl: String): List<TranslationTableRow> =
            rowsByPageUrl.getOrPut(pageUrl) {
                parseTranslationCsv(context.assets.open("translations.csv").use { it.readBytes().toString(Charsets.UTF_8) }, pageUrl)
            }
}
