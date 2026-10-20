package com.babycare.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babycare.app.data.dao.MetaDao
import com.babycare.app.data.dao.RecordDao
import com.babycare.app.data.entity.MetaEntity
import com.babycare.app.data.entity.RecordEntity

@Database(
    entities = [RecordEntity::class, MetaEntity::class],
    version = 2,
    // 单人项目暂不开 schema 导出；迁移以手写 SQL 维护（0016: +dirty 列）
    exportSchema = false,
)
abstract class BabyDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun metaDao(): MetaDao

    companion object {
        /** v1→v2（0016）：records 加待上行标记；默认 1 = 存量记录推送状态未知，视为待推（首次绑定的全量上行由此成立） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): BabyDatabase =
            Room.databaseBuilder(context.applicationContext, BabyDatabase::class.java, "babycare.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
