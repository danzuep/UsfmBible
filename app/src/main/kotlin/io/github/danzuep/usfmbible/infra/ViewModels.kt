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

import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import io.github.danzuep.usfmbible.AppContainer
import io.github.danzuep.usfmbible.container
import kotlin.reflect.KClass

/** Keeps a platform independent [BaseViewModel] across configuration changes and clears it with the screen. */
class ViewModelHolder(val viewModel: BaseViewModel<*, *>) : ViewModel() {
    override fun onCleared() {
        viewModel.clear()
    }
}

/** The view model of this activity, built by [AppContainer.createViewModel] the first time it is read. */
inline fun <reified VM : BaseViewModel<*, *>> ComponentActivity.viewModelOf(): Lazy<VM> = lazy { obtainViewModel(this, container, VM::class) }

/** The view model of this fragment, built by [AppContainer.createViewModel] the first time it is read. */
inline fun <reified VM : BaseViewModel<*, *>> Fragment.viewModelOf(): Lazy<VM> = lazy { obtainViewModel(this, requireContext().container, VM::class) }

fun <VM : BaseViewModel<*, *>> obtainViewModel(owner: ViewModelStoreOwner, container: AppContainer, type: KClass<VM>): VM {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ViewModelHolder(container.createViewModel(type)) as T
    }
    val holder = ViewModelProvider(owner, factory)[type.java.name, ViewModelHolder::class.java]
    return type.java.cast(holder.viewModel)!!
}
