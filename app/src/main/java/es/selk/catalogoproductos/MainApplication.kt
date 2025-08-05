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
            Log.d("MainApplication", "Ejecución normal. Programando sincronización horaria.")
        }

        // Siempre programar sincronización horaria (para días laborales)
        setupHourlySync()
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
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "initial_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        Log.d("MainApplication", "Sincronización inmediata programada.")
    }

    private fun setupHourlySync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calcular delay inicial hasta la próxima ventana de sincronización
        val initialDelay = calculateInitialDelayForBusinessHours()

        Log.d("MainApplication", "Delay inicial calculado: ${initialDelay / (1000 * 60)} minutos")

        // Crear trabajo periódico cada hora
        val hourlySyncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            1, TimeUnit.HOURS, // Repetir cada hora
            15, TimeUnit.MINUTES // Flex period de 15 minutos
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        // Programar el trabajo periódico
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            hourlySyncRequest
        )

        Log.d("MainApplication", "Sincronización horaria programada (8:45-16:45, L-V).")
    }

    private fun calculateInitialDelayForBusinessHours(): Long {
        val calendar = Calendar.getInstance()
        val currentTime = calendar.timeInMillis

        // Encontrar la próxima ventana de sincronización (8:45 AM - 4:45 PM, L-V)
        while (true) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            // Verificar si es día laboral (lunes=2 a viernes=6)
            val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY

            if (isWeekday) {
                // Si estamos en día laboral, verificar la hora
                when {
                    // Antes de las 8:45 AM - programar para 8:45 AM hoy
                    hour < 8 || (hour == 8 && minute < 45) -> {
                        calendar.set(Calendar.HOUR_OF_DAY, 8)
                        calendar.set(Calendar.MINUTE, 45)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        break
                    }
                    // Entre 8:45 AM y 4:45 PM - programar para la próxima hora :45
                    (hour in (8..15)) && !(hour == 8 && minute < 45) && !(hour == 16 && minute >= 45) -> {
                        val nextHour = if (hour == 8 && minute < 45) 8 else hour + 1
                        if (nextHour <= 16) {
                            calendar.set(Calendar.HOUR_OF_DAY, nextHour)
                            calendar.set(Calendar.MINUTE, 45)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            break
                        } else {
                            // Si sería después de las 4:45 PM, ir al próximo día laboral
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                            calendar.set(Calendar.HOUR_OF_DAY, 8)
                            calendar.set(Calendar.MINUTE, 45)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                        }
                    }
                    // Después de las 4:45 PM - programar para mañana 8:45 AM
                    else -> {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 8)
                        calendar.set(Calendar.MINUTE, 45)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                    }
                }
            } else {
                // Si es fin de semana, avanzar al próximo lunes
                val daysUntilMonday = (Calendar.MONDAY - dayOfWeek + 7) % 7
                if (daysUntilMonday == 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, 7) // Si es domingo, ir al próximo lunes
                } else {
                    calendar.add(Calendar.DAY_OF_YEAR, daysUntilMonday)
                }
                calendar.set(Calendar.HOUR_OF_DAY, 8)
                calendar.set(Calendar.MINUTE, 45)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                break
            }
        }

        val delay = calendar.timeInMillis - currentTime
        val nextSyncTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(calendar.time)
        Log.d("MainApplication", "Próxima sincronización programada para: $nextSyncTime")

        return delay
    }

    companion object {
        // Para compartir el estado de sincronización inicial
        val isInitialSyncRunning = MutableStateFlow(false)
    }
}