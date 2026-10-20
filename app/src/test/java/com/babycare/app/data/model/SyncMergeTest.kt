package com.babycare.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 与后端 0001 语义对齐：严格更大才覆盖，相等先到先得 */
class SyncMergeTest {

    @Test
    fun table() {
        assertTrue(SyncMerge.shouldApply(null, 1L))    // 本地无此条 → 应用
        assertTrue(SyncMerge.shouldApply(5L, 6L))      // 远端更新 → 应用
        assertFalse(SyncMerge.shouldApply(5L, 5L))     // 相等 → 跳过（先到先得）
        assertFalse(SyncMerge.shouldApply(5L, 4L))     // 远端更旧 → 跳过
    }
}
