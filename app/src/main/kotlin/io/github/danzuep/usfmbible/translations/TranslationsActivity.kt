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

import android.content.Intent
import io.github.danzuep.usfmbible.core.repository.remote.UsfmMetadata
import io.github.danzuep.usfmbible.core.repository.remote.cleanShortName
import io.github.danzuep.usfmbible.core.repository.remote.parseFileNameMetadata
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.asExecutor
import io.github.danzuep.usfmbible.Navigator
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.TranslationInfo
import io.github.danzuep.usfmbible.core.provider.CoroutineDispatcherProvider
import io.github.danzuep.usfmbible.databinding.ActivityTranslationsBinding
import io.github.danzuep.usfmbible.databinding.DialogUploadTranslationBinding
import io.github.danzuep.usfmbible.infra.BaseActivity
import io.github.danzuep.usfmbible.ui.ProgressDialog
import io.github.danzuep.usfmbible.ui.dialog
import io.github.danzuep.usfmbible.ui.fadeIn
import io.github.danzuep.usfmbible.ui.indeterminateProgressDialog
import io.github.danzuep.usfmbible.ui.progressDialog
import io.github.danzuep.usfmbible.ui.showCopyright
import io.github.danzuep.usfmbible.ui.toast
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.android.ext.android.inject

class TranslationsActivity : BaseActivity<ActivityTranslationsBinding, TranslationsViewModel.ViewAction, TranslationsViewModel.ViewState, TranslationsViewModel>() {
    companion object {
        const val KEY_DOWNLOAD_TRANSLATION = "io.github.danzuep.usfmbible.KEY_DOWNLOAD_TRANSLATION"
    }

    val coroutineDispatcherProvider: CoroutineDispatcherProvider by inject()

    private lateinit var adapter: TranslationsAdapter

