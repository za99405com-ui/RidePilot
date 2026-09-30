package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AppLogEntry
import com.example.data.model.AppTarget
import com.example.data.model.PricingBand
import com.example.data.model.WorkZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [PricingBand::class, WorkZone::class, AppLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pricingBandDao(): PricingBandDao
    abstract fun workZoneDao(): WorkZoneDao
    abstract fun appLogDao(): AppLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ridepilot_db"
                ).addCallback(DatabaseCallback(scope)).build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(database: AppDatabase) {
                val pricingDao = database.pricingBandDao()
                // Default Uber Bands
                pricingDao.insertBands(
                    listOf(
                        PricingBand(appTarget = AppTarget.UBER, minKm = 0.0, maxKm = 10.0, pricePerKm = 8.0, sortOrder = 1),
                        PricingBand(appTarget = AppTarget.UBER, minKm = 10.0, maxKm = 20.0, pricePerKm = 7.0, sortOrder = 2),
                        PricingBand(appTarget = AppTarget.UBER, minKm = 20.0, maxKm = 35.0, pricePerKm = 6.5, sortOrder = 3),
                        PricingBand(appTarget = AppTarget.UBER, minKm = 35.0, maxKm = 60.0, pricePerKm = 6.0, sortOrder = 4)
                    )
                )
                // Default inDrive Bands
                pricingDao.insertBands(
                    listOf(
                        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 0.0, maxKm = 10.0, pricePerKm = 9.0, sortOrder = 1),
                        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 10.0, maxKm = 20.0, pricePerKm = 7.0, sortOrder = 2),
                        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 20.0, maxKm = 30.0, pricePerKm = 6.5, sortOrder = 3),
                        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 30.0, maxKm = 50.0, pricePerKm = 6.0, sortOrder = 4)
                    )
                )

                // Default Work Zone (Alexandria Core Area)
                val zoneDao = database.workZoneDao()
                val alexPolygonJson = """[
                    {"latitude":31.258,"longitude":29.965},
                    {"latitude":31.267,"longitude":30.021},
                    {"latitude":31.242,"longitude":30.043},
                    {"latitude":31.205,"longitude":30.001},
                    {"latitude":31.218,"longitude":29.948}
                ]""".trimIndent()

                zoneDao.insertZone(
                    WorkZone(
                        name = "منطقة الإسكندرية الأساسية (شرق/وسط)",
                        polygonJson = alexPolygonJson,
                        isEnabled = true,
                        allowedKeywords = "سيدي بشر,ميامي,العصافرة,المندرة,السيوف,فلمنج,سيدي جابر,سموحة,محرم بك"
                    )
                )
            }
        }
    }
}
