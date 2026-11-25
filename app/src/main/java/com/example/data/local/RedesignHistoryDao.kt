package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RedesignHistoryDao {
    @Query("SELECT * FROM redesign_history ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<RedesignHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RedesignHistoryEntity): Long

    @Query("DELETE FROM redesign_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
