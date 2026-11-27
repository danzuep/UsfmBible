package io.github.danzuep.usfmbible.translations

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.checkbox.MaterialCheckBox
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.databinding.ActivityTranslationUpdatesBinding
import io.github.danzuep.usfmbible.infra.BaseActivity
import io.github.danzuep.usfmbible.ui.ProgressDialog
import io.github.danzuep.usfmbible.ui.listDialog
import io.github.danzuep.usfmbible.ui.progressDialog
import io.github.danzuep.usfmbible.ui.toast
import androidx.activity.viewModels
import io.github.danzuep.usfmbible.container

class TranslationUpdatesActivity : BaseActivity<
    ActivityTranslationUpdatesBinding,
    TranslationUpdatesViewModel.ViewAction,
    TranslationUpdatesViewModel.ViewState,
    TranslationUpdatesViewModel,
    >() {
    override val viewModel: TranslationUpdatesViewModel by viewModels { container.viewModelFactory }

    override val viewBinding: ActivityTranslationUpdatesBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityTranslationUpdatesBinding.inflate(layoutInflater)
    }

    override fun initializeView() = with(viewBinding) {
        toolbar.setNavigationOnClickListener { navigator.goBack() }
        toolbar.inflateMenu(R.menu.menu_translation_updates)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_data_policy) {
                selectDataPolicy()
                true
            } else false
        }
        selectAll.setOnClickListener { viewModel.setAllChecked(selectAll.isChecked) }
        updateButton.setOnClickListener {
            val state = viewModel.currentState
            when (state.status) {
                is TranslationUpdatesViewModel.Status.WaitingForWifi, is TranslationUpdatesViewModel.Status.Updating -> viewModel.cancel()
                is TranslationUpdatesViewModel.Status.NetworkBlocked -> viewModel.checkWhenOnWifi()
                else -> viewModel.update()
            }
        }
    }

    override fun onViewActionEmitted(viewAction: TranslationUpdatesViewModel.ViewAction) = when (viewAction) {
        is TranslationUpdatesViewModel.ViewAction.Completed -> toast(resources.getQuantityString(R.plurals.toast_translations_updated, viewAction.count, viewAction.count))
    }

    override fun onViewStateUpdated(viewState: TranslationUpdatesViewModel.ViewState): Unit = with(viewBinding) {
        val status = viewState.status
        showProgress(status)
        loadingSpinner.isVisible = status is TranslationUpdatesViewModel.Status.Checking
        updatesScroll.isVisible = viewState.items.isNotEmpty()
        updateButton.isVisible = viewState.items.isNotEmpty() || status is TranslationUpdatesViewModel.Status.WaitingForWifi ||
            status is TranslationUpdatesViewModel.Status.NetworkBlocked

        status.message(viewState.items.any { it.unknownVersion })?.let {
            statusText.text = it
            statusText.isVisible = true
        } ?: run { statusText.isVisible = false }

        val cancellable = status is TranslationUpdatesViewModel.Status.WaitingForWifi || status is TranslationUpdatesViewModel.Status.Updating
        updateButton.setText(when {
            cancellable -> android.R.string.cancel
            status is TranslationUpdatesViewModel.Status.NetworkBlocked -> R.string.button_check_on_wifi
            else -> R.string.button_update
        })
        updateButton.isEnabled = cancellable || status is TranslationUpdatesViewModel.Status.NetworkBlocked || viewState.items.any { it.checked }
        selectAll.isEnabled = !viewState.locked
        selectAll.isChecked = viewState.allChecked

        // Rebuilt only when the rows change, so that ticking a box does not recreate the view being tapped.
        val rows = viewState.items.map { it.translation.shortName to it.unknownVersion }
        if (rows != shownRows) {
            shownRows = rows
            updateList.removeAllViews()
            viewState.items.forEach { item ->
                updateList.addView(MaterialCheckBox(this@TranslationUpdatesActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.item_height))
                    setPadding(resources.getDimensionPixelSize(R.dimen.padding), 0, resources.getDimensionPixelSize(R.dimen.padding), 0)
                    text = if (item.unknownVersion) "${item.translation.name} ?" else item.translation.name
                    tag = item.translation.shortName
                    setOnClickListener { viewModel.setChecked(item.translation.shortName, isChecked) }
                })
            }
        }
        for (i in 0 until updateList.childCount) {
            val box = updateList.getChildAt(i) as MaterialCheckBox
            box.isChecked = viewState.items.firstOrNull { it.translation.shortName == box.tag }?.checked == true
            box.isEnabled = !viewState.locked
        }
    }

    private fun selectDataPolicy() {
        lifecycleScope.launch {
            listDialog(
                title = R.string.settings_title_data_policy,
                items = arrayOf(R.string.settings_text_data_policy_wifi, R.string.settings_text_data_policy_mobile, R.string.settings_text_data_policy_roaming)
                    .map { getString(it) }.toTypedArray(),
                selected = viewModel.dataPolicy(),
                onSelected = viewModel::saveDataPolicy,
            )
        }
    }

    private var updateProgress: ProgressDialog? = null

    private fun showProgress(status: TranslationUpdatesViewModel.Status) {
        if (status is TranslationUpdatesViewModel.Status.Updating) {
            if (updateProgress == null) {
                updateProgress = progressDialog(R.string.dialog_title_downloading, 100) { viewModel.cancel() }
            }
            updateProgress?.setProgress(status.progress)
        } else {
            updateProgress?.dismiss()
            updateProgress = null
        }
    }

    private var shownRows: List<Pair<String, Boolean>> = emptyList()

    private fun TranslationUpdatesViewModel.Status.message(hasUnknown: Boolean): String? = when (this) {
        is TranslationUpdatesViewModel.Status.Checking -> getString(R.string.text_checking_for_updates)
        is TranslationUpdatesViewModel.Status.UpToDate -> getString(R.string.text_up_to_date)
        is TranslationUpdatesViewModel.Status.CheckFailed -> getString(R.string.text_update_check_failed)
        is TranslationUpdatesViewModel.Status.NetworkBlocked -> getString(R.string.text_network_blocked)
        is TranslationUpdatesViewModel.Status.WaitingForWifi -> getString(R.string.text_waiting_for_wifi)
        is TranslationUpdatesViewModel.Status.Updating -> getString(R.string.text_updating, name, progress)
        is TranslationUpdatesViewModel.Status.UpdateFailed -> getString(R.string.text_update_failed, name)
        is TranslationUpdatesViewModel.Status.Idle -> if (hasUnknown) getString(R.string.text_update_version_unknown) else null
    }
}
