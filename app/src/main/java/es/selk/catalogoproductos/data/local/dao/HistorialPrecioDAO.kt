package es.selk.catalogoproductos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.selk.catalogoproductos.data.local.entity.HistorialPrecioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistorialPrecioDao {
    @Query("SELECT * FROM historial_precio WHERE id_producto = :idProducto ORDER BY fecha_cambio DESC")
    fun getHistorialPrecioByProductoId(idProducto: String): Flow<List<HistorialPrecioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorialPrecio(historial: HistorialPrecioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorialPrecios(historiales: List<HistorialPrecioEntity>)

    @Query("SELECT * FROM historial_precio WHERE sincronizado = 0")
    suspend fun getHistorialesNoSincronizados(): List<HistorialPrecioEntity>

    @Query("UPDATE historial_precio SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun marcarComoSincronizados(ids: List<Long>)
}