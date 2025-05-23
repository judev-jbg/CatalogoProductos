package es.selk.catalogoproductos.data.local.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.dao.UltimaActualizacionDao
import es.selk.catalogoproductos.data.local.entity.HistorialPrecioEntity
import es.selk.catalogoproductos.data.local.entity.HistorialStockEntity
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity

@Database(
    entities = [
        ProductoEntity::class,
        HistorialPrecioEntity::class,
        HistorialStockEntity::class,
        UltimaActualizacionEntity::class,
        ProductoFTS::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun ultimaActualizacionDao(): UltimaActualizacionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "productos_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "productos_fts")
@Fts4(contentEntity = ProductoEntity::class, tokenizer = "unicode61")
data class ProductoFTS(
    @ColumnInfo(name = "rowid")
    @PrimaryKey
    val rowId: Int,
    val referencia: String,
    val descripcion: String,
    val familia: String
)