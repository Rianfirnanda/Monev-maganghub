package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AttendanceLog
import com.example.data.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MonevRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun buildLogId(userId: String, dateKey: String): String {
        return "${userId}_${dateKey}"
    }

    fun observeAttendanceLogs(): Flow<List<AttendanceLog>> = flow {
        val uid = requireUserId()
        val path = "attendance_logs"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AttendanceLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.dateKey }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getAttendanceLogs(): Result<List<AttendanceLog>> {
        val uid = requireUserId()
        val path = "attendance_logs"
        return try {
            val snapshot = db.collection(path)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val logs = snapshot.toObjects(
                AttendanceLog::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.dateKey }
            Result.success(logs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceLogById(logId: String): Result<AttendanceLog?> {
        val path = "attendance_logs/$logId"
        return try {
            val doc = db.collection("attendance_logs").document(logId).get().await()
            Result.success(
                doc.toObject(
                    AttendanceLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun upsertAttendanceLog(
        dateKey: String,
        checkedOut: Boolean,
        activitySummary: String,
        checkInTime: String? = null,
        checkOutTime: String? = null,
        driveFileId: String? = null,
        driveWebViewLink: String? = null,
        driveFileName: String? = null,
        localPhotoUri: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val logId = buildLogId(uid, dateKey)
        val docRef = db.collection("attendance_logs").document(logId)
        return try {
            val existingQuery = db.collection("attendance_logs")
                .whereEqualTo("userId", uid)
                .whereEqualTo("dateKey", dateKey)
                .limit(1)
                .get()
                .await()
            val existingSnap = existingQuery.documents.firstOrNull()
            if (existingSnap == null || !existingSnap.exists()) {
                val newLog = AttendanceLog(
                    id = logId,
                    userId = uid,
                    dateKey = dateKey,
                    checkedOut = checkedOut,
                    checkInTime = checkInTime,
                    checkOutTime = checkOutTime,
                    activitySummary = activitySummary,
                    driveFileId = driveFileId,
                    driveWebViewLink = driveWebViewLink,
                    driveFileName = driveFileName,
                    localPhotoUri = localPhotoUri
                )
                docRef.set(newLog.toCreateMap()).await()
            } else {
                val existing = existingSnap.toObject(
                    AttendanceLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val updated = AttendanceLog(
                    id = logId,
                    userId = uid,
                    dateKey = dateKey,
                    checkedOut = checkedOut,
                    checkInTime = checkInTime ?: existing?.checkInTime,
                    checkOutTime = checkOutTime ?: existing?.checkOutTime,
                    activitySummary = activitySummary,
                    driveFileId = driveFileId ?: existing?.driveFileId,
                    driveWebViewLink = driveWebViewLink ?: existing?.driveWebViewLink,
                    driveFileName = driveFileName ?: existing?.driveFileName,
                    localPhotoUri = localPhotoUri ?: existing?.localPhotoUri
                )
                docRef.update(updated.toUpdateMap()).await()
            }
            Result.success(logId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserSettings(): Flow<UserSettings?> = flow {
        val uid = requireUserId()
        val path = "user_settings/$uid"
        emitAll(
            db.collection("user_settings").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserSettings(
        monThuHour: Int,
        monThuMinute: Int,
        friHour: Int,
        friMinute: Int,
        reminderEnabled: Boolean,
        autoLoginEnabled: Boolean,
        driveFolderName: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("user_settings").document(uid)
        return try {
            val existing = docRef.get().await()
            val settings = UserSettings(
                id = uid,
                userId = uid,
                monThuHour = monThuHour.toLong(),
                monThuMinute = monThuMinute.toLong(),
                friHour = friHour.toLong(),
                friMinute = friMinute.toLong(),
                reminderEnabled = reminderEnabled,
                autoLoginEnabled = autoLoginEnabled,
                driveFolderName = driveFolderName.trim().ifEmpty { "Dokumentasi MagangHub Kemnaker" }
            )
            if (!existing.exists()) {
                docRef.set(settings.toCreateMap()).await()
            } else {
                docRef.update(settings.toUpdateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
