package com.babycare.app.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.babycare.app.BabycareApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 单闹钟链触发（ReminderCenter 的 AlarmManager 闹钟，仅显式 Intent） */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                (context.applicationContext as BabycareApplication).reminderCenter.reschedule()
            } finally {
                result.finish()
            }
        }
    }
}

/** 开机重排：AlarmManager 闹钟不随重启存活（Manifest RECEIVE_BOOT_COMPLETED） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                (context.applicationContext as BabycareApplication).reminderCenter.reschedule()
            } finally {
                result.finish()
            }
        }
    }
}
