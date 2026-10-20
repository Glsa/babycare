package com.babycare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.babycare.app.ui.BabycareApp
import com.babycare.app.ui.theme.BabycareTheme
import com.babycare.app.widget.EXTRA_SHEET

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSheetIntent(intent)
        setContent {
            // 我的页深色开关：覆盖系统设置（关 = 跟随系统）
            val app = application as BabycareApplication
            val darkOverride by app.darkOverride.collectAsState()
            BabycareTheme(darkTheme = darkOverride || isSystemInDarkTheme()) {
                BabycareApp()
            }
        }
    }

    /** singleTop：已在栈顶时 widget 深链走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSheetIntent(intent)
    }

    private fun handleSheetIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SHEET)?.let { (application as BabycareApplication).pendingSheet.value = it }
        // 提醒通知点击直达 Tab（0014）：tab 下标见 BabycareApp Tabs 顺序
        intent?.getIntExtra(com.babycare.app.remind.ReminderCenter.EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let {
            (application as BabycareApplication).pendingTab.value = it
        }
    }
}
