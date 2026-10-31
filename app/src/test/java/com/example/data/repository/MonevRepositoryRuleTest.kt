package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MonevRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAttendanceLog_validPayload_createsAndUpdatesDocument() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = MonevRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertAttendanceLog(
                dateKey = "2026-10-31",
                checkedOut = false,
                activitySummary = "Menyiapkan bahan monev magang",
                checkInTime = "08:00 WIB"
            )
        }
        assertTrue(createResult.isSuccess)
        val logId = createResult.getOrThrow()
        assertTrue(logId.isNotEmpty())

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertAttendanceLog(
                dateKey = "2026-10-31",
                checkedOut = true,
                activitySummary = "Selesai absen pulang dan upload dokumentasi ke Drive",
                checkOutTime = "16:05 WIB",
                driveFileId = "drive_123",
                driveWebViewLink = "https://drive.google.com/file/d/drive_123/view",
                driveFileName = "Dokumentasi_2026-10-31.jpg"
            )
        }
        assertTrue(updateResult.isSuccess)

        val logsResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getAttendanceLogs() }
        assertTrue(logsResult.isSuccess)
        val logs = logsResult.getOrThrow()
        assertTrue(logs.any { it.id == logId && it.checkedOut && it.driveFileId == "drive_123" })
    }

    @Test
    fun observeAttendanceLogs_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = MonevRepository(firestore)
        val randomDay = (10..28).random()
        val testDateKey = "2026-10-$randomDay"
        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertAttendanceLog(
                dateKey = testDateKey,
                checkedOut = true,
                activitySummary = "Absen harian Kamis"
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAttendanceLogs().first { list -> list.any { it.id == logId } }
        }
        assertTrue(emitted.any { it.id == logId })
    }

    @Test
    fun getAttendanceLogById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = MonevRepository(firestore)
        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertAttendanceLog(
                dateKey = "2026-10-29",
                checkedOut = true,
                activitySummary = "Laporan Alice"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MonevRepository(firestore)
        val bobResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getAttendanceLogById(logId)
        }
        assertTrue(bobResult.isFailure)
        val ex = bobResult.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull(ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeAttendanceLogs_unauthenticatedUser_fails() = runBlocking {
        auth.signOut()
        val repository = MonevRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAttendanceLogs().first()
            }
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            // Expected IllegalStateException from requireUserId() or PERMISSION_DENIED
            assertTrue(e is IllegalStateException || e.unwrapFirestoreException() != null)
        }
    }

    @Test
    fun saveUserSettings_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = MonevRepository(firestore)
        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserSettings(
                monThuHour = 16,
                monThuMinute = 0,
                friHour = 16,
                friMinute = 30,
                reminderEnabled = true,
                autoLoginEnabled = true,
                driveFolderName = "Dokumentasi MagangHub Kemnaker"
            )
        }
        assertTrue(saveResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
