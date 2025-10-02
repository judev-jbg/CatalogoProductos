package es.selk.catalogoproductos.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.selk.catalogoproductos.data.local.database.AppDatabase
import es.selk.catalogoproductos.data.repository.SyncRepository
import es.selk.catalogoproductos.utils.NetworkUtil
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = formatter.format(Calendar.getInstance().time)

        Log.d("SyncWorker", "========================================")
        Log.d("SyncWorker", "INICIANDO TRABAJO DE SINCRONIZACIÓN")
        Log.d("SyncWorker", "Hora actual: $currentTime")
        Log.d("SyncWorker", "Intento número: $runAttemptCount")
        Log.d("SyncWorker", "========================================")

        // Verificar horario laboral
        if (!isBusinessHours()) {
            Log.d("SyncWorker", "Fuera de horario laboral. Sincronización omitida pero trabajo exitoso.")
            Log.d("SyncWorker", "Próxima verificación en la siguiente ejecución periódica.")
            return Result.success()
        }

        // Verificar conectividad
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
            Log.d("SyncWorker", "Condiciones cumplidas. Iniciando sincronización...")

            // Verificar si es primera instalación
            val isFirstInstallation = syncRepository.isFirstInstallation()
            Log.d("SyncWorker", "¿Es primera instalación?: $isFirstInstallation")

            val success = if (isFirstInstallation) {
                Log.d("SyncWorker", "Ejecutando sincronización inicial completa")
                syncRepository.sincronizacionInicialCompleta()
            } else {
                Log.d("SyncWorker", "Verificando actualizaciones disponibles")
                val updateAvailable = syncRepository.checkUpdates()

                if (updateAvailable as Boolean) {
                    Log.d("SyncWorker", "Actualizaciones disponibles. Iniciando sincronización incremental")
                    syncRepository.sincronizarCambios()
                } else {
                    Log.d("SyncWorker", "No hay actualizaciones disponibles")
                    true
                }
            }

            val endTime = System.currentTimeMillis()
            val duration = (endTime - startTime) / 1000.0

            if (success) {
                Log.d("SyncWorker", "========================================")
                Log.d("SyncWorker", "SINCRONIZACIÓN COMPLETADA EXITOSAMENTE")
                Log.d("SyncWorker", "Duración: ${String.format("%.2f", duration)} segundos")
                Log.d("SyncWorker", "========================================")
                Result.success()
            } else {
                Log.d("SyncWorker", "========================================")
                Log.d("SyncWorker", "SINCRONIZACIÓN FALLÓ. Reintentando...")
                Log.d("SyncWorker", "========================================")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "========================================")
            Log.e("SyncWorker", "ERROR DURANTE LA SINCRONIZACIÓN", e)
            Log.e("SyncWorker", "========================================")
            Result.failure()
        }
    }

    /**
     * Verifica si estamos en horario laboral (8:00-17:00, L-V)
     * AMPLIADO: Ahora incluye hasta las 17:00 (5 PM)
     */
    private fun isBusinessHours(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // Verificar día laboral (lunes=2 a viernes=6)
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY

        if (!isWeekday) {
            Log.d("SyncWorker", "No es día laboral. Día: $dayOfWeek (${getDayName(dayOfWeek)})")
            return false
        }

        // Verificar horario (8:00 AM - 5:00 PM)
        val currentTimeInMinutes = hour * 60 + minute
        val startTime = 8 * 60       // 8:00 AM = 480 minutos
        val endTime = 17 * 60        // 5:00 PM = 1020 minutos

        val isInBusinessHours = currentTimeInMinutes in startTime until endTime

        if (!isInBusinessHours) {
            Log.d("SyncWorker", "Fuera de horario laboral (8:00-17:00)")
            Log.d("SyncWorker", "Hora actual: ${String.format("%02d:%02d", hour, minute)}")
        } else {
            Log.d("SyncWorker", "En horario laboral")
            Log.d("SyncWorker", "Hora actual: ${String.format("%02d:%02d", hour, minute)}")
        }

        return isInBusinessHours
    }

    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> "Domingo"
            Calendar.MONDAY -> "Lunes"
            Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"
            Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"
            Calendar.SATURDAY -> "Sábado"
            else -> "Desconocido"
        }
    }

    companion object {
        const val WORK_NAME = "sync_worker"
    }
}