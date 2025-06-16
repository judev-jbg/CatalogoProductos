package es.selk.catalogoproductos

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import es.selk.catalogoproductos.sync.SyncWorker
import java.util.concurrent.TimeUnit
import androidx.work.*
import java.util.*
import androidx.core.content.edit
import es.selk.catalogoproductos.utils.NetworkUtil
import kotlinx.coroutines.flow.MutableStateFlow

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Verificar conexión antes de programar sincronización
        if (!NetworkUtil.isNetworkAvailable(this)) {
            Log.d("MainApplication", "No hay conexión a internet. No se programará sincronización inicial.")
            return
        }

        // Check if this is the first run
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (isFirstRun) {
            Log.d("MainApplication", "Primera ejecución detectada. Programando sincronización inicial.")
            // First run - schedule immediate sync
            setupImmediateSync()

            // Save that it's no longer first run
            prefs.edit() { putBoolean("is_first_run", false) }
        } else {
            Log.d("MainApplication", "Ejecución normal. Programando sincronización diaria.")
            // Normal run - schedule daily sync only
            setupDailySync()
        }
    }

    private fun setupImmediateSync() {
        // Verificar conectividad antes de programar
        if (!NetworkUtil.isNetworkAvailable(this)) {
            Log.d("MainApplication", "No hay conexión. Cancelando sincronización inmediata.")
            isInitialSyncRunning.value = false
            return
        }

        isInitialSyncRunning.value = true
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create one-time work request for immediate sync
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        // Enqueue the work
        WorkManager.getInstance(this).enqueueUniqueWork(
            "initial_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        Log.d("MainApplication", "Sincronización inmediata programada.")

        // Also setup daily sync
        setupDailySync()
    }

    private fun setupDailySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calculate time until 5:00 AM
        val calendar = Calendar.getInstance()
        val currentTimeMillis = calendar.timeInMillis

        // Configure for 5:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 5)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        // If already past 5:00 AM, schedule for tomorrow
        if (calendar.timeInMillis <= currentTimeMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Calculate initial delay
        val initialDelay = calendar.timeInMillis - currentTimeMillis

        // Create the periodic work request
        val dailySyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            24, TimeUnit.HOURS, // Repeat every 24 hours
            15, TimeUnit.MINUTES // Flex period of 15 minutes
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        // Enqueue the periodic work
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            dailySyncRequest
        )

        Log.d("MainApplication", "Sincronización diaria programada para las 5:00 AM.")
    }

    companion object {
        // Para compartir el estado de sincronización inicial
        val isInitialSyncRunning = MutableStateFlow(false)
    }
}