    private val selectFileForUploadLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { showUploadTranslationDialog(it) }
    }

    private var downloadTranslationDialog: ProgressDialog? = null
    private var removeTranslationDialog: AlertDialog? = null

    override val viewModel: TranslationsViewModel by viewModel()

    override val viewBinding: ActivityTranslationsBinding by lazy(LazyThreadSafetyMode.NONE) { ActivityTranslationsBinding.inflate(layoutInflater) }

    override fun initializeView() {
        viewBinding.toolbar.setNavigationOnClickListener { navigator.goBack(this) }
        adapter = TranslationsAdapter(
            inflater = layoutInflater,
            executor = coroutineDispatcherProvider.default.asExecutor()
        ) { viewEvent ->
            when (viewEvent) {
                is TranslationsAdapter.ViewEvent.DownloadTranslation -> showDownloadTranslationDialog(viewEvent.translationToDownload)
                is TranslationsAdapter.ViewEvent.RemoveTranslation -> {
                    dialog(
                        cancelable = true,
                        title = viewEvent.translationToRemove.name,
                        message = R.string.dialog_message_delete_translation_confirmation,
                        onPositive = { _, _ -> viewModel.removeTranslation(viewEvent.translationToRemove) },
                    )
                }
                is TranslationsAdapter.ViewEvent.SelectTranslation -> viewModel.selectTranslation(viewEvent.translationToSelect)
            }
        }
        viewBinding.translationList.adapter = adapter
        setupFastScroller()

        viewBinding.filterKeyword.setText(viewModel.keywordFilter)
        viewBinding.filterKeyword.doAfterTextChanged { text ->
            viewModel.applyFilter(text.toString())
        }

        with(viewBinding.toolbar) {
            inflateMenu(R.menu.menu_translations)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_download_sites -> {
                        showDownloadSitesDialog()
                        true
                    }
                    R.id.action_check_for_updates -> {
                        navigator.navigate(this@TranslationsActivity, Navigator.SCREEN_TRANSLATION_UPDATES)
                        true
                    }
                    R.id.action_upload_translation -> {
                        selectFileForUploadLauncher.launch(Intent.createChooser(
                            // The picker filters by MIME type only; USFM/SFM files are usually reported as one of these.
                            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip")
                                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                    "application/zip", "application/x-zip-compressed",
                                    "text/plain", "text/x-usfm", "application/octet-stream"
                                ))
                                .addCategory(Intent.CATEGORY_OPENABLE),
                            getString(R.string.action_upload_translation)
                        ))
                        true
                    }
                    else -> false
                }
            }
        }

        with(viewBinding.swipeRefresher) {
            setColorSchemeResources(R.color.primary, R.color.secondary, R.color.dark_cyan, R.color.dark_lime)
            setOnRefreshListener { viewModel.loadTranslations(forceRefresh = true) }
        }

        intent.getStringExtra(KEY_DOWNLOAD_TRANSLATION)?.let { viewModel.downloadTranslation(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && hasUsfmName(uri)) openFile(uri) else if (uri != null) finish()
        }
    }

    private fun displayName(uri: Uri): String? =
        runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            ?: uri.lastPathSegment

    /** The MIME filters also match any text or unknown-type file, so check the extension here; an unnamed file is given the benefit of the doubt. */
    private fun hasUsfmName(uri: Uri): Boolean {
        val extension = displayName(uri)?.substringAfterLast('.', "")?.lowercase() ?: return true
        return extension.isEmpty() || extension in setOf("usfm", "sfm", "zip")
    }

    private fun setupFastScroller() {
        viewBinding.fastScrollTrack.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val fraction = (event.y / view.height).coerceIn(0f, 1f)
                    scrollToLanguage(fraction)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.performClick()
                    viewBinding.fastScrollBubble.isVisible = false
                    true
                }
                else -> false
            }
        }
    }

    private fun scrollToLanguage(fraction: Float) {
        // Only language headers are used for the index; the "Available" section header and native name sub headings are not languages.
        val headers = adapter.currentList.withIndex()
            .filter { (it.value as? TranslationsItem.Header)?.isLanguage == true }
        if (headers.isEmpty()) return

        val index = (fraction * headers.size).toInt().coerceIn(0, headers.lastIndex)
        val header = headers[index]
        (viewBinding.translationList.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(header.index, 0)

        with(viewBinding.fastScrollBubble) {
            text = (header.value as TranslationsItem.Header).title.firstOrNull()?.uppercase() ?: ""
            isVisible = true
            val trackHeight = viewBinding.fastScrollTrack.height
            translationY = (fraction * trackHeight - height / 2f).coerceIn(0f, (trackHeight - height).toFloat().coerceAtLeast(0f))
        }
    }

    private fun showDownloadTranslationDialog(translation: TranslationInfo) {
        viewModel.readTranslationDetails(translation) { details ->
            if (isDestroyed) return@readTranslationDetails
            val content = layoutInflater.inflate(R.layout.dialog_translation_details, null)
            content.findViewById<TextView>(R.id.details_text).text = details.joinToString("\n\n") { (label, value) -> "$label: $value" }
            val dialog = AlertDialog.Builder(this)
                .setTitle(translation.name)
                .setView(content)
                .create()
            content.findViewById<View>(R.id.details_copyright).setOnClickListener { showCopyright(translation.shortName) }
            content.findViewById<View>(R.id.details_cancel).setOnClickListener { dialog.dismiss() }
            content.findViewById<View>(R.id.details_download).setOnClickListener {
                dialog.dismiss()
                viewModel.downloadTranslation(translation)
            }
            dialog.show()
        }
    }

    private fun showDownloadSitesDialog() {
        viewModel.readDownloadSites { sites ->
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setHorizontallyScrolling(false)
                minLines = 3
                setText(sites.joinToString("\n"))
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_download_sites)
                .setMessage(R.string.dialog_message_download_sites)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.saveDownloadSites(input.text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /** Installs the opened file straight away if it names its translation and language, otherwise asks for them. */
    private fun openFile(uri: Uri) = viewModel.readUsfmMetadata(uri) { metadata ->
        val shortName = metadata?.shortName
        val language = metadata?.language
        if (shortName == null || language == null) {
            showUploadTranslationDialog(uri, metadata)
            return@readUsfmMetadata
        }
        val progress = indeterminateProgressDialog(R.string.dialog_title_uploading)
        viewModel.uploadTranslation(uri, metadata.name ?: shortName, shortName, language) { successful, error ->
            progress?.dismiss()
            if (successful) toast(R.string.toast_uploaded) else showUploadError(error)
        }
    }

    private fun showUploadError(error: Throwable?) = dialog(
        cancelable = true, title = R.string.dialog_title_error, message = R.string.dialog_message_failed_to_upload, onPositive = { _, _ -> }, error = error,
    )

    private fun showUploadTranslationDialog(uri: Uri, metadata: UsfmMetadata? = null) {
        val form = DialogUploadTranslationBinding.inflate(layoutInflater)
        val fileName = displayName(uri).orEmpty().substringBeforeLast('.')
        val guess = metadata ?: parseFileNameMetadata(fileName)
        form.uploadName.setText(guess?.name ?: fileName)
        form.uploadShortName.setText(cleanShortName(guess?.shortName ?: fileName))
        form.uploadLanguage.setText(guess?.language ?: java.util.Locale.getDefault().language)

        val uploadDialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_upload_translation)
            .setView(form.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val progress = indeterminateProgressDialog(R.string.dialog_title_uploading)
                viewModel.uploadTranslation(
                    uri, form.uploadName.text.toString(), form.uploadShortName.text.toString().trim(), form.uploadLanguage.text.toString()
                ) { successful, error ->
                    progress?.dismiss()
                    if (successful) toast(R.string.toast_uploaded) else showUploadError(error)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        form.uploadShortName.doAfterTextChanged { text ->
            val typed = text.toString()
            val cleaned = cleanShortName(typed)
            if (cleaned != typed.trim() && typed.isNotEmpty()) {
                form.uploadShortName.setText(cleaned)
                form.uploadShortName.setSelection(cleaned.length)
                return@doAfterTextChanged
            }
            form.uploadShortNameLayout.error = if (cleaned.isEmpty()) getString(R.string.error_upload_short_name) else null
            uploadDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = cleaned.isNotEmpty()
        }
        uploadDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = form.uploadShortName.text?.isNotEmpty() == true
    }

    override fun onViewActionEmitted(viewAction: TranslationsViewModel.ViewAction) = when (viewAction) {
        TranslationsViewModel.ViewAction.GoBack -> navigator.goBack(this)
    }

    override fun onViewStateUpdated(viewState: TranslationsViewModel.ViewState): Unit = with(viewBinding) {
        if (viewState.loading) {
            swipeRefresher.isRefreshing = true
            translationList.isVisible = false
        } else {
            swipeRefresher.isRefreshing = false
            translationList.fadeIn()
        }

        adapter.submitList(viewState.items)

        viewState.translationDownloadingState.handle()
        viewState.translationRemovalState.handle()
        viewState.error?.handle()
    }

    private fun TranslationsViewModel.ViewState.TranslationDownloadingState.handle() {
        when (this) {
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Idle -> {
                downloadTranslationDialog?.dismiss()
                downloadTranslationDialog = null
            }
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Downloading -> {
                if (downloadTranslationDialog == null) {
                    downloadTranslationDialog = progressDialog(R.string.dialog_title_downloading, 100) { viewModel.cancelDownloadingTranslation() }
                }
                downloadTranslationDialog?.setProgress(progress)
            }
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Installing -> {
                downloadTranslationDialog?.let { dialog ->
                    dialog.setTitle(R.string.dialog_title_installing)
                    dialog.setIsIndeterminate(true)
                }
            }
            is TranslationsViewModel.ViewState.TranslationDownloadingState.Completed -> {
                downloadTranslationDialog?.dismiss()
                downloadTranslationDialog = null

                if (successful) {
                    toast(R.string.toast_downloaded)
                }

                viewModel.markTranslationDownloadingStateAsIdle()
            }
        }
    }

    private fun TranslationsViewModel.ViewState.TranslationRemovalState.handle() {
        when (this) {
            is TranslationsViewModel.ViewState.TranslationRemovalState.Idle -> {
                removeTranslationDialog?.dismiss()
                removeTranslationDialog = null
            }
            is TranslationsViewModel.ViewState.TranslationRemovalState.Removing -> {
                if (removeTranslationDialog == null) {
                    removeTranslationDialog = indeterminateProgressDialog(R.string.dialog_title_deleting)
                }
            }
            is TranslationsViewModel.ViewState.TranslationRemovalState.Completed -> {
                removeTranslationDialog?.dismiss()
                removeTranslationDialog = null

                if (successful) {
                    toast(R.string.toast_deleted)
                }

                viewModel.markTranslationRemovalStateAsIdle()
            }
        }
    }

    private fun TranslationsViewModel.ViewState.Error.handle() = when (this) {
        is TranslationsViewModel.ViewState.Error.NoTranslationsError -> {
            dialog(
                cancelable = false,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_load_translation_list,
                onPositive = { _, _ -> viewModel.loadTranslations(forceRefresh = true) },
                onNegative = { _, _ -> navigator.goBack(this@TranslationsActivity) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
        is TranslationsViewModel.ViewState.Error.TranslationAlreadyInstalledError,
        is TranslationsViewModel.ViewState.Error.TranslationNotInstalledError -> {
            toast(R.string.toast_unknown_error)
            viewModel.markErrorAsShown(this)
        }
        is TranslationsViewModel.ViewState.Error.TranslationDownloadingError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_download,
                onPositive = { _, _ -> viewModel.downloadTranslation(translationToDownload) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
        is TranslationsViewModel.ViewState.Error.TranslationRemovalError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_delete,
                onPositive = { _, _ -> viewModel.removeTranslation(translationToRemove) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
        is TranslationsViewModel.ViewState.Error.TranslationSelectionError -> {
            dialog(
                cancelable = true,
                title = R.string.dialog_title_error,
                message = R.string.dialog_message_failed_to_select_translation,
                onPositive = { _, _ -> viewModel.selectTranslation(translationToSelect) },
                onDismiss = { viewModel.markErrorAsShown(this) },
            )
        }
    }
}
