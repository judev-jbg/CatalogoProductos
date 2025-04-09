package es.selk.catalogoproductos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.repository.ProductoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class ProductoViewModel(
    private val productoRepository: ProductoRepository
) : ViewModel() {
    // Estado de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Estado de resultados
    private val _searchResults = MutableStateFlow<List<ProductoEntity>>(emptyList())
    val searchResults: StateFlow<List<ProductoEntity>> = _searchResults

    // Estado de carga
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Estado de error
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Configurar flujo reactivo para búsqueda en tiempo real
        viewModelScope.launch {
            searchQuery
                .debounce(300) // Esperar 300ms para evitar múltiples consultas
                .filter { it.length >= 3 } // Mínimo 3 caracteres
                .distinctUntilChanged() // Solo procesar si cambia la consulta
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        _isLoading.value = true
                        productoRepository.searchProductos(query)
                            .catch {
                                _error.value = it.message
                                _isLoading.value = false
                                emit(emptyList())
                            }
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                    _isLoading.value = false
                }
        }
    }

    // Actualizar consulta de búsqueda
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Factory para crear el ViewModel con dependencias
    class Factory(
        private val productoRepository: ProductoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProductoViewModel::class.java)) {
                return ProductoViewModel(productoRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}