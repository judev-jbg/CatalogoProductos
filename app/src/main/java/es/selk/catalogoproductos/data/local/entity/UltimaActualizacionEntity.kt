package es.selk.catalogoproductos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ultima_actualizacion")
data class UltimaActualizacionEntity(
    @PrimaryKey
    val id: Int = 1, // Solo habrá un registro
    val timestamp: Long,
    val version: String
)