package es.selk.catalogoproductos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos WHERE id_producto IN (" +
            "    SELECT id_producto FROM productos " +
            "    ORDER BY RANDOM() " +
            "    LIMIT 20" +
            ")")
    fun getAllProductosFlow(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE referencia LIKE :query || '%' COLLATE NOCASE " +
            "OR descripcion LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR familia LIKE :query || '%' COLLATE NOCASE " +
            "ORDER BY referencia LIMIT 100")
    fun searchProductos(query: String): Flow<List<ProductoEntity>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductos(productos: List<ProductoEntity>)


    @Query("SELECT p.* FROM productos p " +
            "JOIN productos_fts ON p.rowid = productos_fts.rowid " +
            "WHERE productos_fts MATCH :query " +
            "ORDER BY referencia LIMIT 50")
    fun searchProductosFTS(query: String): Flow<List<ProductoEntity>>

    @Query("SELECT COUNT(*) FROM productos")
    suspend fun getProductCount(): Int

}