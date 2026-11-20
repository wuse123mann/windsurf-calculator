package de.wuse.windsurfrechner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Wind-Alarm (Premium): prüft im Hintergrund alle paar Stunden die Spots mit aktivem Alarm und meldet,
 * wenn im eingestellten Zeitraum genug gleitbare Stunden angesagt sind.
 * Pro Spot und Gleittag kommt höchstens eine Benachrichtigung.
 */
object WindAlarm {
    private const val PERIODIC = "windalarm"
    private const val NOW = "windalarm_jetzt"
    const val CHANNEL = "windalarm"
    const val EXTRA_SPOT = "spot_id"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<WindAlarmWorker>(3, TimeUnit.HOURS)
            .setConstraints(networkConstraint())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Sofort prüfen; mit [force] wird auch ein bereits gemeldeter Tag nochmal gemeldet (zum Testen). */
    fun checkNow(context: Context, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<WindAlarmWorker>()
            .setConstraints(networkConstraint())
            .setInputData(workDataOf("force" to force))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Wind-Alarm", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Meldet gleitbaren Wind an deinen Spots" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun text(day: DaySummary): String = dayLabel(day.date) + " " + windowText(day)

    /** z. B. „13–17 Uhr · bis 18 kn (Böen 24) · Segel 6,5–7,5 m²“ */
    fun windowText(day: DaySummary): String = buildString {
        append(day.firstGood).append("–").append((day.lastGood ?: 0) + 1).append(" Uhr")
        append(" · bis ").append(day.maxWindKn.de(0)).append(" kn")
        day.maxGustKn?.let { append(" (Böen ").append(it.de(0)).append(")") }
        val min = day.sailMin
        val max = day.sailMax
        if (min != null && max != null) {
            append(" · Segel ")
            append(if (min == max) min.de() else "${min.de()}–${max.de()}")
            append(" m²")
        }
    }

    fun notify(context: Context, spot: Spot, day: DaySummary) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SPOT, spot.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            spot.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = text(day)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🏄 Wind in ${spot.name}!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(spot.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Berechtigung inzwischen entzogen
        }
    }
}

class WindAlarmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Premium.isUnlocked(applicationContext)) return Result.success()
        val storage = Storage(applicationContext)
        val settings = storage.loadSpotSettings()
        val force = inputData.getBoolean("force", false)
        if (!settings.alarmEnabled && !force) return Result.success()

        val spots = storage.loadSpots().filter { it.alarm && it.covered }
        if (spots.isEmpty()) return Result.success()
        val quiver = storage.loadQuiver()

        return try {
            // Eine Anfrage für alle Alarm-Spots
            val forecasts = GeoSphere.forecast(spots.map { it.lat to it.lon })
            spots.zip(forecasts).forEach { (spot, hours) ->
                val day = Advisor.alarmDay(hours, settings, quiver) ?: return@forEach
                val sig = Advisor.signature(day)
                if (force || storage.lastAlarm(spot.id) != sig) {
                    WindAlarm.notify(applicationContext, spot, day)
                    storage.setLastAlarm(spot.id, sig)
                }
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}
