package es.selk.catalogoproductos.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.repository.SyncRepository

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val syncRepository = SyncRepository(
            database.productoDao(),
            database.historialPrecioDao(),
            database.historialStockDao(),
            database.ultimaActualizacionDao()
        )

        return try {
            val updateAvailable = syncRepository.checkUpdates()

            if (updateAvailable) {
                val success = syncRepository.sincronizarCambios()
                if (success) Result.success() else Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sync_worker"
    }
}