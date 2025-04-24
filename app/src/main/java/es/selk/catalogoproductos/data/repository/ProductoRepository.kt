package es.selk.catalogoproductos.data.repository

import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

class ProductoRepository(
    private val productoDao: ProductoDao,
) {
    // Búsqueda de productos
    fun searchProductos(query: String): Flow<List<ProductoEntity>> {
        // Normalizar la consulta removiendo acentos
        val normalizedQuery = normalizeString(query.trim().replace("\\s+".toRegex(), " "))

        // Si la consulta parece un ID de producto (contiene doble espacio)
        return if (query.contains("  ")) {
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

    // Función para normalizar cadenas eliminando acentos
    private fun normalizeString(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace("[^\\p{ASCII}]".toRegex(), "")
    }

    suspend fun getProductCount(): Int {
        return productoDao.getProductCount()
    }

    fun getAllProductos(): Flow<List<ProductoEntity>> {
        return productoDao.getAllProductosFlow()
    }
}