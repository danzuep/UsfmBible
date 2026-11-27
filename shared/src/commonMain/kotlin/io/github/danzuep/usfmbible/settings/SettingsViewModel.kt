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

package io.github.danzuep.usfmbible.settings

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import io.github.danzuep.usfmbible.core.BackupManager
import io.github.danzuep.usfmbible.core.DocumentReader
import io.github.danzuep.usfmbible.core.DocumentWriter
import io.github.danzuep.usfmbible.core.Highlight
import io.github.danzuep.usfmbible.ui.PlatformUi
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.infra.BaseViewModel
import co.touchlab.kermit.Logger

class SettingsViewModel(
    private val backupManager: BackupManager,
    private val settingsManager: SettingsManager,
    private val platform: PlatformUi,
    private val documentReader: DocumentReader,
    private val documentWriter: DocumentWriter,
    private val coroutineDispatcherProvider: CoroutineDispatcherProvider
) : BaseViewModel<SettingsViewModel.ViewAction, SettingsViewModel.ViewState>(
    initialViewState = ViewState(
        fontSizeScale = Settings.DEFAULT.fontSizeScale,
        bodyTextSizePx = platform.textSizes(Settings.DEFAULT).bodyPx,
        captionTextSizePx = platform.textSizes(Settings.DEFAULT).captionPx,
        keepScreenOn = Settings.DEFAULT.keepScreenOn,
        nightMode = Settings.DEFAULT.nightMode,
        simpleReadingModeOn = Settings.DEFAULT.simpleReadingModeOn,
        showSearchButton = Settings.DEFAULT.showSearchButton,
        rubyOn = Settings.DEFAULT.rubyOn,
        defaultHighlightColor = Settings.DEFAULT.defaultHighlightColor,
        backupState = ViewState.BackupRestoreState.Idle,
        restoreState = ViewState.BackupRestoreState.Idle,
        version = "",
        fontSizeScaleSelection = null,
        nightModeSelection = null,
        highlightColorSelection = null,
        error = null,
    )
) {
    sealed class ViewAction {
        object OpenRateMe : ViewAction()
        object OpenWebsite : ViewAction()
        object RequestUriForBackup : ViewAction()
        object RequestUriForRestore : ViewAction()
    }

    data class ViewState(
        val fontSizeScale: Float,
        val bodyTextSizePx: Int,
        val captionTextSizePx: Int,
        val keepScreenOn: Boolean,
        @Settings.Companion.NightMode val nightMode: Int,
        val simpleReadingModeOn: Boolean,
        val showSearchButton: Boolean,
        val rubyOn: Boolean,
        @Highlight.Companion.AvailableColor val defaultHighlightColor: Int,
        val backupState: BackupRestoreState,
        val restoreState: BackupRestoreState,
        val version: String,
        val fontSizeScaleSelection: FontSizeScaleSelection?,
        val nightModeSelection: NightModeSelection?,
        val highlightColorSelection: HighlightColorSelection?,
        val error: Error?,
    ) {
        sealed class BackupRestoreState {
            object Idle : BackupRestoreState()
            object Ongoing : BackupRestoreState()
            data class Completed(val successful: Boolean) : BackupRestoreState()
        }

        data class FontSizeScaleSelection(
            val currentBodyTextSizePx: Int,
            val currentCaptionTextSizePx: Int,
            val currentScale: Float,
            val minScale: Float,
            val maxScale: Float,
        )

        data class NightModeSelection(val currentPosition: Int, val availableModes: List<NightMode>) {
            data class NightMode(@Settings.Companion.NightMode val nightMode: Int)
        }

        data class HighlightColorSelection(val currentPosition: Int, val availableColors: List<HighlightColor>) {
            data class HighlightColor(@Highlight.Companion.AvailableColor val color: Int)
        }

        sealed class Error {
            object BackupError : Error()
            object RestoreError : Error()
            object SettingsUpdatingError : Error()
        }
    }

    init {
        updateViewState { it.copy(version = runCatching { platform.appVersion }.getOrDefault("")) }

        settingsManager.settings().onEach { settings ->
            updateViewState { current ->
                current.copy(
                    fontSizeScale = settings.fontSizeScale,
                    bodyTextSizePx = platform.textSizes(settings).bodyPx,
                    captionTextSizePx = platform.textSizes(settings).captionPx,
                    keepScreenOn = settings.keepScreenOn,
                    nightMode = settings.nightMode,
                    simpleReadingModeOn = settings.simpleReadingModeOn,
                    showSearchButton = settings.showSearchButton,
                    rubyOn = settings.rubyOn,
                    defaultHighlightColor = settings.defaultHighlightColor,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun saveFontSizeScale(fontSizeScale: Float) {
        viewModelScope.launch {
            updateViewState { it.copy(fontSizeScale = fontSizeScale, fontSizeScaleSelection = null) }

            val current = settingsManager.settings().first()
            runCatching {
                if (current.fontSizeScale != fontSizeScale) {
                    settingsManager.saveSettings(current.copy(fontSizeScale = fontSizeScale))
                }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to save settings" }
                updateViewState { it.copy(fontSizeScale = current.fontSizeScale, error = ViewState.Error.SettingsUpdatingError) }
            }
        }
    }

    fun saveKeepScreenOn(keepScreenOn: Boolean) {
        updateSettings { it.copy(keepScreenOn = keepScreenOn) }
    }

    private inline fun updateSettings(crossinline op: (current: Settings) -> Settings) {
        viewModelScope.launch {
            runCatching {
                val current = settingsManager.settings().first()
                op(current).takeIf { it != current }?.let { settingsManager.saveSettings(it) }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to save settings" }
                updateViewState { it.copy(error = ViewState.Error.SettingsUpdatingError) }
            }
        }
    }

    fun saveNightMode(@Settings.Companion.NightMode nightMode: Int) {
        viewModelScope.launch {
            runCatching {
                val current = settingsManager.settings().first()
                if (current.nightMode != nightMode) {
                    settingsManager.saveSettings(current.copy(nightMode = nightMode))
                }

                // This will restart the activity, so do it AFTER the settings are successfully saved.
                platform.applyNightMode(nightMode)
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to save settings" }
                updateViewState { it.copy(error = ViewState.Error.SettingsUpdatingError) }
            }
        }
    }

    fun saveSimpleReadingModeOn(simpleReadingModeOn: Boolean) {
        updateSettings { it.copy(simpleReadingModeOn = simpleReadingModeOn) }
    }

    fun saveShowSearchButton(showSearchButton: Boolean) {
        updateSettings { it.copy(showSearchButton = showSearchButton) }
    }

    fun saveRubyOn(rubyOn: Boolean) {
        updateSettings { it.copy(rubyOn = rubyOn) }
    }

    fun saveDefaultHighlightColor(@Highlight.Companion.AvailableColor defaultHighlightColor: Int) {
        updateSettings { it.copy(defaultHighlightColor = defaultHighlightColor) }
    }

    fun selectFontSizeScale() {
        viewModelScope.launch {
            val currentSettings = settingsManager.settings().first()
            val currentBodyTextSizePx = platform.textSizes(currentSettings).bodyPx
            val currentCaptionTextSizePx = platform.textSizes(currentSettings).captionPx
            val currentFontSizeScale = currentSettings.fontSizeScale
            updateViewState { currentViewState ->
                currentViewState.copy(
                    fontSizeScaleSelection = ViewState.FontSizeScaleSelection(
                        currentBodyTextSizePx = currentBodyTextSizePx,
                        currentCaptionTextSizePx = currentCaptionTextSizePx,
                        currentScale = currentFontSizeScale,
                        minScale = Settings.MIN_FONT_SIZE_SCALE,
                        maxScale = Settings.MAX_FONT_SIZE_SCALE,
                    )
                )
            }
        }
    }

    fun selectNightMode() {
        viewModelScope.launch {
            val currentNightMode = settingsManager.settings().first().nightMode
            val availableModes = listOf(
                ViewState.NightModeSelection.NightMode(Settings.NIGHT_MODE_ON),
                ViewState.NightModeSelection.NightMode(Settings.NIGHT_MODE_OFF),
                ViewState.NightModeSelection.NightMode(Settings.NIGHT_MODE_FOLLOW_SYSTEM),
            )
            val currentPosition = availableModes.indexOfFirst { it.nightMode == currentNightMode }
            updateViewState { it.copy(nightModeSelection = ViewState.NightModeSelection(currentPosition, availableModes)) }
        }
    }

    fun selectHighlightColor() {
        viewModelScope.launch {
            val currentDefaultHighlightColor = settingsManager.settings().first().defaultHighlightColor
            val availableColors = listOf(
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_NONE),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_YELLOW),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_PINK),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_ORANGE),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_PURPLE),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_RED),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_GREEN),
                ViewState.HighlightColorSelection.HighlightColor(Highlight.COLOR_BLUE),
            )
            val currentPosition = availableColors.indexOfFirst { it.color == currentDefaultHighlightColor }
            updateViewState { it.copy(highlightColorSelection = ViewState.HighlightColorSelection(currentPosition, availableColors)) }
        }
    }

    fun backup() {
        emitViewAction(ViewAction.RequestUriForBackup)
    }

    fun restore() {
        emitViewAction(ViewAction.RequestUriForRestore)
    }

    fun backup(uri: String?) {
        if (uri == null) {
            updateViewState { it.copy(error = ViewState.Error.BackupError) }
            return
        }
        updateViewState { it.copy(backupState = ViewState.BackupRestoreState.Ongoing) }

        viewModelScope.launch(coroutineDispatcherProvider.io) {
            runCatching {
                documentWriter.writeBytes(uri, backupManager.prepareForBackup().encodeToByteArray())
                updateViewState { it.copy(backupState = ViewState.BackupRestoreState.Completed(successful = true)) }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to backup data" }
                updateViewState { current ->
                    current.copy(
                        backupState = ViewState.BackupRestoreState.Completed(successful = false),
                        error = ViewState.Error.BackupError,
                    )
                }
            }
        }
    }

    fun restore(uri: String?) {
        if (uri == null) {
            updateViewState { it.copy(error = ViewState.Error.RestoreError) }
            return
        }
        updateViewState { it.copy(restoreState = ViewState.BackupRestoreState.Ongoing) }

        viewModelScope.launch(coroutineDispatcherProvider.io) {
            runCatching {
                backupManager.restore(documentReader.readBytes(uri).decodeToString())
                updateViewState { it.copy(restoreState = ViewState.BackupRestoreState.Completed(successful = true)) }
            }.onFailure { e ->
                Logger.withTag(tag).e(e) { "Failed to restore data" }
                updateViewState { current ->
                    current.copy(
                        restoreState = ViewState.BackupRestoreState.Completed(successful = false),
                        error = ViewState.Error.RestoreError,
                    )
                }
            }
        }
    }

    fun openRateMe() {
        emitViewAction(ViewAction.OpenRateMe)
    }

    fun openWebsite() {
        emitViewAction(ViewAction.OpenWebsite)
    }

    fun markBackupStateAsIdle() {
        updateViewState { it.copy(backupState = ViewState.BackupRestoreState.Idle) }
    }

    fun markRestoreStateAsIdle() {
        updateViewState { it.copy(restoreState = ViewState.BackupRestoreState.Idle) }
    }

    fun markFontSizeSelectionAsDismissed() {
        updateViewState { it.copy(fontSizeScaleSelection = null) }
    }

    fun markNightModeSelectionAsDismissed() {
        updateViewState { it.copy(nightModeSelection = null) }
    }

    fun markHighlightColorSelectionAsDismissed() {
        updateViewState { it.copy(highlightColorSelection = null) }
    }

    fun markErrorAsShown(error: ViewState.Error) {
        updateViewState { current -> if (current.error == error) current.copy(error = null) else null }
    }
}
