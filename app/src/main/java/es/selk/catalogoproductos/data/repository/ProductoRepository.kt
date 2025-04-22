package es.selk.catalogoproductos.data.repository

import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow

class ProductoRepository(
    private val productoDao: ProductoDao,
) {
    // Búsqueda de productos
    fun searchProductos(query: String): Flow<List<ProductoEntity>> {
        // Si la consulta parece un ID de producto (contiene doble espacio)
        val normalizedQuery = query.trim().replace("\\s+".toRegex(), " ")

        return if (normalizedQuery.contains("  ")) {
            productoDao.searchProductos(normalizedQuery)
        } else {
            // Usar FTS para búsqueda por descripción
            try {
                // Escape special FTS characters to prevent syntax errors
                val safeQuery = normalizedQuery.replace("[\"*]".toRegex(), " ")
                productoDao.searchProductosFTS(safeQuery + "*")  // Add wildcard for partial matches
            } catch (e: Exception) {
                // Fallback to regular search if FTS fails
                productoDao.searchProductos(normalizedQuery)
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