package es.selk.catalogoproductos.data.repository

import android.util.Log
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.dao.UltimaActualizacionDao
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity
import es.selk.catalogoproductos.data.remote.model.ProductoResponse
import es.selk.catalogoproductos.data.remote.model.ProductoResponseAdapter
import es.selk.catalogoproductos.data.remote.model.VersionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.net.HttpURLConnection
import java.net.URL

private const val DRIVE_FILE_ID = "10MzVnbLPZC5CbwkLZ7-qhzwcyBPQ_d1I"
private const val DRIVE_DOWNLOAD_URL = "https://drive.usercontent.google.com/uc?export=download&id=$DRIVE_FILE_ID"
private const val DRIVE_VERSION_FILE_ID = "10jOi4NWeQE6NhJo-zJD8c6oScdYKSpgL"
private const val DRIVE_VERSION_DOWNLOAD_URL = "https://drive.usercontent.google.com/uc?export=download&id=$DRIVE_VERSION_FILE_ID"

class SyncRepository(
    private val productoDao: ProductoDao,
    private val ultimaActualizacionDao: UltimaActualizacionDao,

) {
    var versionUltimaActualizacion: String = ""
    var timestampUltimaActualizacion: Long = 0

    // Obtener última actualización
    fun getUltimaActualizacion(): Flow<UltimaActualizacionEntity?> {
        return ultimaActualizacionDao.getUltimaActualizacion()
    }

    // Verificar si hay actualizaciones disponibles
    suspend fun checkUpdates(): Comparable<Nothing> {
        return withContext(Dispatchers.IO) {
            try {
                val ultimoTimestamp = ultimaActualizacionDao.getUltimoTimestamp() ?: 0L
                Log.d("SyncRepository","Ultimo timestamp DAO: $ultimoTimestamp")

                // Descargar archivo de versión
                val url = URL(DRIVE_VERSION_DOWNLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }

                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()
                val adapter = moshi.adapter(VersionResponse::class.java)
                val version = adapter.fromJson(jsonString)

                // Verificar si hay una versión más reciente

                // Obtener el timestamp remoto (con validación)
                val remoteTimestamp = version?.timestamp ?: 0L
                // Guardar la información de versión para usarla después
                if (remoteTimestamp > 0) {
                    versionUltimaActualizacion = version?.version ?: "1.0.0"
                    timestampUltimaActualizacion = remoteTimestamp

                    Log.d("SyncRepository", "Version remota: $versionUltimaActualizacion, timestamp: $timestampUltimaActualizacion")
                    Log.d("SyncRepository", "Último timestamp local: $ultimoTimestamp")
                } else {
                    Log.w("SyncRepository", "Timestamp inválido recibido: $remoteTimestamp")
                }

                // Verificar si hay una versión más reciente
                val hayActualizacion = ultimoTimestamp == 0L || remoteTimestamp > ultimoTimestamp

                if (hayActualizacion) {
                    if (ultimoTimestamp == 0L) {
                        Log.d("SyncRepository", "Base de datos vacía. Se requiere sincronización inicial.")
                    } else {
                        Log.d("SyncRepository", "Nueva versión disponible. Remote: $remoteTimestamp > Local: $ultimoTimestamp")
                    }
                } else {
                    Log.d("SyncRepository", "No hay nueva version disponible. Remote: $remoteTimestamp <= Local: $ultimoTimestamp")
                }

                return@withContext hayActualizacion

            } catch (e: Exception) {
                Log.e("SyncRepository", "Error verificando actualizaciones", e)
                return@withContext false
            }
        }
    }

    // Descargar y aplicar actualizaciones
    suspend fun sincronizarCambios(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Configuración inicial
                val url = URL(DRIVE_DOWNLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 60000
                connection.readTimeout = 15000

                // Configurar Moshi para deserializar el JSON
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .add(ProductoResponseAdapter())
                    .build()

                // Usar la API de Okio para crear un Buffer
                val source = connection.inputStream.source().buffer()
                val jsonReader = JsonReader.of(source)

                // Preparar procesamiento por lotes
                val productosBatch = mutableListOf<ProductoEntity>()
                val batchSize = 100 // Procesar 100 productos a la vez
                var totalProcessed = 0
                var success = false

                // Leer array
                jsonReader.beginArray()

                // Crear adaptador para un solo objeto ProductoResponse
                val adapter = moshi.adapter(ProductoResponse::class.java)

                // Leer objetos uno por uno
                while (jsonReader.hasNext() && !jsonReader.peek().equals(JsonReader.Token.END_ARRAY)) {
                    try {
                        val productoResponse = adapter.fromJson(jsonReader)

                        if (productoResponse != null) {
                            // Convertir a entidad y añadir al lote actual
                            val entity = ProductoEntity(
                                id_producto = 0,
                                referencia = productoResponse.referencia.ifBlank { "" },
                                descripcion = productoResponse.descripcion.ifBlank { "" },
                                cantidad_bulto = productoResponse.cantidadBulto.takeIf { !it.isNaN() } ?: 0.0,
                                unidad_venta = productoResponse.unidadVenta.takeIf { !it.isNaN() } ?: 0.0,
                                familia = productoResponse.familia.ifBlank { "" },
                                stock_actual = productoResponse.stockActual.takeIf { !it.isNaN() } ?: 0.0,
                                precio_actual = productoResponse.precioActual.takeIf { !it.isNaN() } ?: 0.0,
                                descuento = productoResponse.descuento.ifBlank { "" },
                                ultima_actualizacion = productoResponse.ultimaActualizacion.takeIf { it > 0 }
                                    ?: System.currentTimeMillis(),
                                estado = if ((productoResponse.estado).toIntOrNull() == 0) "Activo" else "Anulado"
                            )
                            productosBatch.add(entity)

                            // Si alcanzamos el tamaño del lote, insertamos y limpiamos
                            if (productosBatch.size >= batchSize) {
                                productoDao.insertProductos(productosBatch)
                                totalProcessed += productosBatch.size
                                Log.d("SyncRepository", "Procesados $totalProcessed productos")
                                productosBatch.clear()

                                // Liberar memoria
                                System.gc()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SyncRepository", "Error procesando producto", e)
                        // Si hay error en un producto, intentamos saltar al siguiente
                        try {
                            jsonReader.skipValue()
                        } catch (e2: Exception) {
                            Log.e("SyncRepository", "Error intentando saltar valor", e2)
                        }
                    }
                }

                // Insertar cualquier producto restante
                if (productosBatch.isNotEmpty()) {
                    productoDao.insertProductos(productosBatch)
                    totalProcessed += productosBatch.size
                    Log.d("SyncRepository", "Procesados $totalProcessed productos (lote final)")
                }

                // Cerrar el reader
                try {
                    jsonReader.endArray()
                    jsonReader.close()
                } catch (e: Exception) {
                    Log.e("SyncRepository", "Error cerrando el reader", e)
                }

                // Actualizar timestamp si procesamos algún producto
                if (totalProcessed > 0) {
                    val timestampToSave = if (timestampUltimaActualizacion > 0)
                        timestampUltimaActualizacion
                    else
                        System.currentTimeMillis()

                    ultimaActualizacionDao.insertUltimaActualizacion(
                        UltimaActualizacionEntity(
                            timestamp = timestampToSave,
                            version = versionUltimaActualizacion.ifBlank { "1.0.0" }
                        )
                    )

                    Log.d("SyncRepository", "Sincronización completa. Procesados: $totalProcessed productos")
                    success = true
                } else {
                    Log.w("SyncRepository", "No se procesaron productos")
                    success = false
                }

                return@withContext success
            } catch (e: Exception) {
                Log.e("SyncRepository", "Error sincronizando datos", e)
                e.printStackTrace()
                return@withContext false
            }
        }
    }


    // Helper para convertir ProductoResponse a ProductoEntity
    private fun ProductoResponse.toProductoEntity(): ProductoEntity {
        return ProductoEntity(

            id_producto = 0,
            referencia = this.referencia,
            descripcion = this.descripcion,
            cantidad_bulto = this.cantidadBulto,
            unidad_venta = this.unidadVenta,
            familia = this.familia,
            stock_actual = this.stockActual,
            precio_actual = this.precioActual,
            descuento = this.descuento,
            ultima_actualizacion = this.ultimaActualizacion,
            estado = this.estado
        )
    }


}