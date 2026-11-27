/*
 * Copyright (C) 2023 Xizhi Zhu
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

import io.github.danzuep.usfmbible.utils.VisibleForTesting
import io.github.danzuep.usfmbible.utils.userLanguageName
import io.github.danzuep.usfmbible.core.TranslationInfo

class TranslationInfoComparator(private val sortOrder: Int) : Comparator<TranslationInfo> {
    companion object {
        const val SORT_ORDER_LANGUAGE_THEN_NAME = 0
        const val SORT_ORDER_LANGUAGE_THEN_SHORT_NAME = 1
    }

    override fun compare(t1: TranslationInfo, t2: TranslationInfo): Int {
        val userLanguage = userLanguage()
        val language1 = languageDisplayName(t1.language)
        val language2 = languageDisplayName(t2.language)
        val score1 = if (userLanguage == language1 || languageMatchesUserLocale(t1.language)) 1 else 0
        val score2 = if (userLanguage == language2 || languageMatchesUserLocale(t2.language)) 1 else 0
        var r = score2 - score1
        if (r == 0) {
            r = languageNameComparator.compare(language1, language2)
        }
        if (r == 0) {
            r = when (sortOrder) {
                SORT_ORDER_LANGUAGE_THEN_NAME -> t1.name.compareTo(t2.name)
                SORT_ORDER_LANGUAGE_THEN_SHORT_NAME -> t1.shortName.compareTo(t2.shortName)
                else -> throw IllegalArgumentException("Unsupported sort order: $sortOrder")
            }
        }
        return r
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun userLanguage(): String = userLanguageName()
}
