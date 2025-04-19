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

    // Estado de todos los productos
    private val _allProducts = MutableStateFlow<List<ProductoEntity>>(emptyList())
    val allProducts: StateFlow<List<ProductoEntity>> = _allProducts

    suspend fun getProductCount(): Int {
        return productoRepository.getProductCount()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Clear search to show all products
                _searchQuery.value = ""

                // Load fresh products from database
                productoRepository.getAllProductos()
                    .catch {
                        _error.value = it.message
                        emit(emptyList())
                    }
                    .collect { productos ->
                        _allProducts.value = productos
                        _searchResults.value = productos
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = "Error refreshing products: ${e.message}"
                _isLoading.value = false
            }
        }
    }


    init {
        // Configurar flujo reactivo para búsqueda en tiempo real
        viewModelScope.launch {
            searchQuery
                .debounce(300) // Esperar 300ms para evitar múltiples consultas
                .distinctUntilChanged() // Solo procesar si cambia la consulta
                .flatMapLatest { query ->
                    _isLoading.value = true
                    if (query.isBlank()) {
                        productoRepository.getAllProductos()
                            .catch {
                                _error.value = it.message
                                emit(emptyList())
                            }
                    }else if (query.length >= 6) {
                        // Search with at least 6 characters
                        productoRepository.searchProductos(query)
                            .catch {
                                _error.value = it.message
                                emit(emptyList())
                            }
                    } else {
                        // For very short queries, show all products
                        productoRepository.getAllProductos()
                            .catch {
                                _error.value = it.message
                                emit(emptyList())
                            }
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                    _isLoading.value = false
                }
        }
        viewModelScope.launch {
            _isLoading.value = true
            productoRepository.getAllProductos()
                .catch {
                    _error.value = it.message
                    emit(emptyList())
                }
                .collect { productos ->
                    _allProducts.value = productos

                    // Si no hay búsqueda activa, muestra todos los productos
                    if (_searchQuery.value.isBlank()) {
                        _searchResults.value = productos
                    }
                    _isLoading.value = false
                }
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
}