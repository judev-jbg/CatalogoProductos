package es.selk.catalogoproductos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos LIMIT 10")
    fun getAllProductosFlow(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE referencia LIKE :query || '%' OR descripcion LIKE '%' || :query || '%' LIMIT 100")
    fun searchProductos(query: String): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE referencia = :referencia")
    suspend fun getProductoByReferencia(referencia: String): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductos(productos: List<ProductoEntity>)

    @Update
    suspend fun updateProducto(producto: ProductoEntity)

    @Query("UPDATE productos SET precio_actual = :precio, ultima_actualizacion = :timestamp WHERE id_producto = :idProducto")
    suspend fun updatePrecio(idProducto: Int, precio: Double, timestamp: Long)

    @Query("UPDATE productos SET stock_actual = :stock, ultima_actualizacion = :timestamp WHERE id_producto = :idProducto")
    suspend fun updateStock(idProducto: Int, stock: Int, timestamp: Long)

    @Query("DELETE FROM productos")
    suspend fun deleteAllProductos()


    @Query("SELECT * FROM productos " +
            "JOIN productos_fts ON productos.rowid = productos_fts.rowid " +
            "WHERE productos_fts MATCH :query LIMIT 50")
    fun searchProductosFTS(query: String): Flow<List<ProductoEntity>>

    @Query("SELECT COUNT(*) FROM productos")
    suspend fun getProductCount(): Int

    @Transaction
    suspend fun updateProductosConHistorial(productos: List<ProductoEntity>) {
        deleteAllProductos()
        insertProductos(productos)
    }
}