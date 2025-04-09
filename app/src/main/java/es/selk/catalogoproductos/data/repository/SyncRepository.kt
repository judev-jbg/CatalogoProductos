package es.selk.catalogoproductos.data.repository

import es.selk.catalogoproductos.data.local.dao.HistorialPrecioDao
import es.selk.catalogoproductos.data.local.dao.HistorialStockDao
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.dao.UltimaActualizacionDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity
import es.selk.catalogoproductos.data.remote.model.ProductoResponse
import es.selk.catalogoproductos.data.remote.service.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URL

class SyncRepository(
    private val productoDao: ProductoDao,
    private val historialPrecioDao: HistorialPrecioDao,
    private val historialStockDao: HistorialStockDao,
    private val ultimaActualizacionDao: UltimaActualizacionDao
) {
    // Obtener última actualización
    fun getUltimaActualizacion(): Flow<UltimaActualizacionEntity?> {
        return ultimaActualizacionDao.getUltimaActualizacion()
    }

    // Comprobar si hay actualizaciones disponibles
    suspend fun checkUpdates(): Boolean {
        return try {
            val ultimoTimestamp = ultimaActualizacionDao.getUltimoTimestamp() ?: 0
            val version = ApiClient.productoApiService.checkVersion()
            version.timestamp > ultimoTimestamp
        } catch (e: Exception) {
            false
        }
    }

    // Descargar y aplicar actualizaciones
    suspend fun sincronizarCambios(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val ultimoTimestamp = ultimaActualizacionDao.getUltimoTimestamp() ?: 0

                // Obtener solo cambios desde última actualización
                val cambios = ApiClient.productoApiService.getChanges(ultimoTimestamp)
                if (cambios.isEmpty()) return@withContext false

                // Procesar cambios
                val productosActualizados = cambios.map { it.toProductoEntity() }

                // Actualizar productos
                productoDao.insertProductos(productosActualizados)

                // Actualizar timestamp
                val version = ApiClient.productoApiService.checkVersion()
                ultimaActualizacionDao.insertUltimaActualizacion(
                    UltimaActualizacionEntity(
                        timestamp = version.timestamp,
                        version = version.version
                    )
                )

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // Descargar archivo JSON para actualización offline
    suspend fun descargarArchivoDiferencias(urlStr: String, destFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val connection = url.openConnection()
                val inputStream = connection.getInputStream()
                destFile.outputStream().use { fileOut ->
                    inputStream.copyTo(fileOut)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // Procesar archivo de diferencias
    suspend fun procesarArchivoDiferencias(archivoDif: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val contenido = FileInputStream(archivoDif).bufferedReader().use { it.readText() }

                // Aquí procesaríamos el JSON que tiene los cambios
                // Este es un ejemplo simplificado

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // Helper para convertir ProductoResponse a ProductoEntity
    private fun ProductoResponse.toProductoEntity(): ProductoEntity {
        return ProductoEntity(
            id_producto = this.idProducto,
            codigo = this.codigo,
            descripcion = this.descripcion,
            precio_actual = this.precio,
            stock_actual = this.stock,
            categoria = this.categoria,
            descuento = this.descuento,
            ultima_actualizacion = this.ultimaActualizacion,
            campo_adicional = this.campoAdicional
        )
    }
}