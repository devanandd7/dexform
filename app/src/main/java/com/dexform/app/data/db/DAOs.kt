package com.dexform.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isSelected = 0")
    suspend fun clearSelections()

    @Query("UPDATE user_profiles SET isSelected = 1 WHERE id = :id")
    suspend fun setSelectedProfile(id: Long)
}

@Dao
interface FieldDefDao {
    @Query("SELECT * FROM field_defs ORDER BY `group` ASC, label ASC")
    fun getAllDefs(): Flow<List<FieldDefEntity>>

    @Query("SELECT * FROM field_defs WHERE approved = 1")
    suspend fun getApprovedDefs(): List<FieldDefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDef(def: FieldDefEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefs(defs: List<FieldDefEntity>)

    @Update
    suspend fun updateDef(def: FieldDefEntity)

    @Delete
    suspend fun deleteDef(def: FieldDefEntity)
}

@Dao
interface DocRecordDao {
    @Query("SELECT * FROM doc_records WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getDocsForProfile(profileId: Long): Flow<List<DocRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoc(doc: DocRecordEntity): Long

    @Delete
    suspend fun deleteDoc(doc: DocRecordEntity)
}

@Dao
interface FieldValueDao {
    @Query("SELECT * FROM field_values WHERE profileId = :profileId AND docId IS NULL")
    fun getBioValues(profileId: Long): Flow<List<FieldValueEntity>>

    @Query("SELECT * FROM field_values WHERE profileId = :profileId AND docId = :docId")
    fun getDocValues(profileId: Long, docId: Long): Flow<List<FieldValueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(valEntity: FieldValueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(valEntities: List<FieldValueEntity>)

    @Delete
    suspend fun deleteValue(valEntity: FieldValueEntity)
}
