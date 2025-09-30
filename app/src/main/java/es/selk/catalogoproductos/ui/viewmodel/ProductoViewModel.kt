package es.selk.catalogoproductos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.repository.ProductoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class StockFilter {
    ALL,
    IN_STOCK,
    NO_STOCK
}

class ProductoViewModel(
    private val productoRepository: ProductoRepository
) : ViewModel() {

    companion object {
        private const val MAX_RESULTS = 100
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _stockFilter = MutableStateFlow(StockFilter.ALL)
    val stockFilter: StateFlow<StockFilter> = _stockFilter

    private val _searchResults = MutableStateFlow<List<ProductoEntity>>(emptyList())
    val searchResults: StateFlow<List<ProductoEntity>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _allProducts = MutableStateFlow<List<ProductoEntity>>(emptyList())
    val allProducts: StateFlow<List<ProductoEntity>> = _allProducts

    // Job para cancelar búsquedas anteriores
    private var searchJob: Job? = null

    suspend fun getProductCount(): Int {
        return productoRepository.getProductCount()
    }

    fun setSearchQuery(query: String) {
        Log.d("ProductoViewModel", "setSearchQuery llamado con: '$query'")
        _searchQuery.value = query
    }

    fun setStockFilter(filter: StockFilter) {
        if (_stockFilter.value != filter) {
            Log.d("ProductoViewModel", "Cambiando filtro de stock de ${_stockFilter.value} a: $filter")
            _stockFilter.value = filter
            performSearch(_searchQuery.value, filter)
        }
    }

    fun resetStockFilter() {
        Log.d("ProductoViewModel", "Reseteando filtro de stock a ALL")
        setStockFilter(StockFilter.ALL)
    }

    private fun performSearch(query: String, stockFilter: StockFilter) {
        // Cancelar búsqueda anterior si existe
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            try {
                Log.d("ProductoViewModel", "performSearch INICIADA - Query: '$query', Filtro: $stockFilter")

                _isLoading.value = true
                _error.value = null

                if (query.isEmpty()) {
                    // Si no hay búsqueda, mostrar todos los productos
                    productoRepository.getAllProductos()
                        .catch { e ->
                            Log.e("ProductoViewModel", "Error cargando todos los productos", e)
                            _error.value = e.message
                            _isLoading.value = false
                            emit(emptyList())
                        }
                        .collect { productos ->
                            val filtered = applyStockFilterAndLimit(productos, stockFilter)
                            Log.d("ProductoViewModel", "Todos los productos - Total BD: ${productos.size}, Después de filtro: ${filtered.size}")

                            _isLoading.value = false
                            _searchResults.value = filtered
                            Log.d("ProductoViewModel", "performSearch COMPLETADA - Query: '$query'")
                        }
                } else {
                    // Ejecutar búsqueda
                    productoRepository.searchProductos(query)
                        .catch { e ->
                            Log.e("ProductoViewModel", "Error en búsqueda", e)
                            _error.value = e.message
                            _isLoading.value = false
                            emit(emptyList())
                        }
                        .collect { productos ->
                            val filtered = applyStockFilterAndLimit(productos, stockFilter)
                            Log.d("ProductoViewModel", "Búsqueda: '$query' - Total BD: ${productos.size}, Después de filtro '$stockFilter': ${filtered.size}")

                            _isLoading.value = false
                            _searchResults.value = filtered
                            Log.d("ProductoViewModel", "performSearch COMPLETADA - Query: '$query', Resultados finales: ${filtered.size}")
                        }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("ProductoViewModel", "Error en performSearch", e)
                    _error.value = e.message
                    _isLoading.value = false
                } else {
                    Log.d("ProductoViewModel", "performSearch CANCELADA - Query: '$query'")
                }
            }
        }
    }

    private fun applyStockFilterAndLimit(productos: List<ProductoEntity>, filter: StockFilter): List<ProductoEntity> {
        val filtered = when (filter) {
            StockFilter.ALL -> productos
            StockFilter.IN_STOCK -> {
                val result = productos.filter { it.stock_actual > 0 }
                Log.d("ProductoViewModel", "Filtro IN_STOCK aplicado: ${productos.size} -> ${result.size} productos")
                result
            }
            StockFilter.NO_STOCK -> {
                val result = productos.filter { it.stock_actual <= 0 }
                Log.d("ProductoViewModel", "Filtro NO_STOCK aplicado: ${productos.size} -> ${result.size} productos")
                result
            }
        }

        val limited = filtered.take(MAX_RESULTS)

        if (filtered.size > MAX_RESULTS) {
            Log.d("ProductoViewModel", "Límite aplicado: ${filtered.size} -> $MAX_RESULTS productos mostrados")
        }

        Log.d("ProductoViewModel", "applyStockFilterAndLimit - Filtro: $filter, Entrada: ${productos.size}, Después filtro: ${filtered.size}, Final: ${limited.size}")
        return limited
    }

    fun refreshProducts() {
        viewModelScope.launch {
            try {
                Log.d("ProductoViewModel", "Refrescando productos")

                _isLoading.value = true

                productoRepository.getAllProductos()
                    .catch { e ->
                        Log.e("ProductoViewModel", "Error refrescando productos", e)
                        _error.value = e.message
                        _isLoading.value = false
                        emit(emptyList())
                    }
                    .collect { productos ->
                        Log.d("ProductoViewModel", "Productos refrescados: ${productos.size}")
                        _allProducts.value = productos

                        performSearch(_searchQuery.value, _stockFilter.value)
                    }
            } catch (e: Exception) {
                Log.e("ProductoViewModel", "Error general refrescando", e)
                _error.value = "Error refrescando productos: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    init {
        // Observar cambios en el query de búsqueda
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    Log.d("ProductoViewModel", "Query cambió (después de debounce) a: '$query'")
                    performSearch(query, _stockFilter.value)
                }
        }

        // Cargar productos iniciales
        viewModelScope.launch {
            try {
                Log.d("ProductoViewModel", "Carga inicial de productos")
                _isLoading.value = true

                productoRepository.getAllProductos()
                    .catch { e ->
                        Log.e("ProductoViewModel", "Error en carga inicial", e)
                        _error.value = e.message
                        _isLoading.value = false
                        emit(emptyList())
                    }
                    .collect { productos ->
                        Log.d("ProductoViewModel", "Carga inicial completada: ${productos.size} productos")
                        _allProducts.value = productos

                        if (_searchQuery.value.isEmpty()) {
                            val filtered = applyStockFilterAndLimit(productos, _stockFilter.value)
                            Log.d("ProductoViewModel", "Aplicando filtro inicial, resultados: ${filtered.size}")

                            _isLoading.value = false
                            _searchResults.value = filtered
                        } else {
                            _isLoading.value = false
                        }
                    }
            } catch (e: Exception) {
                Log.e("ProductoViewModel", "Error fatal en init", e)
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

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