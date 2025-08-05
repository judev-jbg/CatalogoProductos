package es.selk.catalogoproductos.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.utils.NetworkUtil
import java.util.Calendar

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Iniciando trabajo de sincronización")

        // Verificar si estamos en horario laboral
        if (!isBusinessHours()) {
            Log.d("SyncWorker", "Fuera de horario laboral. Saltando sincronización.")
            return Result.success()
        }

        // Verificar conectividad antes de intentar sincronizar
        if (!NetworkUtil.isNetworkAvailable(applicationContext)) {
            Log.d("SyncWorker", "No hay conexión a internet. Reintentando más tarde.")
            return Result.retry()
        }

        val database = AppDatabase.getInstance(applicationContext)
        val syncRepository = SyncRepository(
            database.productoDao(),
            database.ultimaActualizacionDao()
        )

        return try {
            Log.d("SyncWorker", "En horario laboral y con conexión. Iniciando sincronización.")

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
                    Log.d("SyncWorker", "Actualizaciones disponibles, iniciando sincronización incremental")
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

    /**
     * Verifica si estamos en horario laboral (8:45-16:45, L-V)
     */
    private fun isBusinessHours(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // Verificar día laboral (lunes=2 a viernes=6)
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY

        if (!isWeekday) {
            Log.d("SyncWorker", "No es día laboral. Día: $dayOfWeek")
            return false
        }

        // Verificar horario (8:45 AM - 4:45 PM)
        val currentTimeInMinutes = hour * 60 + minute
        val startTime = 8 * 60 + 45  // 8:45 AM = 525 minutos
        val endTime = 16 * 60 + 45   // 4:45 PM = 1005 minutos

        val isInBusinessHours = currentTimeInMinutes in startTime..endTime

        if (!isInBusinessHours) {
            Log.d("SyncWorker", "Fuera de horario laboral. Hora actual: ${hour}:${String.format("%02d", minute)}")
        } else {
            Log.d("SyncWorker", "En horario laboral. Hora actual: ${hour}:${String.format("%02d", minute)}")
        }

        return isInBusinessHours
    }

    companion object {
        const val WORK_NAME = "sync_worker"
    }
}