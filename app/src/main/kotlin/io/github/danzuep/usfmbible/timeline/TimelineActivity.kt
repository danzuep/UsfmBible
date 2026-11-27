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

package io.github.danzuep.usfmbible.timeline

import android.widget.Spinner
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.asExecutor
import io.github.danzuep.usfmbible.Screen
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseAdapter
import io.github.danzuep.usfmbible.annotated.AnnotatedVerseItem
import io.github.danzuep.usfmbible.annotated.initializeSortOrder
import io.github.danzuep.usfmbible.core.ReadingTimeline
import io.github.danzuep.usfmbible.core.VerseIndex
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.databinding.ActivityTimelineBinding
import io.github.danzuep.usfmbible.infra.BaseActivity
import io.github.danzuep.usfmbible.ui.dialog
import io.github.danzuep.usfmbible.ui.fadeIn
import io.github.danzuep.usfmbible.utils.localized
import androidx.activity.viewModels
import io.github.danzuep.usfmbible.container

class TimelineActivity : BaseActivity<ActivityTimelineBinding, TimelineViewModel.ViewAction, TimelineViewModel.ViewState, TimelineViewModel>() {
    val coroutineDispatcherProvider: CoroutineDispatcherProvider
        get() = container.coroutineDispatcherProvider

    private lateinit var adapter: AnnotatedVerseAdapter
    private var limit = ReadingTimeline.DEFAULT_LIMIT
    private var timelineItems = emptyList<AnnotatedVerseItem>()
    private val selectedVerses = linkedSetOf<VerseIndex>()
    private var selectionMode = false

    override val viewModel: TimelineViewModel by viewModels { container.viewModelFactory }

