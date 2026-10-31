package com.example.data.drive

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DriveFolderHierarchy(
    val rootFolder: String,
    val yearFolder: String,
    val monthFolder: String,
    val dateFolder: String
) {
    val fullPathDisplay: String
        get() = "$rootFolder / $yearFolder / $monthFolder / $dateFolder"
}

data class DriveUploadResult(
    val fileId: String,
    val fileName: String,
    val webViewLink: String,
    val folderPath: String
)

class GoogleDriveService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadPhotoToDrive(
        accessToken: String,
        bitmap: Bitmap,
        fileName: String,
        description: String,
        rootFolderName: String,
        dateKey: String
    ): Result<DriveUploadResult> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Token akses Google Drive belum tersedia. Silakan hubungkan Google Drive terlebih dahulu.")
                )
            }

            val hierarchy = buildDateFolderHierarchy(rootFolderName, dateKey)

            // 1. Root folder (e.g., "Dokumentasi MagangHub Kemnaker")
            val rootFolderId = findOrCreateFolder(
                accessToken = accessToken,
                folderName = hierarchy.rootFolder,
                parentId = null
            )
            // 2. Year folder (e.g., "Tahun 2026")
            val yearFolderId = if (rootFolderId != null) {
                findOrCreateFolder(
                    accessToken = accessToken,
                    folderName = hierarchy.yearFolder,
                    parentId = rootFolderId
                )
            } else null

            // 3. Month & Year folder (e.g., "10 - Oktober 2026")
            val monthFolderId = if (yearFolderId != null) {
                findOrCreateFolder(
                    accessToken = accessToken,
                    folderName = hierarchy.monthFolder,
                    parentId = yearFolderId
                )
            } else null

            // 4. Daily Attendance Date folder (e.g., "31 Oktober 2026")
            val targetDateFolderId = if (monthFolderId != null) {
                findOrCreateFolder(
                    accessToken = accessToken,
                    folderName = hierarchy.dateFolder,
                    parentId = monthFolderId
                )
            } else {
                monthFolderId ?: yearFolderId ?: rootFolderId
            }

            val imageBytes = ByteArrayOutputStream().use { bos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bos)
                bos.toByteArray()
            }

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("mimeType", "image/jpeg")
                put("description", "$description (Folder: ${hierarchy.fullPathDisplay})")
                if (!targetDateFolderId.isNullOrBlank()) {
                    put("parents", JSONArray().put(targetDateFolderId))
                }
            }.toString()

            val boundary = "MonevMagangHubBoundary_${System.currentTimeMillis()}"
            val bodyStream = ByteArrayOutputStream()
            val writer = bodyStream.writer(Charsets.UTF_8)

            writer.write("--$boundary\r\n")
            writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            writer.write("$metadataJson\r\n")
            writer.write("--$boundary\r\n")
            writer.write("Content-Type: image/jpeg\r\n\r\n")
            writer.flush()

            bodyStream.write(imageBytes)

            writer.write("\r\n--$boundary--\r\n")
            writer.flush()

            val multipartBytes = bodyStream.toByteArray()
            val mediaType = "multipart/related; boundary=$boundary".toMediaType()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink")
                .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                .post(multipartBytes.toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(respBody).optJSONObject("error")?.optString("message")
                            ?: "HTTP ${response.code}: $respBody"
                    } catch (e: Exception) {
                        "HTTP ${response.code}: $respBody"
                    }
                    return@withContext Result.failure(
                        IllegalStateException("Gagal mengunggah ke Google Drive: $errorMsg")
                    )
                }

                val json = JSONObject(respBody)
                val id = json.getString("id")
                val name = json.optString("name", fileName)
                val link = json.optString(
                    "webViewLink",
                    "https://drive.google.com/file/d/$id/view"
                )
                Result.success(
                    DriveUploadResult(
                        fileId = id,
                        fileName = name,
                        webViewLink = link,
                        folderPath = hierarchy.fullPathDisplay
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentId: String?
    ): String? {
        return try {
            val safeName = folderName.replace("'", "\\'")
            val parentFilter = if (!parentId.isNullOrBlank()) {
                " and '$parentId' in parents"
            } else {
                ""
            }
            val query = "mimeType='application/vnd.google-apps.folder' and name='$safeName'$parentFilter and trashed=false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name)&spaces=drive")
                .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                .get()
                .build()

            client.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val files = JSONObject(body).optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return files.getJSONObject(0).optString("id")
                    }
                }
            }

            // Create folder if not found
            val createJson = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                if (!parentId.isNullOrBlank()) {
                    put("parents", JSONArray().put(parentId))
                }
            }.toString()

            val createReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id")
                .addHeader("Authorization", "Bearer ${accessToken.trim()}")
                .post(createJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            client.newCall(createReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    return JSONObject(body).optString("id").takeIf { it.isNotBlank() }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val REDIRECT_URI = "https://ais-dev-vkzl7hs5e5yymqwxv3cjev-142486990060.asia-east1.run.app"

        /**
         * Builds the automatic folder structure by Date, Month, and Year of the internship attendance:
         * Root Folder -> Tahun YYYY -> MM - Bulan YYYY -> Absen DD Bulan YYYY
         */
        fun buildDateFolderHierarchy(rootFolderName: String, dateKey: String): DriveFolderHierarchy {
            val cleanRoot = rootFolderName.trim().ifEmpty { "Dokumentasi MagangHub Kemnaker" }
            val idLocale = Locale.forLanguageTag("id-ID")
            val wibZone = TimeZone.getTimeZone("Asia/Jakarta")

            val parsedDate: Date = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = wibZone
                }.parse(dateKey) ?: Date()
            } catch (e: Exception) {
                Date()
            }

            val yearStr = SimpleDateFormat("yyyy", idLocale).apply { timeZone = wibZone }.format(parsedDate)
            val monthNumAndName = SimpleDateFormat("MM - MMMM yyyy", idLocale).apply { timeZone = wibZone }.format(parsedDate)
            val fullDateIndonesian = SimpleDateFormat("dd MMMM yyyy", idLocale).apply { timeZone = wibZone }.format(parsedDate)

            return DriveFolderHierarchy(
                rootFolder = cleanRoot,
                yearFolder = "Tahun $yearStr",
                monthFolder = monthNumAndName,
                dateFolder = "Absen $fullDateIndonesian"
            )
        }

        fun buildDriveOAuthUrl(clientId: String, loginHintEmail: String?): String {
            val encodedScope = URLEncoder.encode(DRIVE_FILE_SCOPE, "UTF-8")
            val encodedRedirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
            val hintParam = if (!loginHintEmail.isNullOrBlank()) {
                "&login_hint=${URLEncoder.encode(loginHintEmail, "UTF-8")}"
            } else {
                ""
            }
            return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=$clientId" +
                "&redirect_uri=$encodedRedirect" +
                "&response_type=token" +
                "&scope=$encodedScope" +
                "&include_granted_scopes=true" +
                hintParam
        }
    }
}
