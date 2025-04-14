package es.selk.catalogoproductos.data.repository

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.Types
import es.selk.catalogoproductos.data.local.dao.HistorialPrecioDao
import es.selk.catalogoproductos.data.local.dao.HistorialStockDao
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
import okio.Buffer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val DRIVE_FILE_ID = "1Q-cf5rP3iTufaejDVevBjeh5HKmQOvzn"
private const val DRIVE_DOWNLOAD_URL = "https://drive.usercontent.google.com/uc?export=download&id=$DRIVE_FILE_ID"
private const val DRIVE_VERSION_FILE_ID = "1mx0z2oAQvXZwc0BQKYKBngqzgyi69drK"
private const val DRIVE_VERSION_DOWNLOAD_URL = "https://drive.usercontent.google.com/uc?export=download&id=$DRIVE_VERSION_FILE_ID"

class SyncRepository(
    private val productoDao: ProductoDao,
    private val historialPrecioDao: HistorialPrecioDao,
    private val historialStockDao: HistorialStockDao,
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
                val ultimoTimestamp = ultimaActualizacionDao.getUltimoTimestamp() ?: 0

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

                if( (version?.timestamp ?: 0) > ultimoTimestamp) {
                    Log.d("SyncRepository", "Hay una actualización disponible. Timestamp de version: ${version?.timestamp}")
                    versionUltimaActualizacion = version!!.version
                    timestampUltimaActualizacion = version.timestamp
                } else {
                    Log.d("SyncRepository", "No hay actualizaciones disponibles. Timestamp de version: ${version?.timestamp}")
                }

                (version?.timestamp ?: 0) > ultimoTimestamp

            } catch (e: Exception) {
                Log.e("SyncRepository", "Error verificando actualizaciones", e)
                false
            }
        }
    }

    // Descargar y aplicar actualizaciones
    suspend fun sincronizarCambios(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Descargar archivo de cambios
                val url = URL(DRIVE_DOWNLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 60000
                connection.readTimeout = 15000

                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }

                Log.d("SyncRepository", "JSON recibido: $jsonString")

                // Configurar Moshi para deserializar el JSON
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .add(ProductoResponseAdapter())
                    .build()

                // Procesar la lista de productos
                val type = Types.newParameterizedType(
                    List::class.java,
                    ProductoResponse::class.java
                )

                val productos = mutableListOf<ProductoResponse>()

                val adapter = moshi.adapter<List<ProductoResponse>>(type).lenient()
                try {
                    val parsedProductos = adapter.fromJson(jsonString) ?: listOf()
                    productos.addAll(parsedProductos)
                } catch (e: Exception) {
                    Log.e("SyncRepository", "Error parseando JSON", e)
                    // Intenta el metodo alternativo si está implementado
                }

                if (productos.isEmpty()) {
                    Log.d("SyncRepository", "No hay cambios para aplicar")
                    return@withContext false
                }
                Log.d("SyncRepository", "Datos para actualizar: ${productos}")

                // Convertir a entidades
                val productosEntities = productos.mapNotNull { productoResponse ->
                    try {
                        ProductoEntity(
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
                    } catch (e: Exception) {
                        Log.e("SyncRepository", "Error mapeando producto: ${productoResponse.referencia}", e)
                        null // Excluir este producto si hay error
                    }
                }

                Log.d("SyncRepository", "Productos filtrados: ${productosEntities.size} de ${productos.size}")

                if (productosEntities.isNotEmpty()) {
                    productoDao.insertProductos(productosEntities)

                    ultimaActualizacionDao.insertUltimaActualizacion(
                        UltimaActualizacionEntity(
                            timestamp = timestampUltimaActualizacion,
                            version = versionUltimaActualizacion
                        )
                    )


                    true
                } else {
                    Log.w("SyncRepository", "No hay productos válidos para insertar")
                    false
                }

            } catch (e: Exception) {
                Log.e("SyncRepository", "Error sincronizando datos", e)
                e.printStackTrace()
                false
            }
        }
    }

    private fun parseJsonManually(jsonString: String, moshi: Moshi): List<ProductoResponse> {
        try {
            // Usar JsonReader para leer elemento por elemento
            val reader = JsonReader.of(Buffer().writeUtf8(jsonString))
            reader.isLenient = true  // Configurar como lenient

            val productos = mutableListOf<ProductoResponse>()

            // Leer array
            reader.beginArray()
            val objectAdapter = moshi.adapter(ProductoResponse::class.java).lenient()

            while (reader.hasNext()) {
                try {
                    // Leer cada objeto individualmente
                    val producto = objectAdapter.fromJson(reader)
                    if (producto != null) {
                        productos.add(producto)
                    }
                } catch (e: Exception) {
                    // Si un objeto falla, loguearlo y continuar con el siguiente
                    Log.e("SyncRepository", "Error en un objeto JSON, saltando al siguiente", e)
                    reader.skipValue()
                }
            }
            reader.endArray()

            return productos
        } catch (e: Exception) {
            Log.e("SyncRepository", "Error en parseJsonManually", e)
            return emptyList()
        }
    }
    
    suspend fun cargarDatosIniciales(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // Leer el archivo JSON de assets
                val jsonString = context.assets.open("productos_inicial.json").bufferedReader().readText()

                // Configurar Moshi para deserializar el JSON
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()

                // Crear el adaptador para una lista de productos
                val type = Types.newParameterizedType(
                    List::class.java,
                    ProductoResponse::class.java
                )
                val adapter = moshi.adapter<List<ProductoResponse>>(type)

                // Convertir JSON a lista de objetos
                val productos = adapter.fromJson(jsonString) ?: listOf()

                // Mapear a entidades y guardar en la base de datos
                val productosEntities = productos.map {
                    ProductoEntity(
                        id_producto = 0,
                        referencia = it.referencia,
                        descripcion = it.descripcion,
                        precio_actual = it.precioActual,
                        stock_actual = it.stockActual,
                        familia = it.familia,
                        descuento = it.descuento,
                        ultima_actualizacion = it.ultimaActualizacion,
                        cantidad_bulto = it.cantidadBulto,
                        unidad_venta = it.unidadVenta,
                        estado = if (it.estado.toInt() == 0) "Activo" else "Anulado"
                        // Otros campos que necesites
                    )
                }

                // Insertar en la base de datos
                productoDao.insertProductos(productosEntities)
                Log.d("SyncRepository", "Productos insertados: ${productosEntities.size}")

                // Actualizar timestamp de última actualización
                ultimaActualizacionDao.insertUltimaActualizacion(
                    UltimaActualizacionEntity(
                        timestamp = System.currentTimeMillis(),
                        version = "1.0.0"
                    )
                )
            } catch (e: IOException) {
                e.printStackTrace()
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