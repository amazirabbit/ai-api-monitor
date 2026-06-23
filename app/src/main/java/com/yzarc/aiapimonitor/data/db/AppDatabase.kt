package com.yzarc.aiapimonitor.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yzarc.aiapimonitor.model.ApiAccount
import com.yzarc.aiapimonitor.model.BalanceInfo
import com.yzarc.aiapimonitor.model.BalanceSnapshot
import kotlinx.coroutines.flow.Flow

// ============================================================================
// DAOs
// ============================================================================

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY created_at DESC")
    suspend fun getAll(): List<ApiAccount>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Int): ApiAccount?

    @Insert
    suspend fun insert(account: ApiAccount): Long

    @Update
    suspend fun update(account: ApiAccount)

    @Delete
    suspend fun delete(account: ApiAccount)

    @Query("UPDATE accounts SET key_status = :status, last_error = :error WHERE id = :id")
    suspend fun updateKeyStatus(id: Int, status: Int, error: String? = null)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface BalanceDao {
    @Query("SELECT * FROM balances WHERE account_id = :accountId ORDER BY updated_at DESC LIMIT 1")
    suspend fun getByAccountId(accountId: Int): BalanceInfo?

    @Query("SELECT * FROM balances WHERE id IN (SELECT MAX(id) FROM balances GROUP BY account_id)")
    suspend fun getAll(): List<BalanceInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: BalanceInfo)

    @Query("DELETE FROM balances WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Int)

    @Query("UPDATE balances SET total_balance = :total, updated_at = :ts WHERE account_id = :accountId")
    suspend fun update(accountId: Int, total: Double, ts: Long)

    /** 所有账号的最新余额 Flow（用于首页响应式更新） */
    @Query("SELECT * FROM balances WHERE id IN (SELECT MAX(id) FROM balances GROUP BY account_id)")
    fun observeAll(): Flow<List<BalanceInfo>>
}

@Dao
interface SnapshotDao {
    @Insert
    suspend fun insert(snapshot: BalanceSnapshot)

    /** 指定账号的所有快照（ASC — 保证折线图正向绘制） */
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId ORDER BY recorded_at ASC")
    suspend fun getAll(accountId: Int): List<BalanceSnapshot>

    /** 指定账号在指定时间之后的快照（ASC） */
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId AND recorded_at >= :since ORDER BY recorded_at ASC")
    suspend fun getSince(accountId: Int, since: Long): List<BalanceSnapshot>

    /** 所有账号在指定时间之后的快照（ASC） */
    @Query("SELECT * FROM balance_snapshots WHERE recorded_at >= :since ORDER BY recorded_at ASC")
    suspend fun getAllSince(since: Long): List<BalanceSnapshot>

    /** 指定账号的最新一条快照 */
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatest(accountId: Int): BalanceSnapshot?

    @Query("DELETE FROM balance_snapshots WHERE account_id = :accountId")
    suspend fun deleteByAccountId(accountId: Int)

    @Query("DELETE FROM balance_snapshots WHERE recorded_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM balance_snapshots")
    suspend fun count(): Int

    // ---- Flow 查询（响应式） ----

    /** 指定账号的所有快照 Flow */
    @Query("SELECT * FROM balance_snapshots WHERE account_id = :accountId ORDER BY recorded_at ASC")
    fun observeByAccount(accountId: Int): Flow<List<BalanceSnapshot>>

    /** 所有账号的完整快照 Flow */
    @Query("SELECT * FROM balance_snapshots ORDER BY account_id ASC, recorded_at ASC")
    fun observeAll(): Flow<List<BalanceSnapshot>>
}

// ============================================================================
// Database
// ============================================================================

@Database(
    entities = [ApiAccount::class, BalanceInfo::class, BalanceSnapshot::class],
    version = 5, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun balanceDao(): BalanceDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "api_monitor.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }

        /** v1 → v2: 新增 balance_snapshots 表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `balance_snapshots` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`account_id` INTEGER NOT NULL, " +
                    "`total_balance` REAL NOT NULL, " +
                    "`recorded_at` INTEGER NOT NULL)")
            }
        }

        /** v2 → v3: 为常用查询字段添加索引 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_snapshots_account_id` " +
                    "ON `balance_snapshots` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_snapshots_recorded_at` " +
                    "ON `balance_snapshots` (`recorded_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_balances_account_id` " +
                    "ON `balances` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_balances_updated_at` " +
                    "ON `balances` (`updated_at`)")
            }
        }

        /** v3 → v4: 重建 balances 表修复字段顺序 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE temp_balances (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER NOT NULL,
                        total_balance REAL NOT NULL DEFAULT 0.0,
                        used_this_month REAL NOT NULL DEFAULT 0.0,
                        used_today REAL NOT NULL DEFAULT 0.0,
                        currency TEXT NOT NULL DEFAULT 'USD',
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO temp_balances
                        (id, account_id, total_balance, used_this_month, used_today, currency, updated_at)
                    SELECT
                        id, account_id, total_balance, used_this_month, used_today, currency, updated_at
                    FROM balances
                """.trimIndent())
                db.execSQL("DROP TABLE balances")
                db.execSQL("ALTER TABLE temp_balances RENAME TO balances")
                db.execSQL("CREATE INDEX idx_balances_account_id ON balances(account_id)")
                db.execSQL("CREATE INDEX idx_balances_updated_at ON balances(updated_at)")
            }
        }

        /**
         * v4 → v5: 移除 balances 表的消费字段 + 新增 available_balance 到 snapshots
         *
         * 变更：
         * 1. balances 表移除 used_this_month, used_today 列
         * 2. balance_snapshots 表新增 available_balance 列
         *
         * Room 2.6.1 做运行时 schema 校验，要求迁移后表结构精确匹配 Entity 定义：
         *   - 不能有多余的 DEFAULT 约束
         *   - 不能有多余的索引（Entity 无 @Index 则期望 indices=[]）
         *
         * 因此必须重建表（DROP + RENAME），不能 ALTER TABLE（会保留旧表索引）。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---- balances: 重建无消费字段的表 ----
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS balances_v5 (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER NOT NULL,
                        total_balance REAL NOT NULL,
                        currency TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO balances_v5 (id, account_id, total_balance, currency, updated_at)
                    SELECT id, account_id, total_balance, currency, updated_at FROM balances
                """.trimIndent())
                db.execSQL("DROP TABLE balances")
                db.execSQL("ALTER TABLE balances_v5 RENAME TO balances")

                // ---- balance_snapshots: 重建表，新增 available_balance 列 ----
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS snapshots_v5 (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER NOT NULL,
                        total_balance REAL NOT NULL,
                        available_balance REAL NOT NULL DEFAULT 0.0,
                        recorded_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO snapshots_v5 (id, account_id, total_balance, available_balance, recorded_at)
                    SELECT id, account_id, total_balance, 0.0, recorded_at FROM balance_snapshots
                """.trimIndent())
                db.execSQL("DROP TABLE balance_snapshots")
                db.execSQL("ALTER TABLE snapshots_v5 RENAME TO balance_snapshots")

                // 注意：不重建任何索引。Entity 无 @Index，Room 期望 indices=[]，
                // 有多余索引会导致 IllegalStateException。
            }
        }
    }
}