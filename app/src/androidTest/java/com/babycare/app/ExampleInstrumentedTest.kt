package com.babycare.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * 设备上的仪器测试示例。
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // 被测应用的 Context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.babycare.app", appContext.packageName)
    }
}
