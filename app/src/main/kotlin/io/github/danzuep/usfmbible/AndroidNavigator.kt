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
package io.github.danzuep.usfmbible

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.os.bundleOf
import io.github.danzuep.usfmbible.annotated.bookmarks.BookmarksActivity
import io.github.danzuep.usfmbible.annotated.highlights.HighlightsActivity
import io.github.danzuep.usfmbible.annotated.notes.NotesActivity
import io.github.danzuep.usfmbible.progress.ReadingProgressActivity
import io.github.danzuep.usfmbible.reading.ReadingActivity
import io.github.danzuep.usfmbible.search.SearchActivity
import io.github.danzuep.usfmbible.settings.SettingsActivity
import io.github.danzuep.usfmbible.timeline.TimelineActivity
import io.github.danzuep.usfmbible.translations.TranslationUpdatesActivity
import io.github.danzuep.usfmbible.translations.TranslationsActivity
import io.github.danzuep.usfmbible.ui.toast
import java.lang.ref.WeakReference

/** Shows each [Screen] as an activity, started from the activity that is currently on screen. */
class AndroidNavigator(private val application: Application) : Navigator {
    companion object {
        /** The home page, also the store listing's website and support link. */
        const val HOME_PAGE_URL = "https://danzuep.github.io/UsfmBible/"
        const val REPOSITORY_URL = "https://github.com/danzuep/UsfmBible"
    }

    private var current: WeakReference<Activity>? = null

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                current = WeakReference(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (current?.get() === activity) current = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    override fun navigate(screen: Screen, extras: Map<String, Any?>) {
        val activity = current?.get()
        val context = activity ?: application
        val intent = when (screen) {
            Screen.READING -> Intent(context, ReadingActivity::class.java)
            Screen.SEARCH -> Intent(context, SearchActivity::class.java)
            Screen.TRANSLATIONS -> Intent(context, TranslationsActivity::class.java)
            Screen.READING_PROGRESS -> Intent(context, ReadingProgressActivity::class.java)
            Screen.BOOKMARKS -> Intent(context, BookmarksActivity::class.java)
            Screen.HIGHLIGHTS -> Intent(context, HighlightsActivity::class.java)
            Screen.NOTES -> Intent(context, NotesActivity::class.java)
            Screen.SETTINGS -> Intent(context, SettingsActivity::class.java)
            Screen.TIMELINE -> Intent(context, TimelineActivity::class.java)
            Screen.TRANSLATION_UPDATES -> Intent(context, TranslationUpdatesActivity::class.java)
            Screen.RATE_ME -> Intent(Intent.ACTION_VIEW).setData(Uri.parse(REPOSITORY_URL))
            Screen.WEBSITE -> Intent(Intent.ACTION_VIEW).setData(Uri.parse(HOME_PAGE_URL))
        }
        if (extras.isNotEmpty()) intent.putExtras(bundleOf(*extras.map { it.key to it.value }.toTypedArray()))
        // Without an activity on screen, the intent has to start a task of its own.
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Web links need a browser, which not every device has.
            application.toast(R.string.toast_no_browser)
        }
    }

    override fun goBack() {
        val activity = current?.get() ?: return
        if (activity is ComponentActivity) {
            activity.onBackPressedDispatcher.onBackPressed()
        } else {
            activity.finish()
        }
    }
}
