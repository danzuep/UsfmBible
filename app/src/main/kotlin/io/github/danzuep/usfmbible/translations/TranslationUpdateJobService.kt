package io.github.danzuep.usfmbible.translations

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import co.touchlab.kermit.Logger
import io.github.danzuep.usfmbible.R
import io.github.danzuep.usfmbible.core.TranslationManager
import io.github.danzuep.usfmbible.ui.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Updates translations once the device is on an unmetered network (e.g. Wi-Fi), when mobile data is off, roaming or unavailable, using the platform [JobScheduler] so no
 * extra library is needed. Scheduled only when the user opens the updates page or asks to update; interrupted downloads resume later.
 */
class TranslationUpdateJobService : JobService() {
    val translationManager: TranslationManager by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val shortNames = params.extras.getStringArray(KEY_SHORT_NAMES)?.toSet().orEmpty()
        job = scope.launch {
            var failed = false
            runCatching {
                translationManager.reload(false)
                // No names means the check itself was deferred, so find what is outdated now.
                val targets = if (shortNames.isEmpty()) {
                    findTranslationUpdates(applicationContext, translationManager).orEmpty().map { it.translation }
                } else {
                    translationManager.downloadedTranslations().first().filter { it.shortName in shortNames }
                }
                targets.forEach { translationManager.downloadTranslation(it).collect {} }
            }.onFailure { e ->
                Logger.withTag(TAG).e(e) { "Failed to update translations" }
                failed = true
            }
            if (!failed) Handler(Looper.getMainLooper()).post { applicationContext.toast(R.string.toast_translations_updated_job) }
            jobFinished(params, failed)
        }
        return true
    }

    // The unmetered network went away, so stop and try again when it returns.
    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TranslationUpdateJob"
        private const val JOB_ID = 2031
        private const val KEY_SHORT_NAMES = "shortNames"

        fun schedule(context: Context, shortNames: List<String>) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, TranslationUpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setExtras(PersistableBundle().apply { putStringArray(KEY_SHORT_NAMES, shortNames.toTypedArray()) })
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }

        fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)

        fun isPending(context: Context): Boolean = context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) != null
    }
}
