package com.github.danzuep.usfmbible.translations

import android.app.Application
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.danzuep.usfmbible.core.SettingsManager
import com.github.danzuep.usfmbible.core.TranslationInfo
import com.github.danzuep.usfmbible.core.TranslationManager
import com.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import com.github.danzuep.usfmbible.core.repository.remote.UpdateStatus
import com.github.danzuep.usfmbible.core.repository.remote.android.NetworkPolicy
import com.github.danzuep.usfmbible.core.repository.remote.android.TranslationVersions
import com.github.danzuep.usfmbible.core.repository.remote.android.fetchLastModified
import com.github.danzuep.usfmbible.core.repository.remote.siteOrigin
import com.github.danzuep.usfmbible.core.repository.remote.updateStatus
import com.github.danzuep.usfmbible.core.repository.remote.usfmZipUrl
import com.github.danzuep.usfmbible.infra.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Checks the installed translations for newer versions when the page is opened, and updates the ones the user ticks. */
@HiltViewModel
class TranslationUpdatesViewModel @Inject constructor(
    private val translationManager: TranslationManager,
    private val settingsManager: SettingsManager,
    private val application: Application,
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
        val allChecked: Boolean get() = items.isNotEmpty() && items.all { it.checked }
    }

    // Mirrors the view state so that it can be read synchronously; only touched on the main thread.
    private var state = ViewState()
    val currentState: ViewState get() = state

    private fun mutate(block: (ViewState) -> ViewState) {
        state = block(state)
        updateViewState { state }
    }

    private val networkPolicy = NetworkPolicy(application) { settingsManager.settings().first() }
    private var updateJob: Job? = null

    init {
        checkForUpdates()
    }

    private fun checkForUpdates() {
        mutate { it.copy(status = Status.Checking, items = emptyList()) }
        viewModelScope.launch {
            if (!networkPolicy.isAllowed()) {
                mutate { it.copy(status = Status.NetworkBlocked) }
                return@launch
            }
            val result = runCatching {
                withContext(coroutineDispatcherProvider.default) {
                    translationManager.reload(false)
                    val installed = translationManager.downloadedTranslations().first()
                    val origins = translationManager.readDownloadSites().mapNotNull { siteOrigin(it) }.distinct()
                    val versions = TranslationVersions(application)
                    var reachable = false
                    val items = installed.mapNotNull { translation ->
                        val remote = origins.firstNotNullOfOrNull { origin ->
                            runCatching { fetchLastModified(usfmZipUrl(origin, translation.shortName)) }
                                .onFailure { e -> Logger.withTag(tag).w(e) { "Failed to check ${translation.shortName} at $origin" } }
                                .getOrNull()?.also { reachable = true }
                        }
                        val status = updateStatus(versions.installed(translation.shortName), remote)
                        if (remote == null || status == UpdateStatus.UP_TO_DATE) null
                        else Item(translation, unknownVersion = status == UpdateStatus.UNKNOWN)
                    }
                    if (!reachable && installed.isNotEmpty()) null else items
                }
            }.onFailure { e -> Logger.withTag(tag).e(e) { "Failed to check for updates" } }.getOrNull()
            mutate {
                when {
                    result == null -> it.copy(status = Status.CheckFailed)
                    result.isEmpty() -> it.copy(status = Status.UpToDate)
                    else -> it.copy(
                        status = if (TranslationUpdateJobService.isPending(application)) Status.WaitingForWifi else Status.Idle,
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

    fun update() {
        if (state.busy || state.items.none { it.checked }) return
        viewModelScope.launch { if (networkPolicy.isAllowed()) startUpdates() else waitForWifi() }
    }

    fun cancel() {
        TranslationUpdateJobService.cancel(application)
        updateJob?.cancel()
        updateJob = null
        mutate { if (it.busy) it.copy(status = Status.Idle) else it }
    }

    // The job outlives this page; ACCESS_NETWORK_STATE is already declared and is not a runtime permission.
    private fun waitForWifi() {
        TranslationUpdateJobService.schedule(application, state.items.filter { it.checked }.map { it.translation.shortName })
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
