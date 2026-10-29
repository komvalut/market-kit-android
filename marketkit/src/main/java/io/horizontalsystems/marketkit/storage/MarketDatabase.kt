package io.horizontalsystems.marketkit.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.horizontalsystems.marketkit.models.BlockchainEntity
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.CoinHistoricalPrice
import io.horizontalsystems.marketkit.models.CoinPrice
import io.horizontalsystems.marketkit.models.GlobalMarketInfo
import io.horizontalsystems.marketkit.models.SyncerState
import io.horizontalsystems.marketkit.models.TokenEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.logging.Logger


@Database(
    entities = [
        Coin::class,
        BlockchainEntity::class,
        TokenEntity::class,
        CoinPrice::class,
        CoinHistoricalPrice::class,
        GlobalMarketInfo::class,
        SyncerState::class,
    ],
    version = 18,
    exportSchema = false
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class MarketDatabase : RoomDatabase() {
    abstract fun coinDao(): CoinDao
    abstract fun coinPriceDao(): CoinPriceDao
    abstract fun coinHistoricalPriceDao(): CoinHistoricalPriceDao
    abstract fun globalMarketInfoDao(): GlobalMarketInfoDao
    abstract fun syncerStateDao(): SyncerStateDao
    abstract fun blockchainEntityDao(): BlockchainEntityDao
    abstract fun tokenEntityDao(): TokenEntityDao

    companion object {

        private val logger = Logger.getLogger("MarketDatabase")

        @Volatile
        private var INSTANCE: MarketDatabase? = null

        fun getInstance(context: Context): MarketDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MarketDatabase {
            val db = Room.databaseBuilder(context, MarketDatabase::class.java, "marketKitDatabase")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        ensureInitialCoins(db, context)
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        ensureInitialCoins(db, context)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        ensureInitialCoins(db, context)
                    }
                })
//                .setQueryCallback({ sqlQuery, bindArgs ->
//                    println("SQL Query: $sqlQuery SQL Args: $bindArgs")
//                }, Executors.newSingleThreadExecutor())
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()

            // force db creation
            db.query("select 1", null)

            return db
        }

        private const val INITIAL_COINS_ASSET_VERSION_KEY = "marketkit-initial-coins-asset-version"
        private const val INITIAL_COINS_ASSET_VERSION = "1"

        private fun ensureInitialCoins(db: SupportSQLiteDatabase, context: Context) {
            val versionCursor = db.query(
                "SELECT `value` FROM `SyncerState` WHERE `key` = ?",
                arrayOf(INITIAL_COINS_ASSET_VERSION_KEY),
            )
            val loadedVersion = versionCursor.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (loadedVersion == INITIAL_COINS_ASSET_VERSION) return

            val ownsTransaction = !db.inTransaction()
            if (ownsTransaction) db.beginTransaction()
            try {
                val loadedCount = loadInitialCoins(db, context)
                db.execSQL(
                    "INSERT OR REPLACE INTO `SyncerState` (`key`, `value`) VALUES (?, ?)",
                    arrayOf(INITIAL_COINS_ASSET_VERSION_KEY, INITIAL_COINS_ASSET_VERSION),
                )
                if (ownsTransaction) db.setTransactionSuccessful()
                logger.info("Loaded initial coin catalog statements: $loadedCount")
            } catch (error: Exception) {
                logger.severe("Error in ensureInitialCoins(): ${error.message ?: error.javaClass.simpleName}")
            } finally {
                if (ownsTransaction) db.endTransaction()
            }
        }

        private fun loadInitialCoins(db: SupportSQLiteDatabase, context: Context): Int {
            val statements = context.assets.open("initial_coins_list").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readLines() }
            }
            val createStatements = statements.filter { it.startsWith("CREATE TABLE") }
            val blockchainStatements = statements.filter { it.startsWith("INSERT INTO BlockchainEntity") }
            val coinStatements = statements.filter { it.startsWith("INSERT INTO Coin VALUES") }
            val tokenStatements = statements.filter { it.startsWith("INSERT INTO TokenEntity") }
            val orderedStatements = createStatements + blockchainStatements + coinStatements + tokenStatements
            check(orderedStatements.size == statements.size) { "Unexpected statement in initial coin catalog" }

            orderedStatements.forEach { statement ->
                val safeStatement = statement.replaceFirst("INSERT INTO ", "INSERT OR IGNORE INTO ")
                db.execSQL(safeStatement)
            }
            return orderedStatements.size
        }
    }
}
