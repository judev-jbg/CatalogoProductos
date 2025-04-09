package es.selk.catalogoproductos.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProductoResponse(
    @Json(name = "id_producto") val idProducto: String,
    @Json(name = "codigo") val codigo: String,
    @Json(name = "descripcion") val descripcion: String,
    @Json(name = "precio") val precio: Double,
    @Json(name = "stock") val stock: Int,
    @Json(name = "categoria") val categoria: String,
    @Json(name = "descuento") val descuento: Double,
    @Json(name = "campo_adicional") val campoAdicional: String?,
    @Json(name = "ultima_actualizacion") val ultimaActualizacion: Long
)