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

package io.github.danzuep.usfmbible.translations

import android.app.Application
import android.net.Uri
import io.github.danzuep.usfmbible.core.repository.remote.UsfmMetadata
import io.github.danzuep.usfmbible.core.repository.remote.cleanShortName
import io.github.danzuep.usfmbible.core.repository.remote.parseUsfmMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.BibleReadingManager
import io.github.danzuep.usfmbible.core.SettingsManager
import io.github.danzuep.usfmbible.core.TranslationInfo
import io.github.danzuep.usfmbible.core.TranslationManager
import io.github.danzuep.usfmbible.core.containsAnyKeyword
import io.github.danzuep.usfmbible.core.repository.remote.TranslationTableRow
import io.github.danzuep.usfmbible.core.repository.remote.android.TranslationCatalog
import io.github.danzuep.usfmbible.core.toKeywords
import io.github.danzuep.usfmbible.ui.TranslationInfoComparator
import io.github.danzuep.usfmbible.ui.languageDisplayName
import io.github.danzuep.usfmbible.ui.languageDisplayNameWithNative
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.infra.BaseViewModel
import co.touchlab.kermit.Logger

class TranslationsViewModel(
    private val bibleReadingManager: BibleReadingManager,
    private val translationManager: TranslationManager,
    private val settingsManager: SettingsManager,
    private val application: Application,
    private val coroutineDispatcherProvider: CoroutineDispatcherProvider,
) : BaseViewModel<TranslationsViewModel.ViewAction, TranslationsViewModel.ViewState>(
    initialViewState = ViewState(
        loading = true,
        items = emptyList(),
        translationDownloadingState = ViewState.TranslationDownloadingState.Idle,
        translationRemovalState = ViewState.TranslationRemovalState.Idle,
        error = null,
    )
) {
    sealed class ViewAction {
        object GoBack : ViewAction()
    }

    data class ViewState(
        val loading: Boolean,
        val items: List<TranslationsItem>,
        val translationDownloadingState: TranslationDownloadingState,
        val translationRemovalState: TranslationRemovalState,
        val error: Error?,
    ) {
        sealed class TranslationDownloadingState {
            object Idle : TranslationDownloadingState()
            data class Downloading(val progress: Int) : TranslationDownloadingState()
            object Installing : TranslationDownloadingState()
            data class Completed(val successful: Boolean) : TranslationDownloadingState()
        }

        sealed class TranslationRemovalState {
            object Idle : TranslationRemovalState()
            object Removing : TranslationRemovalState()
            data class Completed(val successful: Boolean) : TranslationRemovalState()
        }

        sealed class Error {
            object NoTranslationsError : Error()
            object TranslationAlreadyInstalledError : Error()
            data class TranslationDownloadingError(val translationToDownload: TranslationInfo) : Error()
            object TranslationNotInstalledError : Error()
            data class TranslationRemovalError(val translationToRemove: TranslationInfo) : Error()
            data class TranslationSelectionError(val translationToSelect: TranslationInfo) : Error()
        }
    }

    private val translationComparator = TranslationInfoComparator(TranslationInfoComparator.SORT_ORDER_LANGUAGE_THEN_NAME)
    private var downloadTranslationJob: Job? = null

    private var loadedDownloadedTranslations: List<TranslationInfo> = emptyList()
    private var loadedAvailableTranslations: List<TranslationInfo> = emptyList()
    var keywordFilter: String = ""
        private set
    private var pendingDownloadShortName: String? = null

    private val translationRows: Map<String, TranslationTableRow> by lazy {
        runCatching {
            TranslationCatalog.rows(application, "https://ebible.org/").associateBy { it.id.replace(Regex("[^A-Za-z0-9]"), "_") }
        }.getOrDefault(emptyMap())
    }

    /** The vernacular title with the English title in brackets, e.g. `新译本 (Chinese New Version (simplified))`. */
    private fun TranslationTableRow.displayTitle(): String = when {
        vernacularTitle.isEmpty() -> englishTitle
        englishTitle.isEmpty() || englishTitle == vernacularTitle -> vernacularTitle
        else -> "$vernacularTitle ($englishTitle)"
    }

    init {
        loadTranslations(forceRefresh = false)
    }

    fun loadTranslations(forceRefresh: Boolean) {
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            updateViewState { it.copy(loading = true, items = emptyList()) }

            translationManager.reload(forceRefresh)

            // After the refresh, if no change is detected, nothing will be emitted, therefore we need to manually load here.
            val availableTranslations = translationManager.availableTranslations().first().sortedWith(translationComparator)
            val downloadedTranslations = translationManager.downloadedTranslations().first().sortedWith(translationComparator)
            if (availableTranslations.isEmpty() && downloadedTranslations.isEmpty()) {
                updateViewState { it.copy(loading = false, error = ViewState.Error.NoTranslationsError) }
                return@launch
            }

            loadedDownloadedTranslations = downloadedTranslations
            loadedAvailableTranslations = availableTranslations
            val settings = settingsManager.settings().first()
            val currentTranslation = bibleReadingManager.currentTranslation().first()
            updateViewState { it.copy(loading = false, items = buildItems(settings, currentTranslation)) }
            tryPendingDownload()
        }
    }

    private fun buildItems(settings: Settings, currentTranslation: String): List<TranslationsItem> {
        val items: ArrayList<TranslationsItem> = ArrayList()
        val downloaded = loadedDownloadedTranslations.filter { it.matchesFilter() }
        val available = loadedAvailableTranslations.filter { it.matchesFilter() }
        items.addAll(downloaded.toItems(settings, currentTranslation))
        if (available.isNotEmpty()) {
            items.add(TranslationsItem.Header(settings, application.getString(R.string.header_available_translations), hideDivider = false))
            items.addAll(available.toItems(settings, currentTranslation))
        }
        return items
    }

    private fun TranslationInfo.matchesFilter(): Boolean {
        val languageCode = language.split("_")[0]
        val keywords = keywordFilter.toKeywords()
        if (keywords.isEmpty()) return true
        // Every keyword must match, like a web search.
        val haystack = "$shortName $name $language ${languageDisplayName(languageCode)} ${translationRows[shortName]?.let { "${it.language} ${it.languageEnglish} ${it.vernacularTitle}" }.orEmpty()}"
        return keywords.all { haystack.containsAnyKeyword(listOf(it), wholeWords = false) }
    }

    fun applyFilter(keyword: String) {
        keywordFilter = keyword.trim()
        viewModelScope.launch(coroutineDispatcherProvider.default) {
            val settings = settingsManager.settings().first()
            val currentTranslation = bibleReadingManager.currentTranslation().first()
            updateViewState { it.copy(items = buildItems(settings, currentTranslation)) }
        }
    }

    /** Reads every row of the bundled translation table for [translation], as (label, value) pairs. */
    fun readTranslationDetails(translation: TranslationInfo, onLoaded: (List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch {
            val details = runCatching {
                withContext(coroutineDispatcherProvider.default) {
                    TranslationCatalog.rows(application, "https://ebible.org/")
                        .firstOrNull { it.id == translation.shortName }
                        ?.let { row ->
                            buildList {
                                add(application.getString(R.string.translation_detail_id) to row.id)
                                if (row.territory.isNotEmpty()) add(application.getString(R.string.translation_detail_territory) to row.territory)
                                add(application.getString(R.string.translation_detail_language) to row.language)
                                add(application.getString(R.string.translation_detail_language_english) to row.languageEnglish)
                                add(application.getString(R.string.translation_detail_vernacular_title) to row.vernacularTitle)
                                add(application.getString(R.string.translation_detail_english_title) to row.englishTitle)
                            }
                        }
                }
            }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to read translation details" } }
                .getOrNull()
            onLoaded(details ?: listOf(application.getString(R.string.translation_detail_id) to translation.shortName))
        }
    }

    /** Downloads the translation with [shortName] once the translation list is known. Used by one-click downloads. */
    fun downloadTranslation(shortName: String) {
        pendingDownloadShortName = shortName
        tryPendingDownload()
    }

    private fun tryPendingDownload() {
        val shortName = pendingDownloadShortName ?: return
        val translation = (loadedDownloadedTranslations + loadedAvailableTranslations).firstOrNull { it.shortName == shortName } ?: return
        pendingDownloadShortName = null
        if (!translation.downloaded && downloadTranslationJob == null) downloadTranslation(translation)
    }

    private fun List<TranslationInfo>.toItems(settings: Settings, currentTranslation: String): List<TranslationsItem> {
        val items: ArrayList<TranslationsItem> = ArrayList()
        // Translations are grouped by "Language (English)" from translations.csv, with a sub heading for a different native name.
        groupBy { translationRows[it.shortName]?.languageEnglish?.ifEmpty { null } ?: languageDisplayNameWithNative(it.language.split("_")[0]) }
            .forEach { (languageEnglish, translations) ->
                items.add(TranslationsItem.Header(settings, languageEnglish, hideDivider = false, isLanguage = true))
                translations.groupBy { translationRows[it.shortName]?.language.orEmpty() }.forEach { (native, group) ->
                    if (native.isNotEmpty() && native != languageEnglish) items.add(TranslationsItem.Header(settings, native, hideDivider = true))
                    group.forEach { translationInfo ->
                        items.add(TranslationsItem.Translation(
                            settings = settings,
                            translationInfo = translationInfo,
                            isCurrentTranslation = translationInfo.downloaded && translationInfo.shortName == currentTranslation,
                            title = translationRows[translationInfo.shortName]?.displayTitle() ?: translationInfo.name,
                        ))
                    }
                }
            }
        return items
    }

    /** Reads the download sites and passes them to [onLoaded] on the main thread. */
    fun readDownloadSites(onLoaded: (List<String>) -> Unit) {
        viewModelScope.launch {
            val sites = runCatching { withContext(coroutineDispatcherProvider.default) { translationManager.readDownloadSites() } }
                .onFailure { e -> Logger.withTag(tag).e(e) { "Failed to read download sites" } }
                .getOrDefault(emptyList())
            onLoaded(sites)
        }
    }

    fun saveDownloadSites(sites: List<String>) {
        viewModelScope.launch {
            runCatching { withContext(coroutineDispatcherProvider.default) { translationManager.saveDownloadSites(sites) } }
                .onFailure { e -> Logger.withTag(tag).e(e) { "Failed to save download sites" } }
            loadTranslations(forceRefresh = true)
        }
    }

    /** Passes the metadata in the head of the plain USFM file in [uri] (null for a zip or when it has none) to [onLoaded] on the main thread. */
    fun readUsfmMetadata(uri: Uri, onLoaded: (UsfmMetadata?) -> Unit) {
        viewModelScope.launch {
            val metadata = runCatching {
                withContext(coroutineDispatcherProvider.default) {
                    val head = requireNotNull(application.contentResolver.openInputStream(uri)) { "Cannot open $uri" }.use { it.readNBytes(4096) }
                    // Zips start with "PK".
                    if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) null else parseUsfmMetadata(head.decodeToString())
                }
            }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to read USFM metadata" } }.getOrNull()
            onLoaded(metadata)
        }
    }

    /**
     * Installs the Bible in [uri] (a USFM book or a zip of them) as [shortName], listed under [language] (e.g. `en` or `cmn`).
     * Passes whether it succeeded to [onCompleted] on the main thread.
     */
    fun uploadTranslation(uri: Uri, name: String, rawShortName: String, language: String, onCompleted: (Boolean, Throwable?) -> Unit) {
        val shortName = cleanShortName(rawShortName)
        val problem = when {
            name.isBlank() -> "The name is blank"
            language.isBlank() -> "The language is blank"
            shortName.isEmpty() -> "The abbreviation \"$rawShortName\" needs at least one letter after any leading numbers"
            !Regex("[A-Za-z0-9_]+").matches(shortName) -> "The abbreviation \"$shortName\" must only contain letters, digits and underscores"
            loadedDownloadedTranslations.any { it.shortName == shortName } -> "A translation with the abbreviation \"$shortName\" is already installed"
            else -> null
        }
        if (problem != null) {
            onCompleted(false, IllegalArgumentException(problem))
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(coroutineDispatcherProvider.default) {
                    val content = requireNotNull(application.contentResolver.openInputStream(uri)) { "Cannot open $uri" }.use { it.readBytes() }
                    translationManager.importTranslation(TranslationInfo(shortName, name.trim(), language.trim().lowercase(), content.size.toLong(), true), content)
                }
                loadTranslations(forceRefresh = false)
            }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to upload translation" } }
            onCompleted(result.isSuccess, result.exceptionOrNull())
        }
    }

    fun selectTranslation(translationToSelect: TranslationInfo) {
        if (!translationToSelect.downloaded) {
            updateViewState { it.copy(error = ViewState.Error.TranslationNotInstalledError) }
            return
        }

        viewModelScope.launch {
            runCatching {
                bibleReadingManager.saveCurrentTranslation(translationToSelect.shortName)
                emitViewAction(ViewAction.GoBack)
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to select translation" }
                updateViewState { it.copy(error = ViewState.Error.TranslationSelectionError(translationToSelect)) }
            }
        }
    }

    fun downloadTranslation(translationToDownload: TranslationInfo) {
        if (translationToDownload.downloaded) {
            updateViewState { it.copy(error = ViewState.Error.TranslationAlreadyInstalledError) }
            return
        }

        updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Downloading(progress = 0)) }

        downloadTranslationJob = translationManager.downloadTranslation(translationToDownload)
            .onEach { progress ->
                when (progress) {
                    in 0..99 -> updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Downloading(progress = progress)) }
                    100 -> updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Installing) }
                    101 -> {
                        viewModelScope.launch { loadTranslations(forceRefresh = false) }
                        updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Completed(successful = true)) }
                        emitViewAction(ViewAction.GoBack)
                    }
                    else -> throw IllegalArgumentException("Unsupported downloading progress: $progress")
                }
            }
            .catch { e ->
                Logger.withTag(tag).e(e) { "Failed to download translation" }
                updateViewState { current ->
                    current.copy(
                        translationDownloadingState = ViewState.TranslationDownloadingState.Completed(successful = false),
                        error = ViewState.Error.TranslationDownloadingError(translationToDownload),
                    )
                }
            }
            .onCompletion { e ->
                if (e is CancellationException) {
                    Logger.withTag(tag).d { "Translation downloading cancelled" }
                    updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Idle) }
                }
                downloadTranslationJob = null
            }
            .launchIn(viewModelScope)
    }

    fun cancelDownloadingTranslation() {
        downloadTranslationJob?.cancel()
        downloadTranslationJob = null
    }

    fun removeTranslation(translationToRemove: TranslationInfo) {
        if (!translationToRemove.downloaded) {
            updateViewState { it.copy(error = ViewState.Error.TranslationNotInstalledError) }
            return
        }

        updateViewState { it.copy(translationRemovalState = ViewState.TranslationRemovalState.Removing) }

        viewModelScope.launch {
            runCatching {
                translationManager.removeTranslation(translationToRemove)
                loadTranslations(forceRefresh = false)
                updateViewState { it.copy(translationRemovalState = ViewState.TranslationRemovalState.Completed(successful = true)) }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to remove translation" }
                updateViewState { current ->
                    current.copy(
                        translationRemovalState = ViewState.TranslationRemovalState.Completed(successful = false),
                        error = ViewState.Error.TranslationRemovalError(translationToRemove),
                    )
                }
            }
        }
    }

    fun markTranslationDownloadingStateAsIdle() {
        updateViewState { it.copy(translationDownloadingState = ViewState.TranslationDownloadingState.Idle) }
    }

    fun markTranslationRemovalStateAsIdle() {
        updateViewState { it.copy(translationRemovalState = ViewState.TranslationRemovalState.Idle) }
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }
}
