package com.yzarc.aiapimonitor.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.model.BalanceInfo
import com.yzarc.aiapimonitor.model.BalanceSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY created_at DESC") suspend fun getAll(): List<ApiAccount>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun getById(id: Int): ApiAccount?
    @Insert suspend fun insert(account: ApiAccount): Long
    @Update suspend fun update(account: ApiAccount)
    @Delete suspend fun delete(account: ApiAccount)
    @Query("UPDATE accounts SET key_status = :status, last_error = :error WHERE id = :id") suspend fun updateKeyStatus(id: Int, status: Int, error: String? = null)
    @Query("SELECT COUNT(*) FROM accounts") suspend fun count(): Int
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balances WHERE account_id = :accountId ORDER BY updated_at DESC LIMIT 1") suspend fun getByAccountId(accountId: Int): BalanceInfo?
    @Query("SELECT * FROM balances WHERE id IN (SELECT MAX(id) FROM balances GROUP BY account_id)") suspend fun getAll(): List<BalanceInfo>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(balance: BalanceInfo)
    @Query("DELETE FROM balances WHERE account_id = :accountId") suspend fun deleteByAccountId(accountId: Int)
    @Query("UPDATE balances SET total_balance = :total, updated_at = :ts WHERE account_id = :accountId") suspend fun update(accountId: Int, total: Double, ts: Long)
    @Query("SELECT * FROM balances WHERE id IN (SELECT MAX(id) FROM balances GROUP BY account_id)") fun observeAll(): Flow<List<BalanceInfo>>
}

@Dao
interface SnapshotDao {
    @Insert suspend fun insert(snapshot: BalanceSnapshot)
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId ORDER BY recorded_at ASC") suspend fun getAll(accountId: Int): List<BalanceSnapshot>
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId AND recorded_at >= :since ORDER BY recorded_at ASC") suspend fun getSince(accountId: Int, since: Long): List<BalanceSnapshot>
    @Query("SELECT * FROM balance_snapshots WHERE recorded_at >= :since ORDER BY recorded_at ASC") suspend fun getAllSince(since: Long): List<BalanceSnapshot>
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId ORDER BY recorded_at DESC LIMIT 1") suspend fun getLatest(accountId: Int): BalanceSnapshot?
    @Query("DELETE FROM balance_snapshots WHERE account_id = :accountId") suspend fun deleteByAccountId(accountId: Int)
    @Query("DELETE FROM balance_snapshots WHERE recorded_at < :before") suspend fun deleteOlderThan(before: Long)
    @Query("SELECT COUNT(*) FROM balance_snapshots") suspend fun count(): Int
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId ORDER BY recorded_at ASC") fun observeByAccount(accountId: Int): Flow<List<BalanceSnapshot>>
    @Query("SELECT * FROM balance_snapshots ORDER BY account_id ASC, recorded_at ASC") fun observeAll(): Flow<List<BalanceSnapshot>>
}

@Database(entities = [ApiAccount::class, BalanceInfo::class, BalanceSnapshot::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun balanceDao(): BalanceDao
    abstract fun snapshotDao(): SnapshotDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) { INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "api_monitor.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it } }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS balance_snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, account_id INTEGER NOT NULL, total_balance REAL NOT NULL, recorded_at INTEGER NOT NULL)") } }
        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE INDEX IF NOT EXISTS idx_snapshots_account_id ON balance_snapshots (account_id)"); db.execSQL("CREATE INDEX IF NOT EXISTS idx_snapshots_recorded_at ON balance_snapshots (recorded_at)"); db.execSQL("CREATE INDEX IF NOT EXISTS idx_balances_account_id ON balances (account_id)"); db.execSQL("CREATE INDEX IF NOT EXISTS idx_balances_updated_at ON balances (updated_at)") } }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE temp_balances (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL, total_balance REAL NOT NULL DEFAULT 0.0, used_this_month REAL NOT NULL DEFAULT 0.0, used_today REAL NOT NULL DEFAULT 0.0, currency TEXT NOT NULL DEFAULT 'USD', updated_at INTEGER NOT NULL)"); db.execSQL("INSERT INTO temp_balances SELECT id, account_id, total_balance, used_this_month, used_today, currency, updated_at FROM balances"); db.execSQL("DROP TABLE balances"); db.execSQL("ALTER TABLE temp_balances RENAME TO balances"); db.execSQL("CREATE INDEX idx_balances_account_id ON balances(account_id)"); db.execSQL("CREATE INDEX idx_balances_updated_at ON balances(updated_at)") } }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS balances_v5 (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL, total_balance REAL NOT NULL, currency TEXT NOT NULL, updated_at INTEGER NOT NULL)"); db.execSQL("INSERT INTO balances_v5 SELECT id, account_id, total_balance, currency, updated_at FROM balances"); db.execSQL("DROP TABLE balances"); db.execSQL("ALTER TABLE balances_v5 RENAME TO balances"); db.execSQL("CREATE TABLE IF NOT EXISTS snapshots_v5 (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL, total_balance REAL NOT NULL, available_balance REAL NOT NULL DEFAULT 0.0, recorded_at INTEGER NOT NULL)"); db.execSQL("INSERT INTO snapshots_v5 SELECT id, account_id, total_balance, 0.0, recorded_at FROM balance_snapshots"); db.execSQL("DROP TABLE balance_snapshots"); db.execSQL("ALTER TABLE snapshots_v5 RENAME TO balance_snapshots") } }
    }
}
