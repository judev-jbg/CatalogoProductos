package es.selk.catalogoproductos

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkManager
import es.selk.catalogoproductos.sync.SyncWorker
import java.util.concurrent.TimeUnit
import androidx.work.*
import java.util.*

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        setupWorker()
    }

    private fun setupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calcular el tiempo hasta las 5:00 AM
        val calendar = Calendar.getInstance()
        val currentTimeMillis = calendar.timeInMillis

        // Configurar para las 5:00 AM
        calendar.set(Calendar.HOUR_OF_DAY, 5)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        // Si ya pasó las 5:00 AM, programarlo para mañana
        if (calendar.timeInMillis <= currentTimeMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Calcular el retraso inicial
        val initialDelay = calendar.timeInMillis - currentTimeMillis

        // Crear el trabajo programado para una sola vez
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        // Encolar el trabajo
        WorkManager.getInstance(this).enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        // Opcional: configurar un receptor para programar el próximo trabajo
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(syncRequest.id)
            .observeForever { workInfo ->
                if (workInfo.state.isFinished) {
                    // Programar el trabajo para el día siguiente
                    setupWorker()
                }
            }
    }
}