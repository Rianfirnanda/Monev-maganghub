package com.example.data.preferences

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LocalPrefsManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getTodayDateKeyWib(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        return sdf.format(Date())
    }

    fun getCurrentTimeWib(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        return "${sdf.format(Date())} WIB"
    }

    fun isCheckedOutToday(dateKey: String = getTodayDateKeyWib()): Boolean {
        return prefs.getBoolean("checked_out_$dateKey", false)
    }

    fun setCheckedOutToday(checkedOut: Boolean, dateKey: String = getTodayDateKeyWib()) {
        prefs.edit().putBoolean("checked_out_$dateKey", checkedOut).apply()
    }

    fun getPortalUsername(): String = prefs.getString(KEY_PORTAL_USERNAME, "").orEmpty()

    fun getPortalPassword(): String = prefs.getString(KEY_PORTAL_PASSWORD, "").orEmpty()

    fun savePortalCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_PORTAL_USERNAME, username.trim())
            .putString(KEY_PORTAL_PASSWORD, password)
            .apply()
    }

    fun clearPortalCredentials() {
        prefs.edit()
            .remove(KEY_PORTAL_USERNAME)
            .remove(KEY_PORTAL_PASSWORD)
            .apply()
    }

    fun isAutoLoginEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_LOGIN_ENABLED, true)

    fun setAutoLoginEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LOGIN_ENABLED, enabled).apply()
    }

    fun isReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDER_ENABLED, true)

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getMonThuHour(): Int = prefs.getInt(KEY_MON_THU_HOUR, 16)
    fun getMonThuMinute(): Int = prefs.getInt(KEY_MON_THU_MINUTE, 0)
    fun getFriHour(): Int = prefs.getInt(KEY_FRI_HOUR, 16)
    fun getFriMinute(): Int = prefs.getInt(KEY_FRI_MINUTE, 30)

    fun saveReminderSchedule(
        monThuHour: Int,
        monThuMinute: Int,
        friHour: Int,
        friMinute: Int,
        reminderEnabled: Boolean
    ) {
        prefs.edit()
            .putInt(KEY_MON_THU_HOUR, monThuHour)
            .putInt(KEY_MON_THU_MINUTE, monThuMinute)
            .putInt(KEY_FRI_HOUR, friHour)
            .putInt(KEY_FRI_MINUTE, friMinute)
            .putBoolean(KEY_REMINDER_ENABLED, reminderEnabled)
            .apply()
    }

    fun getDriveFolderName(): String =
        prefs.getString(KEY_DRIVE_FOLDER_NAME, "Dokumentasi MagangHub Kemnaker")
            ?: "Dokumentasi MagangHub Kemnaker"

    fun setDriveFolderName(folderName: String) {
        prefs.edit().putString(KEY_DRIVE_FOLDER_NAME, folderName.trim()).apply()
    }

    fun getDriveAccessToken(): String = prefs.getString(KEY_DRIVE_ACCESS_TOKEN, "").orEmpty()

    fun getDriveTokenExpiryMs(): Long = prefs.getLong(KEY_DRIVE_TOKEN_EXPIRY_MS, 0L)

    fun hasValidDriveToken(): Boolean {
        val token = getDriveAccessToken()
        val expiry = getDriveTokenExpiryMs()
        return token.isNotBlank() && (expiry == 0L || System.currentTimeMillis() < expiry)
    }

    fun saveDriveAccessToken(token: String, expiresInSeconds: Long = 3600L) {
        val expiryMs = if (expiresInSeconds > 0) {
            System.currentTimeMillis() + (expiresInSeconds * 1000L)
        } else {
            0L
        }
        prefs.edit()
            .putString(KEY_DRIVE_ACCESS_TOKEN, token.trim())
            .putLong(KEY_DRIVE_TOKEN_EXPIRY_MS, expiryMs)
            .apply()
    }

    fun clearDriveAccessToken() {
        prefs.edit()
            .remove(KEY_DRIVE_ACCESS_TOKEN)
            .remove(KEY_DRIVE_TOKEN_EXPIRY_MS)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "monev_maganghub_prefs"
        private const val KEY_PORTAL_USERNAME = "portal_username"
        private const val KEY_PORTAL_PASSWORD = "portal_password"
        private const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_MON_THU_HOUR = "mon_thu_hour"
        private const val KEY_MON_THU_MINUTE = "mon_thu_minute"
        private const val KEY_FRI_HOUR = "fri_hour"
        private const val KEY_FRI_MINUTE = "fri_minute"
        private const val KEY_DRIVE_FOLDER_NAME = "drive_folder_name"
        private const val KEY_DRIVE_ACCESS_TOKEN = "drive_access_token"
        private const val KEY_DRIVE_TOKEN_EXPIRY_MS = "drive_token_expiry_ms"
    }
}
