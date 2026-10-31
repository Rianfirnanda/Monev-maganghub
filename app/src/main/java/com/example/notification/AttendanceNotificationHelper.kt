package com.example.notification

import android.Manifest
import android.app.AlarmManager
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
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.LocalPrefsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object AttendanceNotificationHelper {

    const val CHANNEL_ID = "monev_attendance_reminder_channel"
    const val NOTIFICATION_ID = 16001
    const val ACTION_MARK_CHECKED_OUT = "com.aistudio.monevmaganghub.ACTION_MARK_CHECKED_OUT"
    const val EXTRA_OPEN_PORTAL = "EXTRA_OPEN_PORTAL"
    private const val ALARM_REQUEST_CODE = 16002
    private const val TAG = "AttendanceNotif"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Calculates the next scheduled attendance reminder in Asia/Jakarta (WIB):
     * - Monday to Thursday: monThuHour:monThuMinute (default 16:00 WIB)
     * - Friday: friHour:friMinute (default 16:30 WIB)
     * - Skips Saturday & Sunday to next Monday.
     */
    fun computeNextReminderCalendarWib(
        monThuHour: Int,
        monThuMinute: Int,
        friHour: Int,
        friMinute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Calendar {
        val wibZone = TimeZone.getTimeZone("Asia/Jakarta")
        val nowCal = Calendar.getInstance(wibZone).apply {
            timeInMillis = nowMillis
        }

        for (dayOffset in 0..7) {
            val candidate = Calendar.getInstance(wibZone).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            val isMonToThu = dayOfWeek in listOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY
            )
            val isFriday = dayOfWeek == Calendar.FRIDAY

            if (isMonToThu || isFriday) {
                val targetHour = if (isFriday) friHour else monThuHour
                val targetMinute = if (isFriday) friMinute else monThuMinute
                candidate.set(Calendar.HOUR_OF_DAY, targetHour)
                candidate.set(Calendar.MINUTE, targetMinute)
                candidate.set(Calendar.SECOND, 0)
                candidate.set(Calendar.MILLISECOND, 0)

                if (candidate.timeInMillis > nowCal.timeInMillis + 5_000L) {
                    return candidate
                }
            }
        }

        // Fallback: tomorrow at monThuHour:monThuMinute
        return Calendar.getInstance(wibZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, monThuHour)
            set(Calendar.MINUTE, monThuMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun formatNextScheduleWib(
        monThuHour: Int,
        monThuMinute: Int,
        friHour: Int,
        friMinute: Int
    ): String {
        val nextCal = computeNextReminderCalendarWib(monThuHour, monThuMinute, friHour, friMinute)
        val sdf = SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }
        return "${sdf.format(nextCal.time)} WIB"
    }

    fun scheduleNextReminder(context: Context) {
        val prefs = LocalPrefsManager(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AttendanceAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!prefs.isReminderEnabled()) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Attendance reminder disabled; cancelled existing alarm.")
            return
        }

        val nextCal = computeNextReminderCalendarWib(
            monThuHour = prefs.getMonThuHour(),
            monThuMinute = prefs.getMonThuMinute(),
            friHour = prefs.getFriHour(),
            friMinute = prefs.getFriMinute()
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextCal.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextCal.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextCal.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled next attendance reminder at ${nextCal.time}")
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextCal.timeInMillis,
                pendingIntent
            )
        }
    }

    fun showAttendanceReminderNotification(
        context: Context,
        isTestNotification: Boolean = false
    ) {
        ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted; skipping notification.")
                return
            }
        }

        val wibZone = TimeZone.getTimeZone("Asia/Jakarta")
        val cal = Calendar.getInstance(wibZone)
        val isFriday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val jamPulangLabel = if (isFriday) "Jumat 16:30 WIB" else "Senin–Kamis 16:00 WIB"

        val openPortalIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PORTAL, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            openPortalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDoneIntent = Intent(context, AttendanceAlarmReceiver::class.java).apply {
            action = ACTION_MARK_CHECKED_OUT
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            2002,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isTestNotification) {
            "🔔 [Tes] Pengingat Absen Pulang MagangHub"
        } else {
            "⏰ Waktunya Absen Pulang MagangHub!"
        }

        val message = "Anda belum menandai absen pulang hari ini ($jamPulangLabel). " +
            "Ketuk untuk membuka dashboard Monev Kemnaker & unggah foto dokumentasi ke Google Drive."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Isi Absen Sekarang",
                openPendingIntent
            )
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Sudah Absen",
                markDonePendingIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class AttendanceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = LocalPrefsManager(context)
        if (intent?.action == AttendanceNotificationHelper.ACTION_MARK_CHECKED_OUT) {
            prefs.setCheckedOutToday(true)
            NotificationManagerCompat.from(context).cancel(AttendanceNotificationHelper.NOTIFICATION_ID)
            return
        }

        // Only notify if the user has NOT yet filled their checkout attendance today
        if (!prefs.isCheckedOutToday()) {
            AttendanceNotificationHelper.showAttendanceReminderNotification(
                context = context,
                isTestNotification = false
            )
        }
        // Always schedule the next weekday alarm
        AttendanceNotificationHelper.scheduleNextReminder(context)
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AttendanceNotificationHelper.scheduleNextReminder(context)
        }
    }
}
