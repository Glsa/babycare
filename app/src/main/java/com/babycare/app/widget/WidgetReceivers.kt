package com.babycare.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 三款 Widget 的薄壳 receiver（Manifest 各挂一个，provider info 指向对应 xml） */
class QuickReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickWidget()
}

class OverviewReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = OverviewWidget()
}

class SleepReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SleepWidget()
}

/** AlarmManager 兜底 tick（刷新三款 + 提醒重排校准；周期由 WidgetRefresher 按睡眠态调档） */
class WidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateAllWidgets(context)
                // 顺带重排提醒（0014）：跨天/进程被杀后的校准入口
                (context.applicationContext as com.babycare.app.BabycareApplication).reminderCenter.reschedule()
            } finally {
                result.finish()
            }
        }
    }
}