    override val viewBinding: ActivityTimelineBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityTimelineBinding.inflate(layoutInflater) }

    override fun initializeView() = with(viewBinding) {
        toolbar.setNavigationOnClickListener { navigator.goBack() }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_timeline_select_all -> {
                    toggleSelectAll()
                    true
                }
                R.id.action_timeline_delete -> {
                    deleteSelectedVerses()
                    true
                }
                R.id.action_timeline_limit -> {
                    showLimitDialog()
                    true
                }
                else -> false
            }
        }

        (toolbar.menu.findItem(R.id.action_sort).actionView as Spinner).initializeSortOrder(viewModel::setSortOrder)

        adapter = AnnotatedVerseAdapter(
            inflater = layoutInflater,
            executor = coroutineDispatcherProvider.default.asExecutor()
        ) { viewEvent ->
            when (viewEvent) {
                is AnnotatedVerseAdapter.ViewEvent.OpenVerse -> viewModel.openVerse(viewEvent.verseToOpen)
                is AnnotatedVerseAdapter.ViewEvent.ShowPreview -> onVerseLongPressed(viewEvent.verseToPreview)
                is AnnotatedVerseAdapter.ViewEvent.SelectionChanged -> updateVerseSelection(viewEvent.verseIndex, viewEvent.selected)
            }
        }
        verseList.adapter = adapter
        updateSelectionMenu()
    }

    override fun onViewActionEmitted(viewAction: TimelineViewModel.ViewAction) = when (viewAction) {
        TimelineViewModel.ViewAction.OpenReadingScreen -> navigator.navigate(Screen.READING)
    }

    override fun onViewStateUpdated(viewState: TimelineViewModel.ViewState): Unit = with(viewBinding) {
        limit = viewState.limit
        toolbar.menu.findItem(R.id.action_timeline_limit).title = viewState.limit.localized()
        (toolbar.menu.findItem(R.id.action_sort).actionView as Spinner).setSelection(viewState.sortOrder)

        if (!viewState.loading) {
            timelineItems = viewState.items
            selectedVerses.retainAll(timelineVerseIndexes())
            if (timelineVerseIndexes().isEmpty()) {
                selectionMode = false
                selectedVerses.clear()
            }
        }

        if (viewState.loading) {
            loadingSpinner.fadeIn()
            verseList.isVisible = false
        } else {
            loadingSpinner.isVisible = false
            verseList.fadeIn()
        }

        adapter.submitList(displayTimelineItems())
        updateSelectionMenu()

        viewState.error?.handle()
    }

    private fun showLimitDialog() {
        if (isDestroyed) return

        val options = ReadingTimeline.LIMIT_OPTIONS
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_timeline_limit)
            .setSingleChoiceItems(options.map { it.localized() }.toTypedArray(), options.indexOf(limit)) { dialog, which ->
                dialog.dismiss()
                if (options[which] != limit) viewModel.saveLimit(options[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onVerseLongPressed(verseIndex: VerseIndex) {
        if (selectionMode) {
            updateVerseSelection(verseIndex, verseIndex !in selectedVerses)
        } else {
            selectionMode = true
            selectedVerses.clear()
            selectedVerses.add(verseIndex)
            renderTimelineSelection()
        }
    }

    private fun updateVerseSelection(verseIndex: VerseIndex, selected: Boolean) {
        if (!selectionMode) return
        if (selected) selectedVerses.add(verseIndex) else selectedVerses.remove(verseIndex)
        renderTimelineSelection()
    }

    private fun toggleSelectAll() {
        if (!selectionMode) return
        val verses = timelineVerseIndexes()
        if (selectedVerses.containsAll(verses)) {
            selectedVerses.clear()
        } else {
            selectedVerses.addAll(verses)
        }
        renderTimelineSelection()
    }

    private fun deleteSelectedVerses() {
        val verses = selectedVerses.toSet()
        if (verses.isEmpty()) return
        selectionMode = false
        selectedVerses.clear()
        renderTimelineSelection()
        viewModel.deleteTimelineVerses(verses)
    }

    private fun timelineVerseIndexes(): Set<VerseIndex> =
        timelineItems.filterIsInstance<AnnotatedVerseItem.Bookmark>().mapTo(linkedSetOf()) { it.verseIndex }

    private fun displayTimelineItems(): List<AnnotatedVerseItem> = timelineItems.map { item ->
        if (item is AnnotatedVerseItem.Bookmark) {
            item.copy(selectionMode = selectionMode, selected = selectionMode && item.verseIndex in selectedVerses)
        } else {
            item
        }
    }

    private fun renderTimelineSelection() {
        adapter.submitList(displayTimelineItems())
        updateSelectionMenu()
    }

    private fun updateSelectionMenu() {
        val menu = viewBinding.toolbar.menu
        menu.findItem(R.id.action_timeline_limit).isVisible = !selectionMode
        menu.findItem(R.id.action_sort).isVisible = !selectionMode
        menu.findItem(R.id.action_timeline_select_all).isVisible = selectionMode
        menu.findItem(R.id.action_timeline_delete).apply {
            isVisible = selectionMode
            isEnabled = selectedVerses.isNotEmpty()
        }
        val allSelected = timelineVerseIndexes().let { it.isNotEmpty() && selectedVerses.containsAll(it) }
        menu.findItem(R.id.action_timeline_select_all).title =
            getString(if (allSelected) R.string.action_deselect_all else R.string.action_select_all)
    }

    private fun TimelineViewModel.ViewState.Error.handle() = when (this) {
        is TimelineViewModel.ViewState.Error.TimelineLoadingError -> {
            dialog(
                cancelable = false,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_load_annotated_verses,
                onPositive = { _, _ -> viewModel.loadTimeline() },
                onNegative = { _, _ -> navigator.goBack() },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
        is TimelineViewModel.ViewState.Error.LimitSavingError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_save_sort_order,
                onPositive = { _, _ -> viewModel.saveLimit(limit) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
        is TimelineViewModel.ViewState.Error.TimelineDeletingError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_delete_timeline,
                onPositive = { _, _ -> viewModel.deleteTimelineVerses(verses) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
        is TimelineViewModel.ViewState.Error.VerseOpeningError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_select_verse,
                onPositive = { _, _ -> viewModel.openVerse(verseToOpen) },
                onDismiss = { viewModel.markErrorAsShown(this) }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.loadTimeline()
    }
}
