package es.selk.catalogoproductos.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import es.selk.catalogoproductos.MainApplication
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity
import es.selk.catalogoproductos.data.repository.SyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val context: Context
) : ViewModel() {
    var justCompletedSync = false

    // Estado de última actualización
    private val _ultimaActualizacion = MutableStateFlow<UltimaActualizacionEntity?>(null)
    val ultimaActualizacion: StateFlow<UltimaActualizacionEntity?> = _ultimaActualizacion

    // Estado fecha formateada
    private val _fechaUltimaActualizacion = MutableStateFlow("")
    val fechaUltimaActualizacion: StateFlow<String> = _fechaUltimaActualizacion

    // Estado de sincronización
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    // Estado de actualización disponible
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    // Estado de error
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadLastUpdate()
        checkForUpdates()
    }

    // Cargar última actualización
    private fun loadLastUpdate() {
        viewModelScope.launch {
            syncRepository.getUltimaActualizacion()
                .catch { _error.value = it.message }
                .collect {
                    _ultimaActualizacion.value = it
                    formatDate(it?.timestamp)
                }
        }
    }

    // Verificar actualizaciones
    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                _updateAvailable.value = syncRepository.checkUpdates() as Boolean
            } catch (e: Exception) {
                _error.value = "Error al verificar version de actualizaciones: ${e.message}"
            }
        }
    }

    // Sincronizar datos (automáticamente detecta si es inicial o incremental)
    fun syncData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _error.value = null

            try {
                // Verificar si es primera instalación
                val isFirstInstallation = syncRepository.isFirstInstallation()
                android.util.Log.d("SyncViewModel", "¿Es primera instalación?: $isFirstInstallation")

                val success = if (isFirstInstallation) {
                    // Primera instalación: descarga completa
                    android.util.Log.d("SyncViewModel", "Ejecutando sincronización inicial completa")
                    syncRepository.sincronizacionInicialCompleta()
                } else {
                    // Instalación existente: verificar actualizaciones y descargar solo cambios
                    android.util.Log.d("SyncViewModel", "Verificando actualizaciones para sincronización incremental")
                    val updateAvailable = syncRepository.checkUpdates()

                    if (updateAvailable as Boolean) {
                        android.util.Log.d("SyncViewModel", "Ejecutando sincronización incremental")
                        syncRepository.sincronizarCambios()
                    } else {
                        android.util.Log.d("SyncViewModel", "No hay actualizaciones disponibles")
                        _error.value = "No hay actualizaciones disponibles"
                        false
                    }
                }

                if (success) {
                    justCompletedSync = true
                    loadLastUpdate()
                    _updateAvailable.value = false
                    android.util.Log.d("SyncViewModel", "Sincronización completada exitosamente")
                } else {
                    _error.value = "No se pudo sincronizar los datos"
                    android.util.Log.e("SyncViewModel", "Error en la sincronización")
                }
            } catch (e: Exception) {
                _error.value = "Error de sincronización: ${e.message}"
                android.util.Log.e("SyncViewModel", "Error de sincronización", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Método específico para sincronización inicial (puede ser llamado desde MainActivity)
    fun syncInitialData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _error.value = null

            try {
                android.util.Log.d("SyncViewModel", "Forzando sincronización inicial completa")
                val success = syncRepository.sincronizacionInicialCompleta()

                if (success) {
                    justCompletedSync = true
                    loadLastUpdate()
                    _updateAvailable.value = false
                    android.util.Log.d("SyncViewModel", "Sincronización inicial forzada completada exitosamente")
                } else {
                    _error.value = "No se pudo completar la sincronización inicial"
                    android.util.Log.e("SyncViewModel", "Error en la sincronización inicial forzada")
                }
            } catch (e: Exception) {
                _error.value = "Error de sincronización inicial: ${e.message}"
                android.util.Log.e("SyncViewModel", "Error de sincronización inicial", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // Formatear fecha de última actualización
    private fun formatDate(timestamp: Long?) {
        if (timestamp == null) {
            _fechaUltimaActualizacion.value = "Sin actualizar"
            return
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))
        _fechaUltimaActualizacion.value = dateFormat.format(Date(timestamp))
    }

    // Agregar función para verificar estado de sincronización inicial
    @SuppressLint("RestrictedApi")
    fun checkInitialSyncStatus() {
        viewModelScope.launch {
            // Esperar 2 segundos para permitir que WorkManager inicie
            delay(2000)

            // Si se está ejecutando syncWorker

            val workManager = WorkManager.getInstance(context)
            val workInfoList = workManager.getWorkInfosForUniqueWork("initial_sync").await()

            val isRunning = workInfoList.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }

            if (isRunning) {
                _isSyncing.value = true

                // Esperar a que termine
                val workInfo = workInfoList.first()
                workManager.getWorkInfoByIdLiveData(workInfo.id).asFlow().collect { info ->
                    if (info.state.isFinished) {
                        _isSyncing.value = false
                        MainApplication.isInitialSyncRunning.value = false
                        justCompletedSync = true
                        refreshData()
                    }
                }
            } else {
                MainApplication.isInitialSyncRunning.value = false
            }
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            loadLastUpdate()
        }
    }

    // Factory para crear el ViewModel con dependencias
    class Factory(
        private val syncRepository: SyncRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
                return SyncViewModel(syncRepository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}