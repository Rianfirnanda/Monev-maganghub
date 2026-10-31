package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AttendanceLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val checkedOut: Boolean = false,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val activitySummary: String = "",
    val driveFileId: String? = null,
    val driveWebViewLink: String? = null,
    val driveFileName: String? = null,
    val localPhotoUri: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "dateKey" to dateKey,
            "checkedOut" to checkedOut,
            "checkInTime" to checkInTime,
            "checkOutTime" to checkOutTime,
            "activitySummary" to activitySummary,
            "driveFileId" to driveFileId,
            "driveWebViewLink" to driveWebViewLink,
            "driveFileName" to driveFileName,
            "localPhotoUri" to localPhotoUri,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "checkedOut" to checkedOut,
            "checkInTime" to checkInTime,
            "checkOutTime" to checkOutTime,
            "activitySummary" to activitySummary,
            "driveFileId" to driveFileId,
            "driveWebViewLink" to driveWebViewLink,
            "driveFileName" to driveFileName,
            "localPhotoUri" to localPhotoUri,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monThuHour: Long = 16L,
    val monThuMinute: Long = 0L,
    val friHour: Long = 16L,
    val friMinute: Long = 30L,
    val reminderEnabled: Boolean = true,
    val autoLoginEnabled: Boolean = true,
    val driveFolderName: String = "Dokumentasi MagangHub Kemnaker",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "monThuHour" to monThuHour,
            "monThuMinute" to monThuMinute,
            "friHour" to friHour,
            "friMinute" to friMinute,
            "reminderEnabled" to reminderEnabled,
            "autoLoginEnabled" to autoLoginEnabled,
            "driveFolderName" to driveFolderName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "monThuHour" to monThuHour,
            "monThuMinute" to monThuMinute,
            "friHour" to friHour,
            "friMinute" to friMinute,
            "reminderEnabled" to reminderEnabled,
            "autoLoginEnabled" to autoLoginEnabled,
            "driveFolderName" to driveFolderName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
