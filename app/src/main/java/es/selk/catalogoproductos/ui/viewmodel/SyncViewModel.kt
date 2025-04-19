package es.selk.catalogoproductos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity
import es.selk.catalogoproductos.data.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncViewModel(
    private val syncRepository: SyncRepository
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

    // Sincronizar datos
    fun syncData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _error.value = null

            try {
                val updateAvailable = syncRepository.checkUpdates()

                if (updateAvailable as Boolean) {
                    val success = syncRepository.sincronizarCambios()
                    if (success) {
                        justCompletedSync = true
                        loadLastUpdate()
                        _updateAvailable.value = false
                    } else {
                        _error.value = "No se pudo sincronizar los datos"
                    }
                } else {
                    _error.value = "No hay actualizaciones disponibles"
                }
            } catch (e: Exception) {
                _error.value = "Error de sincronización: ${e.message}"
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

    // Factory para crear el ViewModel con dependencias
    class Factory(
        private val syncRepository: SyncRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SyncViewModel::class.java)) {
                return SyncViewModel(syncRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}