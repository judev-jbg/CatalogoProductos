package es.selk.catalogoproductos.data.repository

import es.selk.catalogoproductos.data.local.dao.HistorialPrecioDao
import es.selk.catalogoproductos.data.local.dao.HistorialStockDao
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.entity.HistorialPrecioEntity
import es.selk.catalogoproductos.data.local.entity.HistorialStockEntity
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class ProductoRepository(
    private val productoDao: ProductoDao,
    private val historialPrecioDao: HistorialPrecioDao,
    private val historialStockDao: HistorialStockDao
) {
    // Búsqueda de productos
    fun searchProductos(query: String): Flow<List<ProductoEntity>> {
        // Si la consulta parece un ID de producto (contiene doble espacio)
        return if (query.contains("  ")) {
            productoDao.searchProductos(query)
        } else {
            // Usar FTS para búsqueda por descripción
            productoDao.searchProductosFTS(query)
        }
    }

    // Obtener producto por ID
    suspend fun getProductoById(id: String): ProductoEntity? {
        return productoDao.getProductoByReferencia(id)
    }

    // Obtener historial de precios
    fun getHistorialPrecios(idProducto: String): Flow<List<HistorialPrecioEntity>> {
        return historialPrecioDao.getHistorialPrecioByProductoId(idProducto)
    }

    // Obtener historial de stock
    fun getHistorialStock(idProducto: String): Flow<List<HistorialStockEntity>> {
        return historialStockDao.getHistorialStockByProductoId(idProducto)
    }

    // Registrar cambio de precio
    suspend fun registrarCambioPrecio(idProducto: Int, nuevoPrecio: Double) {
        val timestamp = System.currentTimeMillis()

        // Actualizar precio actual
        productoDao.updatePrecio(idProducto, nuevoPrecio, timestamp)

        // Registrar en historial
        historialPrecioDao.insertHistorialPrecio(
            HistorialPrecioEntity(
                id_producto = idProducto,
                precio = nuevoPrecio,
                fecha_cambio = timestamp
            )
        )
    }

    // Registrar cambio de stock
    suspend fun registrarCambioStock(idProducto: Int, nuevoStock: Int) {
        val timestamp = System.currentTimeMillis()

        // Actualizar stock actual
        productoDao.updateStock(idProducto, nuevoStock, timestamp)

        // Registrar en historial
        historialStockDao.insertHistorialStock(
            HistorialStockEntity(
                id_producto = idProducto,
                stock = nuevoStock,
                fecha_cambio = timestamp
            )
        )
    }
    suspend fun getProductCount(): Int {
        return productoDao.getProductCount()
    }

    fun getAllProductos(): Flow<List<ProductoEntity>> {
        return productoDao.getAllProductosFlow()
    }


    // Cargar productos desde archivo (primera carga)
    suspend fun cargarProductosDesdeArchivo(contenido: String) {
        try {
            val productos = contenido.lines()
                .filter { it.isNotBlank() }
                .map { linea ->
                    val campos = linea.split("|")
                    if (campos.size < 12) throw IOException("Formato incorrecto")

                    ProductoEntity(
                        id_producto = campos[0].toInt(),
                        referencia = campos[1].trim(),
                        descripcion = campos[2].trim(),
                        cantidad_bulto = campos[3].toDoubleOrNull() ?: 0.0,
                        unidad_venta = campos[4].toDoubleOrNull() ?: 0.0,
                        familia = campos[5].trim(),
                        precio_actual = campos[6].toDoubleOrNull() ?: 0.0,
                        stock_actual = campos[7].toDoubleOrNull() ?: 0.0,
                        descuento = campos[8].trim(),
                        ultima_actualizacion = System.currentTimeMillis(),
                        estado = if (campos[10].toInt() == 0 ) "Activo" else "Anulado",
                    )
                }

            productoDao.updateProductosConHistorial(productos)
        } catch (e: Exception) {
            throw IOException("Error procesando archivo: ${e.message}")
        }
    }
}