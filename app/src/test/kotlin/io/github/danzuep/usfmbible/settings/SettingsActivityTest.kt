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

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.mockk.Ordering
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.Highlight
import io.github.danzuep.usfmbible.Navigator
import io.github.danzuep.usfmbible.Screen
import io.github.danzuep.usfmbible.databinding.ActivitySettingsBinding
import io.github.danzuep.usfmbible.databinding.InnerSettingButtonBinding
import io.github.danzuep.usfmbible.settings.widgets.SettingButton
import io.github.danzuep.usfmbible.tests.BaseActivityTest
import io.github.danzuep.usfmbible.tests.getProperty
import io.github.danzuep.usfmbible.tests.performClickPressed
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import io.github.danzuep.usfmbible.tests.TestContainer

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest : BaseActivityTest() {
    private val mockNavigator: Navigator = mockk()

    val viewModel: SettingsViewModel = mockk(relaxed = true)

    override fun createContainer(application: Application) = TestContainer(application, mockNavigator, viewModel)

    @Test
    fun `test initializeView()`() {
        withActivity<SettingsActivity> { activity ->
            val viewBinding: ActivitySettingsBinding = activity.getProperty("viewBinding")
            viewBinding.keepScreenOn.performClickPressed()
            viewBinding.showSearchButton.performClickPressed()
            viewBinding.fontSize.performClick()
            viewBinding.nightMode.performClick()
            viewBinding.defaultHighlightColor.performClick()
            viewBinding.backup.performClick()
            viewBinding.restore.performClick()
            viewBinding.rate.performClick()
            viewBinding.website.performClick()

            verify(ordering = Ordering.ORDERED) {
                viewModel.saveKeepScreenOn(true)
                viewModel.saveShowSearchButton(true)
                viewModel.selectFontSizeScale()
                viewModel.selectNightMode()
                viewModel.selectHighlightColor()
                viewModel.backup()
                viewModel.restore()
                viewModel.openRateMe()
                viewModel.openWebsite()
            }
        }
    }

    @Test
    fun `test onViewActionEmitted(), OpenRateMe`() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.OpenRateMe)
        every { mockNavigator.navigate(Screen.RATE_ME, any()) } returns Unit

        withActivity<SettingsActivity> { activity ->
            verify(exactly = 1) { mockNavigator.navigate(Screen.RATE_ME, any()) }
        }
    }

    @Test
    fun `test onViewActionEmitted(), OpenWebsite`() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.OpenWebsite)
        every { mockNavigator.navigate(Screen.WEBSITE, any()) } returns Unit

        withActivity<SettingsActivity> { activity ->
            verify(exactly = 1) { mockNavigator.navigate(Screen.WEBSITE, any()) }
        }
    }

    @Test
    fun `test onViewActionEmitted(), RequestUriForBackup`() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.RequestUriForBackup)

        withActivity<SettingsActivity> {
            with(Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity) {
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, action)
                assertEquals("application/json", type)
                assertEquals(setOf(Intent.CATEGORY_OPENABLE), categories)
            }
        }
    }

    @Test
    fun `test onViewActionEmitted(), RequestUriForRestore`() {
        every { viewModel.viewAction() } returns flowOf(SettingsViewModel.ViewAction.RequestUriForRestore)

        withActivity<SettingsActivity> {
            with(Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity) {
                assertEquals(Intent.ACTION_CHOOSER, action)

                val intent = getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                assertEquals(Intent.ACTION_GET_CONTENT, intent?.action)
                assertEquals("*/*", intent?.type)
                assertEquals(setOf(Intent.CATEGORY_OPENABLE), intent?.categories)
            }
        }
    }

    @Test
    fun `test onViewStateUpdated(), with completed backup state, null font size selection, null night mode selection, null highlight color selection, and null error`() {
        every { viewModel.viewState() } returns flowOf(SettingsViewModel.ViewState(
            fontSizeScale = 1.0F,
            bodyTextSizePx = 2,
            captionTextSizePx = 3,
            keepScreenOn = true,
            nightMode = Settings.NIGHT_MODE_OFF,
            simpleReadingModeOn = true,
            showSearchButton = false,
            rubyOn = false,
            defaultHighlightColor = Highlight.COLOR_NONE,
            backupState = SettingsViewModel.ViewState.BackupRestoreState.Completed(successful = true),
            restoreState = SettingsViewModel.ViewState.BackupRestoreState.Idle,
            version = "app_version",
            fontSizeScaleSelection = null,
            nightModeSelection = null,
            highlightColorSelection = null,
            error = null,
        ))

        withActivity<SettingsActivity> { activity ->
            val viewBinding: ActivitySettingsBinding = activity.getProperty("viewBinding")
            assertEquals("1.0x", viewBinding.fontSize.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertTrue(viewBinding.keepScreenOn.isChecked)
            assertFalse(viewBinding.showSearchButton.isChecked)
            assertFalse(viewBinding.ruby.isChecked)
            assertEquals(activity.getString(R.string.settings_text_night_mode_off), viewBinding.nightMode.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals(activity.getString(R.string.text_highlight_color_none), viewBinding.defaultHighlightColor.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals("app_version", viewBinding.version.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            verify(exactly = 1) { viewModel.markBackupStateAsIdle() }
        }
    }

    @Test
    fun `test onViewStateUpdated(), with completed restore state, null font size selection, null night mode selection, null highlight color selection, and null error`() {
        every { viewModel.viewState() } returns flowOf(SettingsViewModel.ViewState(
            fontSizeScale = 1.0F,
            bodyTextSizePx = 2,
            captionTextSizePx = 3,
            keepScreenOn = true,
            nightMode = Settings.NIGHT_MODE_OFF,
            simpleReadingModeOn = true,
            showSearchButton = false,
            rubyOn = false,
            defaultHighlightColor = Highlight.COLOR_NONE,
            backupState = SettingsViewModel.ViewState.BackupRestoreState.Idle,
            restoreState = SettingsViewModel.ViewState.BackupRestoreState.Completed(successful = false),
            version = "app_version",
            fontSizeScaleSelection = null,
            nightModeSelection = null,
            highlightColorSelection = null,
            error = null,
        ))

        withActivity<SettingsActivity> { activity ->
            val viewBinding: ActivitySettingsBinding = activity.getProperty("viewBinding")
            assertEquals("1.0x", viewBinding.fontSize.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertTrue(viewBinding.keepScreenOn.isChecked)
            assertFalse(viewBinding.showSearchButton.isChecked)
            assertFalse(viewBinding.ruby.isChecked)
            assertEquals(activity.getString(R.string.settings_text_night_mode_off), viewBinding.nightMode.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals(activity.getString(R.string.text_highlight_color_none), viewBinding.defaultHighlightColor.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            assertEquals("app_version", viewBinding.version.getProperty<SettingButton, InnerSettingButtonBinding>("viewBinding").description.text)
            verify(exactly = 1) { viewModel.markRestoreStateAsIdle() }
        }
    }
}
