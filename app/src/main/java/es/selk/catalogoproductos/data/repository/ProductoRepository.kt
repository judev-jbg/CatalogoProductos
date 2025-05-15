package es.selk.catalogoproductos.data.repository

import android.util.Log
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

class ProductoRepository(
    private val productoDao: ProductoDao,
) {
    // Búsqueda de productos
    fun searchProductos(query: String): Flow<List<ProductoEntity>> {
        val trimmedQuery = query.replaceFirst("^\\s+".toRegex(), "")
        val isReferenciaSearch = trimmedQuery.contains("  ")
        Log.d("ProductoRepository", "Búsqueda: '$trimmedQuery',es búsqueda por referencia: $isReferenciaSearch")

        // Si la consulta parece un ID de producto (contiene doble espacio)
        return if (isReferenciaSearch) {
            productoDao.searchProductosByReferencia(trimmedQuery)
        } else {
            // Usar FTS para búsqueda por descripción
            try {
                // Escape special FTS characters to prevent syntax errors
                Log.d("ProductoRepository", "Ejecutando búsqueda por FTS")
                val safeQuery = trimmedQuery.replace("[\"*]".toRegex(), " ")
                productoDao.searchProductosFTS(safeQuery + "*")  // Add wildcard for partial matches
            } catch (e: Exception) {
                // Fallback to regular search if FTS fails
                productoDao.searchProductosByDescripcionOrFamilia(trimmedQuery)
            }
        }
    }

    suspend fun getProductCount(): Int {
        return productoDao.getProductCount()
    }

    fun getAllProductos(): Flow<List<ProductoEntity>> {
        return productoDao.getAllProductosFlow()
    }
}