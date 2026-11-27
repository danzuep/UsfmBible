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

package io.github.danzuep.usfmbible.infra

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Platform independent view model: one [ViewState] flow, one-shot [ViewAction]s and a scope that lives until [clear].
 * The UI only renders the state and forwards user input, so everything else can be tested on the JVM.
 */
abstract class BaseViewModel<ViewAction, ViewState>(initialViewState: ViewState) {
    protected val tag: String = this::class.simpleName ?: "ViewModel"

    /** Runs on the main dispatcher, which tests replace with `Dispatchers.setMain`. */
    protected val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val viewAction: MutableSharedFlow<ViewAction> = MutableSharedFlow()
    private val viewState: MutableStateFlow<ViewState> = MutableStateFlow(initialViewState)

    fun viewAction(): Flow<ViewAction> = viewAction

    fun emitViewAction(action: ViewAction) {
        viewModelScope.launch { viewAction.emit(action) }
    }

    fun viewState(): Flow<ViewState> = viewState

    protected fun updateViewState(block: (currentViewState: ViewState) -> ViewState?) {
        viewState.update { block(it) ?: it }
    }

    /** Cancels everything the view model started, called by the platform once the screen is gone for good. */
    fun clear() {
        viewModelScope.cancel()
        onCleared()
    }

    protected open fun onCleared() {}
}
