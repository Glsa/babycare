package com.babycare.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** key-value 杂项：sync_cursor、active_baby_id 等 */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
