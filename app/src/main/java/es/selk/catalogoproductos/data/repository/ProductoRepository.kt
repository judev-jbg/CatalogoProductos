package es.selk.catalogoproductos.data.repository

import android.util.Log
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ProductoRepository(
    private val productoDao: ProductoDao,
) {

    /**
     * Busca productos de manera inteligente:
     * - Si contiene "  " (doble espacio) -> búsqueda SOLO por referencia
     * - Si no, busca en referencia, descripción Y familia y combina resultados
     */
    fun searchProductos(query: String): Flow<List<ProductoEntity>> = flow {
        // Limpiar query: quitar espacios iniciales/finales y comillas
        var cleanedQuery = query.trimStart()
            .removePrefix("\"")
            .removeSuffix("\"")

        Log.d("ProductoRepository", "Query original: '$query'")
        Log.d("ProductoRepository", "Query después de trim y comillas: '$cleanedQuery'")

        if (cleanedQuery.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        // Detectar si es búsqueda EXCLUSIVA por referencia (contiene doble espacio)
        val hasDoubleSpace = cleanedQuery.contains("  ")

        if (hasDoubleSpace) {
            Log.d("ProductoRepository", "Búsqueda EXCLUSIVA por REFERENCIA detectada")

            // Para búsquedas por referencia con doble espacio, NO eliminar espacios finales
            Log.d("ProductoRepository", "Query de referencia final: '$cleanedQuery'")
            val results = productoDao.searchProductosByReferencia(cleanedQuery)
            Log.d("ProductoRepository", "Resultados por referencia: ${results.size}")
            emit(results)
        } else {
            Log.d("ProductoRepository", "Búsqueda GENERAL (referencia + descripción + familia)")

            // Para búsqueda general, eliminar espacios finales adicionales
            cleanedQuery = cleanedQuery.trimEnd()

            Log.d("ProductoRepository", "Query limpia: '$cleanedQuery'")

            // Buscar en TODAS las fuentes: referencia, descripción y familia
            val referenciaResults = productoDao.searchProductosByReferencia(cleanedQuery)
            val descripcionResults = productoDao.searchProductosByDescripcion(cleanedQuery)
            val familiaResults = productoDao.searchProductosByFamilia(cleanedQuery)

            Log.d("ProductoRepository", "Resultados referencia: ${referenciaResults.size}")
            Log.d("ProductoRepository", "Resultados descripción: ${descripcionResults.size}")
            Log.d("ProductoRepository", "Resultados familia: ${familiaResults.size}")

            // Combinar resultados eliminando duplicados por referencia
            val combined = (referenciaResults + descripcionResults + familiaResults)
                .distinctBy { it.referencia }
                .sortedWith(compareBy(
                    // Primero: Activos con stock
                    { producto ->
                        when {
                            producto.estado == "Activo" && producto.stock_actual > 0 -> 1
                            producto.estado == "Activo" && producto.stock_actual <= 0 -> 2
                            producto.estado == "Anulado" -> 3
                            else -> 4
                        }
                    },
                    // Luego alfabéticamente por descripción
                    { it.descripcion }
                ))

            Log.d("ProductoRepository", "Total combinados (sin duplicados): ${combined.size}")
            emit(combined)
        }
    }

    suspend fun getProductCount(): Int {
        return productoDao.getProductCount()
    }

    fun getAllProductos(): Flow<List<ProductoEntity>> {
        return productoDao.getAllProductosFlow()
    }
}