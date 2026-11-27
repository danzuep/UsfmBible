package io.github.danzuep.usfmbible.translations

import co.touchlab.kermit.Logger
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.SettingsManager
import io.github.danzuep.usfmbible.core.TranslationInfo
import io.github.danzuep.usfmbible.core.TranslationManager
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.core.repository.remote.NetworkPolicy
import io.github.danzuep.usfmbible.core.repository.remote.TranslationUpdateChecker
import io.github.danzuep.usfmbible.core.repository.remote.TranslationUpdateScheduler
import io.github.danzuep.usfmbible.infra.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Checks the installed translations for newer versions when the page is opened, and updates the ones the user ticks. */
class TranslationUpdatesViewModel(
    private val translationManager: TranslationManager,
    private val checker: TranslationUpdateChecker,
    private val scheduler: TranslationUpdateScheduler,
    private val networkPolicy: NetworkPolicy,
    private val settingsManager: SettingsManager,
    private val coroutineDispatcherProvider: CoroutineDispatcherProvider,
) : BaseViewModel<TranslationUpdatesViewModel.ViewAction, TranslationUpdatesViewModel.ViewState>(
    initialViewState = ViewState(),
) {
    sealed class ViewAction {
        data class Completed(val count: Int) : ViewAction()
    }

    /** [unknownVersion] is true when no version was recorded at install time, e.g. the prepackaged WEB. */
    data class Item(val translation: TranslationInfo, val unknownVersion: Boolean, val checked: Boolean = true)

    sealed class Status {
        object Idle : Status()
        object Checking : Status()
        object UpToDate : Status()
        object CheckFailed : Status()
        object NetworkBlocked : Status()
        object WaitingForWifi : Status()
        data class Updating(val name: String, val progress: Int) : Status()
        data class UpdateFailed(val name: String) : Status()
    }

    data class ViewState(
        val status: Status = Status.Checking,
        val items: List<Item> = emptyList(),
    ) {
        val busy: Boolean get() = status is Status.Checking || status is Status.WaitingForWifi || status is Status.Updating
        // The ticks stay editable while waiting for Wi-Fi, only a running check or download locks them.
        val locked: Boolean get() = status is Status.Checking || status is Status.Updating
        val allChecked: Boolean get() = items.isNotEmpty() && items.all { it.checked }
    }

    // Mirrors the view state so that it can be read synchronously; only touched on the main thread.
    private var state = ViewState()
    val currentState: ViewState get() = state

    private fun mutate(block: (ViewState) -> ViewState) {
        state = block(state)
        updateViewState { state }
    }

    private var updateJob: Job? = null

    init {
        checkForUpdates()
    }

    private fun checkForUpdates() {
        mutate { it.copy(status = Status.Checking, items = emptyList()) }
        viewModelScope.launch {
            if (!networkPolicy.isAllowed()) {
                mutate { it.copy(status = if (scheduler.isPending()) Status.WaitingForWifi else Status.NetworkBlocked) }
                return@launch
            }
            val result = runCatching {
                withContext(coroutineDispatcherProvider.default) { checker.find() }
            }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to check for updates" } }.getOrNull()
                ?.map { Item(it.translation, it.unknownVersion) }
            mutate {
                when {
                    result == null -> it.copy(status = Status.CheckFailed)
                    result.isEmpty() -> it.copy(status = Status.UpToDate)
                    else -> it.copy(
                        status = if (scheduler.isPending()) Status.WaitingForWifi else Status.Idle,
                        items = result,
                    )
                }
            }
        }
    }

    fun setChecked(shortName: String, checked: Boolean) {
        mutate { current -> current.copy(items = current.items.map { if (it.translation.shortName == shortName) it.copy(checked = checked) else it }) }
    }

    fun setAllChecked(checked: Boolean) {
        mutate { current -> current.copy(items = current.items.map { it.copy(checked = checked) }) }
    }

    /** Asked for by the user: check and update later, on an unmetered network, even if they leave this page. */
    fun checkWhenOnWifi() {
        if (state.status !is Status.NetworkBlocked) return
        scheduler.schedule(emptyList())
        mutate { it.copy(status = Status.WaitingForWifi) }
    }

    fun update() {
        if (state.busy || state.items.none { it.checked }) return
        viewModelScope.launch { if (networkPolicy.isAllowed()) startUpdates() else waitForWifi() }
    }

    suspend fun dataPolicy(): Int = settingsManager.settings().first().dataPolicy

    fun saveDataPolicy(@Settings.Companion.DataPolicy dataPolicy: Int) {
        viewModelScope.launch {
            settingsManager.saveSettings(settingsManager.settings().first().copy(dataPolicy = dataPolicy))
            if (!state.busy) checkForUpdates()
        }
    }

    fun cancel() {
        scheduler.cancel()
        updateJob?.cancel()
        updateJob = null
        mutate { if (it.busy) it.copy(status = Status.Idle) else it }
    }

    // The job outlives this page; ACCESS_NETWORK_STATE is already declared and is not a runtime permission.
    private fun waitForWifi() {
        scheduler.schedule(state.items.filter { it.checked }.map { it.translation.shortName })
        mutate { it.copy(status = Status.WaitingForWifi) }
    }

    private fun startUpdates() {
        if (updateJob?.isActive == true) return
        val selected = state.items.filter { it.checked }.map { it.translation }
        updateJob = viewModelScope.launch {
            for (translation in selected) {
                mutate { it.copy(status = Status.Updating(translation.name, 0)) }
                val succeeded = runCatching {
                    translationManager.downloadTranslation(translation).collect { progress ->
                        mutate { it.copy(status = Status.Updating(translation.name, progress.coerceAtMost(100))) }
                    }
                }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to update ${translation.shortName}" } }.isSuccess
                if (!succeeded) {
                    mutate { it.copy(status = Status.UpdateFailed(translation.name)) }
                    return@launch
                }
            }
            emitViewAction(ViewAction.Completed(selected.size))
            checkForUpdates()
        }
    }
}
