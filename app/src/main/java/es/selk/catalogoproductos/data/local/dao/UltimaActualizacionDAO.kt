package es.selk.catalogoproductos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UltimaActualizacionDao {
    @Query("SELECT * FROM ultima_actualizacion WHERE id = 1")
    fun getUltimaActualizacion(): Flow<UltimaActualizacionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUltimaActualizacion(actualizacion: UltimaActualizacionEntity)

    @Query("SELECT timestamp FROM ultima_actualizacion WHERE id = 1")
    suspend fun getUltimoTimestamp(): Long?
}