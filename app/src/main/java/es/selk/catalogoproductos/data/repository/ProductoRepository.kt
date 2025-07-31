package es.selk.catalogoproductos.data.repository

import android.util.Log
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

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
            // Usar FTS para búsqueda por descripción y familia
            flow {
                val ftsResults = try {
                    val safeQuery = trimmedQuery.replace("[\"*]".toRegex(), " ")
                    productoDao.searchProductosFTS("$safeQuery*").first()
                } catch (e: Exception) {
                    emptyList()
                }

                val likeResults = productoDao.searchProductosByDescripcionOrFamilia(trimmedQuery).first()

                // Combinar y eliminar duplicados
                val combined = (ftsResults + likeResults).distinctBy { it.referencia }
                emit(combined)
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