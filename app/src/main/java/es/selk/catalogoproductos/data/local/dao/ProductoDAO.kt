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
            "    ORDER BY RANDOM()" +
            "    LIMIT 100" +
            ")" +
            "ORDER BY " +
            "    CASE WHEN estado = 'Activo' AND stock_actual > 0 THEN 1 " +
            "         WHEN estado = 'Activo' AND stock_actual <= 0 THEN 2 " +
            "         WHEN estado = 'Anulado' THEN 3 " +
            "         ELSE 4 " +
            "    END," +
            "    RANDOM()")
    fun getAllProductosFlow(): Flow<List<ProductoEntity>>

    // Búsqueda por referencia - SIN LIMIT para permitir filtrado posterior
    @Query("SELECT * FROM productos " +
            "WHERE referencia LIKE :query || '%' COLLATE NOCASE " +
            "ORDER BY " +
            "   CASE WHEN estado = 'Activo' AND stock_actual > 0 THEN 1 " +
            "        WHEN estado = 'Activo' AND stock_actual <= 0 THEN 2 " +
            "        WHEN estado = 'Anulado' THEN 3 " +
            "        ELSE 4 " +
            "   END, " +
            "   referencia")
    suspend fun searchProductosByReferencia(query: String): List<ProductoEntity>

    // Búsqueda por descripción - SIN LIMIT para permitir filtrado posterior
    @Query("SELECT * FROM productos " +
            "WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" +
            "      LOWER(descripcion), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u') " +
            "LIKE '%' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" +
            "      LOWER(:query), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u') || '%' " +
            "ORDER BY " +
            "   CASE WHEN estado = 'Activo' AND stock_actual > 0 THEN 1 " +
            "        WHEN estado = 'Activo' AND stock_actual <= 0 THEN 2 " +
            "        WHEN estado = 'Anulado' THEN 3 " +
            "        ELSE 4 " +
            "   END, " +
            "   descripcion")
    suspend fun searchProductosByDescripcion(query: String): List<ProductoEntity>

    // Búsqueda por familia - SIN LIMIT para permitir filtrado posterior
    @Query("SELECT * FROM productos " +
            "WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" +
            "      LOWER(familia), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u') " +
            "LIKE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" +
            "      LOWER(:query), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u') || '%' " +
            "ORDER BY " +
            "   CASE WHEN estado = 'Activo' AND stock_actual > 0 THEN 1 " +
            "        WHEN estado = 'Activo' AND stock_actual <= 0 THEN 2 " +
            "        WHEN estado = 'Anulado' THEN 3 " +
            "        ELSE 4 " +
            "   END, " +
            "   familia, descripcion")
    suspend fun searchProductosByFamilia(query: String): List<ProductoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductos(productos: List<ProductoEntity>)

    @Query("SELECT ultima_actualizacion FROM productos WHERE referencia = :referencia")
    suspend fun getUltimaActualizacionByReferencia(referencia: String): Long?

    @Query("SELECT COUNT(*) FROM productos WHERE referencia = :referencia")
    suspend fun existsProductoByReferencia(referencia: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducto(producto: ProductoEntity)

    @Query("SELECT COUNT(*) FROM productos")
    suspend fun getProductCount(): Int
}