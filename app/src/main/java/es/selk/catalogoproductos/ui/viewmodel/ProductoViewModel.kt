package es.selk.catalogoproductos.ui.viewmodel

import android.util.Log
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

// Enum para representar los filtros de stock
enum class StockFilter {
    ALL,       // Todos los productos
    IN_STOCK,  // Productos con stock > 0
    NO_STOCK   // Productos sin stock
}

class ProductoViewModel(
    private val productoRepository: ProductoRepository
) : ViewModel() {
    // Estado de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Estado de filtro de stock
    private val _stockFilter = MutableStateFlow(StockFilter.ALL)
    val stockFilter: StateFlow<StockFilter> = _stockFilter

    // Estado de resultados no filtrados (después de la búsqueda pero antes del filtro de stock)
    private val _unfilteredResults = MutableStateFlow<List<ProductoEntity>>(emptyList())

    // Estado de resultados filtrados (después de aplicar filtro de stock)
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

        // Para búsquedas inmediatas - esto ayuda a evitar retrasos
        if (query.isBlank()) {
            viewModelScope.launch {
                _unfilteredResults.value = _allProducts.value
                applyStockFilter()
            }
        }
    }

    // Función para establecer el filtro de stock
    fun setStockFilter(filter: StockFilter) {
        if (_stockFilter.value != filter) {
            _stockFilter.value = filter
            applyStockFilter()
        }
    }

    // Función para resetear el filtro a 'Todos'
    fun resetStockFilter() {
        Log.d("ProductoViewModel", "Reseteando filtro de stock a TODOS")
        _stockFilter.value = StockFilter.ALL
        viewModelScope.launch {
            applyStockFilter()
            Log.d("ProductoViewModel", "Filtro reseteado y aplicado. Mostrando ${_searchResults.value.size} productos")
        }
    }

    // Aplicar el filtro de stock a los resultados no filtrados
    private fun applyStockFilter() {
        val currentFilter = _stockFilter.value
        val resultsBeforeFilter = _unfilteredResults.value
        Log.d("ProductoViewModel", "Aplicando filtro: $currentFilter sobre ${resultsBeforeFilter.size} productos")
        val filteredList = when (currentFilter) {
            StockFilter.ALL -> resultsBeforeFilter
            StockFilter.IN_STOCK -> resultsBeforeFilter .filter { it.stock_actual > 0 }
            StockFilter.NO_STOCK -> resultsBeforeFilter .filter { it.stock_actual <= 0 }
        }
        Log.d("ProductoViewModel", "Resultados después del filtro: ${filteredList.size} productos")
        _searchResults.value = filteredList
    }

    fun refreshProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("ProductoViewModel", "Iniciando refrescado de productos")

                // Forzar recolección de productos más recientes
                productoRepository.getAllProductos()
                    .catch { e ->
                        Log.e("ProductoViewModel", "Error refrescando productos", e)
                        _error.value = e.message
                        emit(emptyList())
                    }
                    .collect { productos ->
                        Log.d("ProductoViewModel", "Productos refrescados: ${productos.size}")
                        _allProducts.value = productos
                        _unfilteredResults.value = productos
                        applyStockFilter()
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e("ProductoViewModel", "Error general refrescando productos", e)
                _error.value = "Error refrescando productos: ${e.message}"
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
                    when {
                        query.isBlank() -> {
                            productoRepository.getAllProductos()
                                .catch {
                                    _error.value = it.message
                                    emit(emptyList())
                                }
                        }
                        query.length >= 6 -> {
                            // Solo ejecuta búsqueda con 6+ caracteres
                            productoRepository.searchProductos(query)
                                .catch {
                                    _error.value = it.message
                                    emit(emptyList())
                                }
                        }
                        else -> {
                            // Para consultas cortas, no emitir nuevos valores
                            // (mantiene los resultados actuales)
                            flow { emit(_unfilteredResults.value ?: emptyList()) }
                        }
                    }
                }
                .collect { results ->
                    _unfilteredResults.value = results
                    applyStockFilter()
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
                        _unfilteredResults.value = productos
                        applyStockFilter()
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