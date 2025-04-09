package es.selk.catalogoproductos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "productos",
    indices = [
        Index(value = ["id_producto"], unique = true),
        Index(value = ["descripcion"]),
        Index(value = ["ultima_actualizacion"])
    ]
)
data class ProductoEntity(
    @PrimaryKey
    val id_producto: String,
    val codigo: String,
    val descripcion: String,
    val precio_actual: Double,
    val stock_actual: Int,
    val categoria: String,
    val descuento: Double,
    val ultima_actualizacion: Long,
    val campo_adicional: String? = null
)