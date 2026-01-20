package com.example.calendar_vol1.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.calendar_vol1.data.CalendarEvent
import com.example.calendar_vol1.receiver.AlarmReceiver

object ReminderManager {

    fun setReminder(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. 检查是否需要提醒 (-1 代表不提醒)
        if (event.remindMinutes == -1) return

        // 2. 计算触发时间
        val triggerTime = event.startTime - (event.remindMinutes * 60 * 1000)
        val now = System.currentTimeMillis()

        // 3. 核心检查：如果提醒时间已过，则不设置闹钟
        if (triggerTime < now) {
            Log.d("Reminder", "时间已过，不设置提醒 (ID=${event.id})")
            return
        }

        // 4. 检查精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("Reminder", "没有精确闹钟权限，无法设置提醒")
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EVENT_ID", event.id)
            putExtra("EVENT_TITLE", event.title)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            // 设置精确闹钟 (在休眠状态下也能唤醒)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d("Reminder", "设置提醒成功: ${event.title} (将在 ${java.text.SimpleDateFormat("HH:mm:ss").format(triggerTime)} 触发)")
        } catch (e: SecurityException) {
            Log.e("Reminder", "设置失败：权限不足", e)
        } catch (e: Exception) {
            Log.e("Reminder", "设置失败：未知错误", e)
        }
    }

    fun cancelReminder(context: Context, event: CalendarEvent) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                event.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                Log.d("Reminder", "已取消提醒: ${event.title}")
            }
        } catch (e: Exception) {
            Log.e("Reminder", "取消提醒失败", e)
        }
    }
}