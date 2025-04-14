package es.selk.catalogoproductos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "historial_stock",
    foreignKeys = [
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id_producto"],
            childColumns = ["id_producto"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["id_producto"]),
        Index(value = ["fecha_cambio"])
    ]
)
data class HistorialStockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val id_producto: Int,
    val stock: Int,
    val fecha_cambio: Long,
    val sincronizado: Boolean = false
)