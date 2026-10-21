package com.music.bitchord.data.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class WeatherAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val w = WeatherRepository.fetch(5.8747, -75.5667, true)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.createNotificationChannel(NotificationChannel("weather", "Clima", NotificationManager.IMPORTANCE_DEFAULT))
                }
                val notif = NotificationCompat.Builder(context, "weather")
                    .setSmallIcon(android.R.drawable.ic_menu_day)
                    .setContentTitle("Clima Mercurio")
                    .setContentText("${w.title} ${w.temp}${w.sym} · ${w.advice}")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                nm.notify(1001, notif)
            } catch (e: Exception) {
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}