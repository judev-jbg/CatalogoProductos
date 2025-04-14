package es.selk.catalogoproductos.data.remote.model

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.*
import okio.Buffer

@JsonClass(generateAdapter = true)
data class ProductoResponse(

    @Json(name = "referencia") val referencia: String = "",
    @Json(name = "descripcion") val descripcion: String = "",
    @Json(name = "cantidad_bulto") val cantidadBulto: Double = 0.0,
    @Json(name = "unidad_venta") val unidadVenta: Double = 0.0,
    @Json(name = "familia") val familia: String = "",
    @Json(name = "stock_actual") val stockActual: Double = 0.0,
    @Json(name = "precio_actual") val precioActual: Double = 0.0,
    @Json(name = "descuento") val descuento: String = "",
    @Json(name = "ultima_actualizacion") val ultimaActualizacion: Long = 0L,
    @Json(name = "campo_adicional") val campoAdicional: String? = null,
    @Json(name = "estado") val estado: String = "0"
)

class ProductoResponseAdapter {
    @FromJson
    fun fromJson(jsonObject: Map<String, Any?>): ProductoResponse {
        try {
            // Extraer valores con manejo seguro de tipos
            val referencia = jsonObject["referencia"]?.toString() ?: ""
            val descripcion = jsonObject["descripcion"]?.toString() ?: ""

            // Conversión segura para números
            val cantidadBulto = safeToDouble(jsonObject["cantidad_bulto"])
            val unidadVenta = safeToDouble(jsonObject["unidad_venta"])
            val stockActual = safeToDouble(jsonObject["stock_actual"])
            val precioActual = safeToDouble(jsonObject["precio_actual"])

            val familia = jsonObject["familia"]?.toString() ?: ""
            val descuento = jsonObject["descuento"]?.toString() ?: ""
            val campoAdicional = jsonObject["campo_adicional"]?.toString()
            val estado = jsonObject["estado"]?.toString() ?: "0"

            // Obtener timestamp con manejo seguro
            val ultimaActualizacion = when (val timestamp = jsonObject["ultima_actualizacion"]) {
                is Long -> timestamp
                is Double -> timestamp.toLong()
                is String -> timestamp.toLongOrNull() ?: 0L
                else -> 0L
            }

            return ProductoResponse(
                referencia = referencia,
                descripcion = descripcion,
                cantidadBulto = cantidadBulto,
                unidadVenta = unidadVenta,
                familia = familia,
                stockActual = stockActual,
                precioActual = precioActual,
                descuento = descuento,
                ultimaActualizacion = ultimaActualizacion,
                campoAdicional = campoAdicional,
                estado = estado
            )
        } catch (e: Exception) {
            // Loguear el error y retornar un objeto vacío
            Log.e("ProductoResponseAdapter", "Error parseando JSON: $jsonObject", e)
            return ProductoResponse()
        }
    }

    // Función para convertir de forma segura a Double
    private fun safeToDouble(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is String -> {
                    value.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
            null -> 0.0
            else -> 0.0
        }
    }
}