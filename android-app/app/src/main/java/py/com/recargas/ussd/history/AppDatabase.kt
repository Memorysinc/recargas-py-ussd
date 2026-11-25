package py.com.recargas.ussd.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class TransactionStatus {
    COMPLETADA,
    CANCELADA,
    FALLIDA,
    DESCONOCIDA
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val operatorId: String,
    val operatorName: String,
    val phoneNumber: String?,
    val amountGs: Int?,
    val operationType: String,
    val productName: String,
    val validity: String,
    val executionMode: String,
    val status: String,
    val detailMessage: String
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val operatorId: String,
    val categoryId: String,
    val productId: String? = null,
    val productName: String,
    val amountGs: Int? = null,
    val validity: String = "",
    val defaultPhoneNumber: String? = null,
    val sortOrder: Int = 0
)

@Entity(tableName = "frequent_numbers")
data class FrequentNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val operatorId: String,
    val alias: String = "",
    val useCount: Int = 1,
    val lastUsedMillis: Long = System.currentTimeMillis()
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FrequentNumberDao {
    @Query("SELECT * FROM frequent_numbers ORDER BY useCount DESC, lastUsedMillis DESC")
    fun observeAll(): Flow<List<FrequentNumberEntity>>

    @Query("SELECT * FROM frequent_numbers WHERE operatorId = :operatorId ORDER BY useCount DESC, lastUsedMillis DESC")
    fun observeByOperator(operatorId: String): Flow<List<FrequentNumberEntity>>

    @Query("SELECT * FROM frequent_numbers WHERE phoneNumber = :phone LIMIT 1")
    suspend fun findByNumber(phone: String): FrequentNumberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FrequentNumberEntity)

    @Query("DELETE FROM frequent_numbers WHERE phoneNumber = :phone")
    suspend fun deleteByNumber(phone: String)
}

@Database(
    entities = [TransactionEntity::class, FavoriteEntity::class, FrequentNumberEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun frequentNumberDao(): FrequentNumberDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "recargas_py.db"
                ).build().also { INSTANCE = it }
            }
        }

        suspend fun seedDefaultFavoritesIfEmpty(db: AppDatabase) {
            if (db.favoriteDao().count() == 0) {
                db.favoriteDao().insertAll(
                    listOf(
                        FavoriteEntity(
                            label = "Recarga Tigo Gs. 5.000",
                            operatorId = "tigo",
                            categoryId = "RECHARGE",
                            productName = "Recarga Gs. 5.000",
                            amountGs = 5000,
                            validity = "Según operador",
                            sortOrder = 1
                        ),
                        FavoriteEntity(
                            label = "Recarga Tigo Gs. 10.000",
                            operatorId = "tigo",
                            categoryId = "RECHARGE",
                            productName = "Recarga Gs. 10.000",
                            amountGs = 10000,
                            validity = "Según operador",
                            sortOrder = 2
                        ),
                        FavoriteEntity(
                            label = "Combo Tigo 1G+Mi Gs. 8.000",
                            operatorId = "tigo",
                            categoryId = "COMBOS",
                            productId = "tigo_combo_1",
                            productName = "1G+Mi",
                            amountGs = 8000,
                            validity = "1 día",
                            sortOrder = 3
                        ),
                        FavoriteEntity(
                            label = "Recarga Claro Gs. 5.000",
                            operatorId = "claro",
                            categoryId = "RECHARGE",
                            productName = "Recarga Gs. 5.000",
                            amountGs = 5000,
                            validity = "Según operador",
                            sortOrder = 4
                        )
                    )
                )
            }
        }
    }
}
