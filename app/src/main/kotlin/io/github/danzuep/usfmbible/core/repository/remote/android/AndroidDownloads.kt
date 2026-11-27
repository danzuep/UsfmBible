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
package io.github.danzuep.usfmbible.core.repository.remote.android

import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.danzuep.usfmbible.core.Settings
import io.github.danzuep.usfmbible.core.repository.remote.NetworkPolicy
import io.github.danzuep.usfmbible.core.repository.remote.TranslationCatalog
import io.github.danzuep.usfmbible.core.repository.remote.TranslationTableRow
import io.github.danzuep.usfmbible.core.repository.remote.TranslationUpdateScheduler
import io.github.danzuep.usfmbible.core.repository.remote.TranslationVersions
import io.github.danzuep.usfmbible.core.repository.remote.parseTranslationCsv
import io.github.danzuep.usfmbible.translations.TranslationUpdateJobService
import java.util.concurrent.ConcurrentHashMap

class AndroidNetworkPolicy(context: Context, private val settings: suspend () -> Settings = { Settings.DEFAULT }) : NetworkPolicy {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override suspend fun isAllowed(): Boolean {
        val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return true
        return settings().allowsNetwork(
            unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            roaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
        )
    }
}

/** The bundled `translations.csv`, parsed once per page URL and kept for the life of the process. */
class AssetTranslationCatalog(private val context: Context) : TranslationCatalog {
    override fun rows(pageUrl: String): List<TranslationTableRow> =
        rowsByPageUrl.getOrPut(pageUrl) {
            parseTranslationCsv(context.assets.open("translations.csv").use { it.readBytes().toString(Charsets.UTF_8) }, pageUrl)
        }

    private companion object {
        val rowsByPageUrl = ConcurrentHashMap<String, List<TranslationTableRow>>()
    }
}

class AndroidTranslationVersions(context: Context) : TranslationVersions {
    private val preferences = context.applicationContext.getSharedPreferences("translation_versions", Context.MODE_PRIVATE)

    override fun installed(shortName: String): String? = preferences.getString(shortName, null)

    override fun save(shortName: String, version: String?) {
        preferences.edit().apply { if (version == null) remove(shortName) else putString(shortName, version) }.apply()
    }
}

/** Runs [TranslationUpdateJobService] with the platform job scheduler. */
class AndroidTranslationUpdateScheduler(private val context: Context) : TranslationUpdateScheduler {
    private val scheduler get() = context.getSystemService(JobScheduler::class.java)

    override fun schedule(shortNames: List<String>) {
        scheduler.schedule(TranslationUpdateJobService.jobInfo(ComponentName(context, TranslationUpdateJobService::class.java), shortNames))
    }

    override fun cancel() = scheduler.cancel(TranslationUpdateJobService.JOB_ID)

    override fun isPending(): Boolean = scheduler.getPendingJob(TranslationUpdateJobService.JOB_ID) != null
}
