package es.selk.catalogoproductos.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.utils.NetworkUtil

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Iniciando trabajo de sincronización")

        // Verificar conectividad antes de intentar sincronizar
        if (!NetworkUtil.isNetworkAvailable(applicationContext)) {
            Log.d("SyncWorker", "No hay conexión a internet. Cancelando sincronización.")
            return Result.failure()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val syncRepository = SyncRepository(
            database.productoDao(),
            database.ultimaActualizacionDao()
        )

        return try {
            Log.d("SyncWorker", "Conexión disponible, determinando tipo de sincronización")

            // Verificar si es primera instalación
            val isFirstInstallation = syncRepository.isFirstInstallation()
            Log.d("SyncWorker", "¿Es primera instalación?: $isFirstInstallation")

            val success = if (isFirstInstallation) {
                // Primera instalación: descarga completa
                Log.d("SyncWorker", "Ejecutando sincronización inicial completa desde WorkManager")
                syncRepository.sincronizacionInicialCompleta()
            } else {
                // Instalación existente: verificar actualizaciones
                Log.d("SyncWorker", "Verificando actualizaciones para sincronización incremental")
                val updateAvailable = syncRepository.checkUpdates()

                if (updateAvailable as Boolean) {
                    Log.d("SyncWorker", "Hay una actualización disponible, iniciando sincronización incremental")
                    syncRepository.sincronizarCambios()
                } else {
                    Log.d("SyncWorker", "No hay actualizaciones disponibles")
                    true // Considerar exitoso aunque no haya actualizaciones
                }
            }

            if (success) {
                Log.d("SyncWorker", "Sincronización completada exitosamente")
                Result.success()
            } else {
                Log.d("SyncWorker", "Sincronización falló, reintentando")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error durante la sincronización", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sync_worker"
    }
}