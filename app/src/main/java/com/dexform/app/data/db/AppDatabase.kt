package com.dexform.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dexform.app.core.ai.DocumentCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SiteRuleEntity::class,
        QuickLinkEntity::class,
        HistoryEntity::class,
        UserProfileEntity::class,
        FieldDefEntity::class,
        DocRecordEntity::class,
        FieldValueEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun quickLinkDao(): QuickLinkDao
    abstract fun historyDao(): HistoryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun fieldDefDao(): FieldDefDao
    abstract fun docRecordDao(): DocRecordDao
    abstract fun fieldValueDao(): FieldValueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dexform_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            getInstance(context).fieldDefDao().insertDefs(DocumentCatalog.systemFields)
                        }
                    }
                })
                .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
