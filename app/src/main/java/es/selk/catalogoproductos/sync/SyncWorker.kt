package es.selk.catalogoproductos.sync

import android.content.Context
import android.util.Log
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
            Log.d("SyncWorker", "Iniciando trabajo de sincronización")
            val updateAvailable = syncRepository.checkUpdates()

            if (updateAvailable as Boolean) {
                Log.d("SyncWorker", "Hay una actualización disponible, iniciando sincro")
                val success = syncRepository.sincronizarCambios()
                if (success) {
                    Log.d("SyncWorker", "Sincronización completada exitosamente")
                    Result.success()
                } else {
                    Log.d("SyncWorker", "Sincronización falló, reintentando")
                    Result.retry()
                }
            } else {
                Log.d("SyncWorker", "No hay actualizaciones disponibles")
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