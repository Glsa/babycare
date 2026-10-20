package com.babycare.app.data.model

/**
 * LWW 合并判定，与后端 0001 语义一致：更新时间严格更大才覆盖，相等则先到先得。
 * 同步下行与本地写入共用这一处，避免出现两套判定。
 */
object SyncMerge {
    fun shouldApply(localUpdatedAt: Long?, incomingUpdatedAt: Long): Boolean =
        localUpdatedAt == null || incomingUpdatedAt > localUpdatedAt
}
