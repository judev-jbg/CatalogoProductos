package es.selk.catalogoproductos.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "productos",
    indices = [
        Index(value = ["referencia"], unique = true),
        Index(value = ["descripcion"]),
        Index(value = ["ultima_actualizacion"])
    ]
)
@Parcelize
data class ProductoEntity(
    @PrimaryKey(autoGenerate = true)
    val id_producto: Int,
    val referencia: String,
    val descripcion: String,
    val cantidad_bulto: Double,
    val unidad_venta: Double,
    val familia: String,
    val stock_actual: Double,
    val precio_actual: Double,
    val descuento: String,
    val ultima_actualizacion: Long,
    val estado: String,
    val localizacion: String
) : Parcelable