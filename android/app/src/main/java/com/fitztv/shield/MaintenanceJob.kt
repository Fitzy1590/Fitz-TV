package com.fitztv.shield

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Nightly maintenance: clears caches, re-disables anything a firmware/app update switched back on,
 * re-applies background blocks, and pre-compiles updated apps. Runs when the device is idle.
 */
class MaintenanceJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Engine.init(this)
        val host = Engine.host(this)
        Engine.worker.execute {
            val result = runCatching { Engine.lines(host, "maintain").joinToString("\n") }
                .getOrElse { Engine.explain(it, host) }
            Engine.save(this, "maint_last", SimpleDateFormat("EEE d MMM HH:mm").format(Date()) + "\n" + result)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val ID = 42

        fun schedule(ctx: Context, on: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!on) { js.cancel(ID); return }
            js.schedule(
                JobInfo.Builder(ID, ComponentName(ctx, MaintenanceJob::class.java))
                    .setPeriodic(TimeUnit.HOURS.toMillis(24), TimeUnit.HOURS.toMillis(6))
                    .setRequiresDeviceIdle(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
