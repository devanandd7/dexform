package com.dexform.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_rules WHERE `key` = :key")
    suspend fun get(key: String): SiteRuleEntity?

    @Upsert
    suspend fun upsert(e: SiteRuleEntity)
}

@Dao
interface QuickLinkDao {
    @Query("SELECT * FROM quick_links ORDER BY createdMs DESC")
    fun getAll(): Flow<List<QuickLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: QuickLinkEntity)

    @Delete
    suspend fun delete(link: QuickLinkEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM browsing_history ORDER BY visitedMs DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Query("DELETE FROM browsing_history")
    suspend fun clearHistory()
}
