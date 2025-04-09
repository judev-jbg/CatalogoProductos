package es.selk.catalogoproductos.data.local.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import es.selk.catalogoproductos.data.local.dao.HistorialPrecioDao
import es.selk.catalogoproductos.data.local.dao.HistorialStockDao
import es.selk.catalogoproductos.data.local.dao.ProductoDao
import es.selk.catalogoproductos.data.local.dao.UltimaActualizacionDao
import es.selk.catalogoproductos.data.local.entity.HistorialPrecioEntity
import es.selk.catalogoproductos.data.local.entity.HistorialStockEntity
import es.selk.catalogoproductos.data.local.entity.ProductoEntity
import es.selk.catalogoproductos.data.local.entity.UltimaActualizacionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductoEntity::class,
        HistorialPrecioEntity::class,
        HistorialStockEntity::class,
        UltimaActualizacionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun historialPrecioDao(): HistorialPrecioDao
    abstract fun historialStockDao(): HistorialStockDao
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
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Inicialización de la base de datos si es necesario
                            CoroutineScope(Dispatchers.IO).launch {
                                val timestamp = System.currentTimeMillis()
                                INSTANCE?.ultimaActualizacionDao()?.insertUltimaActualizacion(
                                    UltimaActualizacionEntity(
                                        timestamp = timestamp,
                                        version = "1.0.0"
                                    )
                                )
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "productos_fts")
@Fts4(contentEntity = ProductoEntity::class)
data class ProductoFTS(
    @ColumnInfo(name = "rowid")
    @PrimaryKey
    val rowId: Int,
    val id_producto: String,
    val descripcion: String
)

