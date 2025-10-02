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
import androidx.core.content.edit
import es.selk.catalogoproductos.utils.NetworkUtil
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

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
            setupImmediateSync()
            prefs.edit() { putBoolean("is_first_run", false) }
        } else {
            Log.d("MainApplication", "Ejecución normal. Verificando estado de sincronización.")
        }

        // Siempre programar sincronización periódica
        setupPeriodicSync()

        // Log del estado actual
        logWorkManagerStatus()
    }

    private fun setupImmediateSync() {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            Log.d("MainApplication", "No hay conexión. Cancelando sincronización inmediata.")
            isInitialSyncRunning.value = false
            return
        }

        isInitialSyncRunning.value = true
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "initial_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        Log.d("MainApplication", "Sincronización inmediata programada.")
    }

    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // CAMBIO IMPORTANTE: Reducir el intervalo de repetición para asegurar ejecución
        // Usar 1 hora como mínimo (es el mínimo permitido por PeriodicWork)
        val periodicSyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            1, TimeUnit.HOURS, // Repetir cada hora
            15, TimeUnit.MINUTES // Ventana de flexibilidad
        )
            .setConstraints(constraints)
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .addTag(SYNC_WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Programar el trabajo periódico
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // KEEP en lugar de REPLACE
            periodicSyncRequest
        )

        Log.d("MainApplication", "Sincronización periódica programada (cada hora)")
    }

    /**
     * Calcula el delay inicial hasta la próxima ejecución
     * SIMPLIFICADO: Solo esperar hasta la próxima hora en punto
     */
    private fun calculateInitialDelay(): Long {
        val calendar = Calendar.getInstance()
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentSecond = calendar.get(Calendar.SECOND)

        // Calcular minutos hasta la próxima hora
        val minutesUntilNextHour = 60 - currentMinute
        val secondsUntilNextHour = 60 - currentSecond

        val delayMillis = ((minutesUntilNextHour * 60 + secondsUntilNextHour) * 1000).toLong()

        val nextSyncTime = Calendar.getInstance().apply {
            add(Calendar.MILLISECOND, delayMillis.toInt())
        }

        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        Log.d("MainApplication", "Próxima sincronización automática: ${formatter.format(nextSyncTime.time)}")
        Log.d("MainApplication", "Delay inicial: ${delayMillis / 1000 / 60} minutos")

        return delayMillis
    }

    private fun logWorkManagerStatus() {
        val workManager = WorkManager.getInstance(this)

        // Obtener información de trabajos programados
        val workInfosFuture = workManager.getWorkInfosForUniqueWork(SyncWorker.WORK_NAME)

        try {
            val workInfos = workInfosFuture.get()
            Log.d("MainApplication", "=== Estado de WorkManager ===")
            Log.d("MainApplication", "Trabajos encontrados: ${workInfos.size}")

            workInfos.forEachIndexed { index, workInfo ->
                Log.d("MainApplication", "Trabajo $index:")
                Log.d("MainApplication", "  - ID: ${workInfo.id}")
                Log.d("MainApplication", "  - Estado: ${workInfo.state}")
                Log.d("MainApplication", "  - Intento: ${workInfo.runAttemptCount}")
                Log.d("MainApplication", "  - Tags: ${workInfo.tags}")
            }
            Log.d("MainApplication", "============================")
        } catch (e: Exception) {
            Log.e("MainApplication", "Error obteniendo estado de WorkManager", e)
        }
    }

    companion object {
        val isInitialSyncRunning = MutableStateFlow(false)
        const val SYNC_WORK_TAG = "sync_work_tag"
    }
}