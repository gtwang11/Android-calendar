package com.example.calendar_vol1.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.calendar_vol1.MainActivity
import com.example.calendar_vol1.R

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 打印日志，证明接收器被激活了
        Log.d("Reminder", "AlarmReceiver: 收到闹钟广播！")

        val eventId = intent.getLongExtra("EVENT_ID", -1)
        val title = intent.getStringExtra("EVENT_TITLE") ?: "日程提醒"

        Log.d("Reminder", "AlarmReceiver: ID=$eventId, Title=$title")

        if (eventId != -1L) {
            createNotificationChannel(context) // 确保渠道存在
            showNotification(context, eventId, title)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "calendar_reminder_channel",
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH // 重要性设为高
            ).apply {
                description = "日历日程提醒通知"
                enableVibration(true) // 开启震动
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, eventId: Long, title: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, eventId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, "calendar_reminder_channel")
            .setSmallIcon(R.drawable.ic_event)
            .setContentTitle("日程提醒")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(eventId.toInt(), builder.build())
            Log.d("Reminder", "AlarmReceiver: 通知已发出")
        } else {
            Log.e("Reminder", "AlarmReceiver: 缺少通知权限，无法发送通知！")
        }
    }
}