package es.selk.catalogoproductos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.selk.catalogoproductos.data.local.entity.HistorialStockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistorialStockDao {
    @Query("SELECT * FROM historial_stock WHERE id_producto = :idProducto ORDER BY fecha_cambio DESC")
    fun getHistorialStockByProductoId(idProducto: String): Flow<List<HistorialStockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorialStock(historial: HistorialStockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistorialStocks(historiales: List<HistorialStockEntity>)

    @Query("SELECT * FROM historial_stock WHERE sincronizado = 0")
    suspend fun getHistorialesNoSincronizados(): List<HistorialStockEntity>

    @Query("UPDATE historial_stock SET sincronizado = 1 WHERE id IN (:ids)")
    suspend fun marcarComoSincronizados(ids: List<Long>)
}