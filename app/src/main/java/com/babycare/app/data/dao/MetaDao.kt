package com.babycare.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babycare.app.data.entity.MetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: MetaEntity)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("DELETE FROM meta WHERE `key` = :key")
    suspend fun delete(key: String)

    /** 清空（清空数据用；亲喂计时暂态一并清） */
    @Query("DELETE FROM meta")
    suspend fun clear()
}
