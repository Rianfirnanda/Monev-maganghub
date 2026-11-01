package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.drive.GoogleDriveService
import com.example.data.model.AttendanceLog
import com.example.data.model.UserSettings
import com.example.data.preferences.LocalPrefsManager
import com.example.data.repository.MonevRepository
import com.example.notification.AttendanceNotificationHelper
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

class MonevViewModel(
    private val repository: MonevRepository,
    private val prefsManager: LocalPrefsManager,
    private val appContext: Context
) : ViewModel() {

    private val driveService = GoogleDriveService()

    val attendanceLogsState: StateFlow<DataUiState<List<AttendanceLog>>> =
        repository.observeAttendanceLogs()
            .map<List<AttendanceLog>, DataUiState<List<AttendanceLog>>> { logs ->
                val todayKey = prefsManager.getTodayDateKeyWib()
                val todayLog = logs.firstOrNull { it.dateKey == todayKey }
                if (todayLog != null) {
                    prefsManager.setCheckedOutToday(todayLog.checkedOut, todayKey)
                    _todayCheckedOut.value = todayLog.checkedOut
                }
                DataUiState.Success(logs)
            }
            .catch { error ->
                Log.w(TAG, "Error observing attendance logs", error)
                emit(DataUiState.Error(error.message ?: "Gagal memuat riwayat absensi"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    val userSettingsState: StateFlow<DataUiState<UserSettings?>> =
        repository.observeUserSettings()
            .map<UserSettings?, DataUiState<UserSettings?>> { settings ->
                if (settings != null) {
                    prefsManager.saveReminderSchedule(
                        monThuHour = settings.monThuHour.toInt(),
                        monThuMinute = settings.monThuMinute.toInt(),
                        friHour = settings.friHour.toInt(),
                        friMinute = settings.friMinute.toInt(),
                        reminderEnabled = settings.reminderEnabled
                    )
                    prefsManager.setAutoLoginEnabled(settings.autoLoginEnabled)
                    prefsManager.setDriveFolderName(settings.driveFolderName)
                    AttendanceNotificationHelper.scheduleNextReminder(appContext)
                    refreshLocalScheduleState()
                }
                DataUiState.Success(settings)
            }
            .catch { error ->
                Log.w(TAG, "Error observing user settings", error)
                emit(DataUiState.Error(error.message ?: "Gagal memuat pengaturan"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    private val _todayCheckedOut = MutableStateFlow(prefsManager.isCheckedOutToday())
    val todayCheckedOut: StateFlow<Boolean> = _todayCheckedOut.asStateFlow()

    private val _portalUsername = MutableStateFlow(prefsManager.getPortalUsername())
    val portalUsername: StateFlow<String> = _portalUsername.asStateFlow()

    private val _portalPassword = MutableStateFlow(prefsManager.getPortalPassword())
    val portalPassword: StateFlow<String> = _portalPassword.asStateFlow()

    private val _autoLoginEnabled = MutableStateFlow(prefsManager.isAutoLoginEnabled())
    val autoLoginEnabled: StateFlow<Boolean> = _autoLoginEnabled.asStateFlow()

    private val _reminderEnabled = MutableStateFlow(prefsManager.isReminderEnabled())
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _monThuHour = MutableStateFlow(prefsManager.getMonThuHour())
    val monThuHour: StateFlow<Int> = _monThuHour.asStateFlow()

    private val _monThuMinute = MutableStateFlow(prefsManager.getMonThuMinute())
    val monThuMinute: StateFlow<Int> = _monThuMinute.asStateFlow()

    private val _friHour = MutableStateFlow(prefsManager.getFriHour())
    val friHour: StateFlow<Int> = _friHour.asStateFlow()

    private val _friMinute = MutableStateFlow(prefsManager.getFriMinute())
    val friMinute: StateFlow<Int> = _friMinute.asStateFlow()

    private val _driveFolderName = MutableStateFlow(prefsManager.getDriveFolderName())
    val driveFolderName: StateFlow<String> = _driveFolderName.asStateFlow()

    private val _driveConnected = MutableStateFlow(prefsManager.hasValidDriveToken())
    val driveConnected: StateFlow<Boolean> = _driveConnected.asStateFlow()

    private val _nextAlarmDisplay = MutableStateFlow(
        AttendanceNotificationHelper.formatNextScheduleWib(
            prefsManager.getMonThuHour(),
            prefsManager.getMonThuMinute(),
            prefsManager.getFriHour(),
            prefsManager.getFriMinute()
        )
    )
    val nextAlarmDisplay: StateFlow<String> = _nextAlarmDisplay.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isUploadingDrive = MutableStateFlow(false)
    val isUploadingDrive: StateFlow<Boolean> = _isUploadingDrive.asStateFlow()

    init {
        AttendanceNotificationHelper.ensureNotificationChannel(appContext)
        AttendanceNotificationHelper.scheduleNextReminder(appContext)
        viewModelScope.launch {
            trySilentDriveAuthorization()
        }
    }

    private suspend fun trySilentDriveAuthorization(): String? {
        if (prefsManager.hasValidDriveToken()) {
            _driveConnected.value = true
            return prefsManager.getDriveAccessToken()
        }
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(GoogleDriveService.DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(appContext).authorize(request).await()
            if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                val token = result.accessToken!!
                prefsManager.saveDriveAccessToken(token, 3600L)
                _driveConnected.value = true
                token
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent Drive auth not yet granted: ${e.message}")
            null
        }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    private fun refreshLocalScheduleState() {
        _reminderEnabled.value = prefsManager.isReminderEnabled()
        _monThuHour.value = prefsManager.getMonThuHour()
        _monThuMinute.value = prefsManager.getMonThuMinute()
        _friHour.value = prefsManager.getFriHour()
        _friMinute.value = prefsManager.getFriMinute()
        _autoLoginEnabled.value = prefsManager.isAutoLoginEnabled()
        _driveFolderName.value = prefsManager.getDriveFolderName()
        _driveConnected.value = prefsManager.hasValidDriveToken()
        _nextAlarmDisplay.value = AttendanceNotificationHelper.formatNextScheduleWib(
            _monThuHour.value,
            _monThuMinute.value,
            _friHour.value,
            _friMinute.value
        )
    }

    fun savePortalCredentials(username: String, password: String, autoLogin: Boolean) {
        prefsManager.savePortalCredentials(username, password)
        prefsManager.setAutoLoginEnabled(autoLogin)
        _portalUsername.value = username.trim()
        _portalPassword.value = password
        _autoLoginEnabled.value = autoLogin
        _statusBannerMessage.value = "Kredensial login otomatis Monev MagangHub berhasil disimpan."
    }

    fun saveDriveAccessToken(token: String, expiresInSeconds: Long = 3600L) {
        prefsManager.saveDriveAccessToken(token, expiresInSeconds)
        _driveConnected.value = prefsManager.hasValidDriveToken()
        _statusBannerMessage.value = "Akun Google Drive berhasil terhubung untuk dokumentasi harian!"
    }

    fun disconnectDrive() {
        prefsManager.clearDriveAccessToken()
        _driveConnected.value = false
        _statusBannerMessage.value = "Koneksi token Google Drive telah diputuskan."
    }

    fun toggleTodayCheckedOut(checkedOut: Boolean, activityNote: String = "") {
        val todayKey = prefsManager.getTodayDateKeyWib()
        val timeNow = prefsManager.getCurrentTimeWib()
        prefsManager.setCheckedOutToday(checkedOut, todayKey)
        _todayCheckedOut.value = checkedOut

        viewModelScope.launch {
            val summary = activityNote.ifBlank {
                if (checkedOut) "Sudah mengisi absen pulang di portal Monev MagangHub ($timeNow)"
                else "Belum mengisi absen pulang"
            }
            val result = repository.upsertAttendanceLog(
                dateKey = todayKey,
                checkedOut = checkedOut,
                activitySummary = summary,
                checkOutTime = if (checkedOut) timeNow else null
            )
            if (result.isSuccess) {
                _statusBannerMessage.value = if (checkedOut) {
                    "Status absen pulang hari ini ($todayKey) ditandai SUDAH ABSEN. Pengingat hari ini dinonaktifkan."
                } else {
                    "Status absen pulang hari ini ($todayKey) ditandai BELUM ABSEN. Pengingat aktif untuk jam pulang."
                }
            } else {
                _statusBannerMessage.value =
                    result.exceptionOrNull()?.localizedMessage ?: "Gagal menyimpan status absensi"
            }
        }
    }

    fun uploadDailyPhotoAndSaveLog(
        bitmap: Bitmap?,
        localUriString: String?,
        activitySummary: String,
        markCheckedOut: Boolean
    ) {
        viewModelScope.launch {
            _isUploadingDrive.value = true
            val todayKey = prefsManager.getTodayDateKeyWib()
            val timeNow = prefsManager.getCurrentTimeWib()
            val cleanSummary = activitySummary.trim().ifEmpty {
                "Dokumentasi kegiatan magang harian tanggal $todayKey"
            }

            try {
                var driveFileId: String? = null
                var driveWebLink: String? = null
                var driveFileName: String? = null
                var createdFolderPath: String? = null

                val activeToken = trySilentDriveAuthorization() ?: prefsManager.getDriveAccessToken()

                if (bitmap != null && activeToken.isNotBlank()) {
                    val hierarchy = GoogleDriveService.buildDateFolderHierarchy(_driveFolderName.value, todayKey)
                    val fileName = "Absen_${hierarchy.dateFolder.replace(' ', '_')}_${System.currentTimeMillis() / 1000}.jpg"
                    val uploadRes = driveService.uploadPhotoToDrive(
                        accessToken = activeToken,
                        bitmap = bitmap,
                        fileName = fileName,
                        description = "Dokumentasi Harian MagangHub ($todayKey): $cleanSummary",
                        rootFolderName = _driveFolderName.value,
                        dateKey = todayKey
                    )
                    if (uploadRes.isSuccess) {
                        val info = uploadRes.getOrThrow()
                        driveFileId = info.fileId
                        driveWebLink = info.webViewLink
                        driveFileName = "${hierarchy.dateFolder} • ${info.fileName}".take(195)
                        createdFolderPath = info.folderPath
                    } else {
                        val errMsg = uploadRes.exceptionOrNull()?.localizedMessage
                            ?: "Gagal mengunggah foto ke Google Drive"
                        _statusBannerMessage.value = errMsg
                        _isUploadingDrive.value = false
                        return@launch
                    }
                }

                if (markCheckedOut) {
                    prefsManager.setCheckedOutToday(true, todayKey)
                    _todayCheckedOut.value = true
                }

                val saveRes = repository.upsertAttendanceLog(
                    dateKey = todayKey,
                    checkedOut = if (markCheckedOut) true else _todayCheckedOut.value,
                    activitySummary = cleanSummary,
                    checkOutTime = if (markCheckedOut) timeNow else null,
                    driveFileId = driveFileId,
                    driveWebViewLink = driveWebLink,
                    driveFileName = driveFileName,
                    localPhotoUri = localUriString
                )

                if (saveRes.isSuccess) {
                    _statusBannerMessage.value = if (driveFileId != null) {
                        "Foto berhasil diunggah ke folder Drive: ${createdFolderPath ?: _driveFolderName.value}"
                    } else {
                        "Rekap absensi & dokumentasi harian ($todayKey) berhasil disimpan!"
                    }
                } else {
                    _statusBannerMessage.value =
                        saveRes.exceptionOrNull()?.localizedMessage ?: "Gagal menyimpan log absensi"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBannerMessage.value = e.localizedMessage ?: "Terjadi kesalahan saat menyimpan"
            } finally {
                _isUploadingDrive.value = false
            }
        }
    }

    fun updateReminderAndSettings(
        monThuHour: Int,
        monThuMinute: Int,
        friHour: Int,
        friMinute: Int,
        reminderEnabled: Boolean,
        autoLoginEnabled: Boolean,
        driveFolderName: String
    ) {
        prefsManager.saveReminderSchedule(
            monThuHour = monThuHour,
            monThuMinute = monThuMinute,
            friHour = friHour,
            friMinute = friMinute,
            reminderEnabled = reminderEnabled
        )
        prefsManager.setAutoLoginEnabled(autoLoginEnabled)
        prefsManager.setDriveFolderName(driveFolderName)
        AttendanceNotificationHelper.scheduleNextReminder(appContext)
        refreshLocalScheduleState()

        viewModelScope.launch {
            val res = repository.saveUserSettings(
                monThuHour = monThuHour,
                monThuMinute = monThuMinute,
                friHour = friHour,
                friMinute = friMinute,
                reminderEnabled = reminderEnabled,
                autoLoginEnabled = autoLoginEnabled,
                driveFolderName = driveFolderName
            )
            _statusBannerMessage.value = if (res.isSuccess) {
                "Pengaturan pengingat (Senin–Kamis %02d:%02d WIB, Jumat %02d:%02d WIB) berhasil disimpan.".format(
                    monThuHour,
                    monThuMinute,
                    friHour,
                    friMinute
                )
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Pengaturan disimpan secara lokal"
            }
        }
    }

    fun triggerTestNotification() {
        AttendanceNotificationHelper.showAttendanceReminderNotification(
            context = appContext,
            isTestNotification = true
        )
        _statusBannerMessage.value =
            "Notifikasi pengingat lokal telah dikirim! Periksa bilah status / panel notifikasi perangkat Anda."
    }

    companion object {
        private const val TAG = "MonevViewModel"
    }
}
