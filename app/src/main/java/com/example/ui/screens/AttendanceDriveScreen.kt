package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.drive.GoogleDriveService
import com.example.data.model.AttendanceLog
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.DataUiState
import com.example.viewmodel.StructuredAttendanceDraft

@Composable
fun AttendanceDriveScreen(
    userEmail: String?,
    todayDateKey: String,
    todayCheckedOut: Boolean,
    driveConnected: Boolean,
    driveFolderName: String,
    isUploadingDrive: Boolean,
    logsState: DataUiState<List<AttendanceLog>>,
    isGeneratingAiDraft: Boolean,
    columnHelperError: String? = null,
    onConnectDriveToken: (String, Long) -> Unit,
    onDisconnectDrive: () -> Unit,
    onAutoGenerateFromExplanation: (String, (StructuredAttendanceDraft) -> Unit) -> Unit,
    onPolishExistingSentence: (String, (StructuredAttendanceDraft) -> Unit) -> Unit,
    onUploadAndSave: (Bitmap?, String?, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var briefExplanation by rememberSaveable { mutableStateOf("") }
    var activitySummary by rememberSaveable { mutableStateOf("") }
    var generatedOutputBadge by rememberSaveable { mutableStateOf("") }
    var markCheckedOutOnSave by rememberSaveable { mutableStateOf(true) }
    var showDriveOAuthDialog by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUriString = uri.toString()
            runCatching {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bmp
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            selectedBitmap = bmp
            selectedUriString = "camera_capture_${System.currentTimeMillis()}"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Header Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (todayCheckedOut) {
                            EmeraldSuccess.copy(alpha = 0.14f)
                        } else {
                            AmberGold.copy(alpha = 0.14f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (todayCheckedOut) EmeraldSuccess else AmberGold
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (todayCheckedOut) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Absensi & Dokumentasi Hari Ini ($todayDateKey)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (todayCheckedOut) {
                                    "Anda sudah menandai absen pulang hari ini. Pengingat pukul 16:00 / 16:30 WIB tidak akan berbunyi lagi hari ini."
                                } else {
                                    "Anda belum mengisi absen pulang hari ini. Pengingat otomatis akan berbunyi pukul 16:00 WIB (Senin–Kamis) atau 16:30 WIB (Jumat)."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                // Google Drive Connection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val folderHierarchy = remember(driveFolderName, todayDateKey) {
                            GoogleDriveService.buildDateFolderHierarchy(driveFolderName, todayDateKey)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (driveConnected) Icons.Filled.CloudDone else Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = if (driveConnected) EmeraldSuccess else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Integrasi Google Drive Otomatis",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Otomatis membuat folder Tanggal, Bulan & Tahun",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                color = if (driveConnected) {
                                    EmeraldSuccess.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (driveConnected) "Terhubung" else "Belum Terhubung",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (driveConnected) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Struktur Folder Otomatis Hari Ini:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "📁 ${folderHierarchy.rootFolder}\n  ↳ 📁 ${folderHierarchy.yearFolder}\n      ↳ 📁 ${folderHierarchy.monthFolder}\n          ↳ 📁 ${folderHierarchy.dateFolder}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { showDriveOAuthDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("connect_google_drive_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (driveConnected) "Perbarui Otorisasi Drive" else "Hubungkan Google Drive")
                            }

                            if (driveConnected) {
                                OutlinedButton(
                                    onClick = onDisconnectDrive,
                                    modifier = Modifier.testTag("disconnect_google_drive_button")
                                ) {
                                    Text("Putuskan")
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Upload Photo & Daily Documentation Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Upload Foto Dokumentasi & Rekap Harian",
                            style = MaterialTheme.typography.titleLarge
                        )

                        // Photo Selection Buttons (Zero-permission Photo Picker & Camera)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_photo_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih Galeri")
                            }

                            OutlinedButton(
                                onClick = { cameraLauncher.launch(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("capture_photo_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ambil Foto")
                            }
                        }

                        if (selectedBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    bitmap = selectedBitmap!!.asImageBitmap(),
                                    contentDescription = "Pratinjau foto dokumentasi hari ini",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // AI Column Auto-Generator & Sentence Polisher Card
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Asisten AI Pengisi & Perbaikan Kolom Absen",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Cukup ketik penjelasan singkat kegiatan Anda, lalu AI yang akan mengisi atau memperbaiki kalimat kolom absen di bawah.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = briefExplanation,
                                    onValueChange = { briefExplanation = it },
                                    label = { Text("Penjelasan Singkat Kegiatan Anda Hari Ini") },
                                    placeholder = {
                                        Text("Contoh: tadi pagi bantu input data peserta di excel, siang ikut zoom meeting monev sama mentor...")
                                    },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("brief_explanation_input")
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val sourceText = briefExplanation.ifBlank { activitySummary }
                                            onAutoGenerateFromExplanation(sourceText) { draft ->
                                                activitySummary = buildString {
                                                    append(draft.uraianKegiatan.ifBlank { draft.kalimatLengkapKolom })
                                                    if (draft.outputCapaian.isNotBlank()) {
                                                        append("\n\nOutput/Capaian: ${draft.outputCapaian}")
                                                    }
                                                }.take(1900)
                                                generatedOutputBadge = draft.judulKegiatan
                                            }
                                        },
                                        enabled = !isGeneratingAiDraft && (briefExplanation.isNotBlank() || activitySummary.isNotBlank()),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ai_autogenerate_column_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Auto-Generate Isi Kolom Absen")
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val targetToPolish = activitySummary.ifBlank { briefExplanation }
                                            onPolishExistingSentence(targetToPolish) { draft ->
                                                activitySummary = draft.uraianKegiatan
                                                    .ifBlank { draft.kalimatLengkapKolom }
                                                    .take(1900)
                                                generatedOutputBadge = draft.judulKegiatan
                                            }
                                        },
                                        enabled = !isGeneratingAiDraft && (activitySummary.isNotBlank() || briefExplanation.isNotBlank()),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("ai_polish_sentence_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Perbaiki Kalimat Kolom Absen")
                                    }
                                }

                                if (isGeneratingAiDraft) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "AI sedang menyusun & mengisi kolom absen untuk Anda...",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                if (!columnHelperError.isNullOrBlank()) {
                                    Text(
                                        text = columnHelperError,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (generatedOutputBadge.isNotBlank()) {
                            Surface(
                                color = EmeraldSuccess.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "✨ Topik Kegiatan Terdeteksi: $generatedOutputBadge",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = activitySummary,
                            onValueChange = { activitySummary = it },
                            label = { Text("Kolom Absen / Laporan Kegiatan Harian (Terisi Otomatis / Edit Manual)") },
                            placeholder = { Text("Kolom ini akan terisi otomatis setelah Anda menekan tombol AI di atas, atau ketik kalimat Anda untuk diperbaiki...") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("activity_summary_input")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = markCheckedOutOnSave,
                                onCheckedChange = { markCheckedOutOnSave = it },
                                modifier = Modifier.testTag("mark_checked_out_checkbox")
                            )
                            Text(
                                text = "Tandai saya sudah mengisi absen pulang hari ini ($todayDateKey)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Button(
                            onClick = {
                                onUploadAndSave(
                                    selectedBitmap,
                                    selectedUriString,
                                    activitySummary,
                                    markCheckedOutOnSave
                                )
                            },
                            enabled = !isUploadingDrive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("upload_and_save_log_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isUploadingDrive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Mengunggah ke Google Drive & Menyimpan...")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedBitmap != null && driveConnected) {
                                        "Upload Foto ke Google Drive & Simpan Absen"
                                    } else {
                                        "Simpan Rekap Absen & Dokumentasi Hari Ini"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Riwayat Absensi & Dokumentasi Google Drive",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            when (logsState) {
                is DataUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is DataUiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = logsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is DataUiState.Success -> {
                    if (logsState.data.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Belum ada riwayat absensi & dokumentasi harian.",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Unggah foto kegiatan magang hari ini untuk menyimpannya ke Google Drive Anda.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(logsState.data, key = { it.id }) { log ->
                            AttendanceLogCard(
                                log = log,
                                onOpenDriveLink = { url ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDriveOAuthDialog) {
        GoogleDriveOAuthDialog(
            userEmail = userEmail,
            onDismiss = { showDriveOAuthDialog = false },
            onTokenReceived = { token, expiresIn ->
                onConnectDriveToken(token, expiresIn)
                showDriveOAuthDialog = false
            }
        )
    }
}

@Composable
private fun AttendanceLogCard(
    log: AttendanceLog,
    onOpenDriveLink: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_log_card_${log.dateKey}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tanggal: ${log.dateKey}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (log.checkedOut) {
                        EmeraldSuccess.copy(alpha = 0.15f)
                    } else {
                        AmberGold.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (log.checkedOut) {
                            "Sudah Absen ${log.checkOutTime ?: ""}".trim()
                        } else {
                            "Belum Absen Pulang"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (log.checkedOut) EmeraldSuccess else AmberGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = log.activitySummary,
                style = MaterialTheme.typography.bodyMedium
            )

            if (!log.driveWebViewLink.isNullOrBlank()) {
                FilledTonalButton(
                    onClick = { onOpenDriveLink(log.driveWebViewLink) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.driveFileName ?: "Lihat Foto Dokumentasi di Google Drive",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleDriveOAuthDialog(
    userEmail: String?,
    onDismiss: () -> Unit,
    onTokenReceived: (String, Long) -> Unit
) {
    val context = LocalContext.current
    var manualToken by rememberSaveable { mutableStateOf("") }
    var showEmbeddedOAuth by rememberSaveable { mutableStateOf(true) }
    val clientId = remember {
        runCatching { context.getString(com.example.R.string.default_web_client_id) }.getOrDefault("")
    }
    val oauthUrl = remember(clientId, userEmail) {
        GoogleDriveService.buildDriveOAuthUrl(clientId, userEmail)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Otorisasi Google Drive (drive.file)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Setujui akses Google Drive agar aplikasi dapat membuat folder dan mengunggah foto dokumentasi harian Anda secara otomatis.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (showEmbeddedOAuth) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    // Clean User-Agent so Google OAuth page renders cleanly
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val urlStr = request?.url?.toString().orEmpty()
                                            if (urlStr.contains("access_token=")) {
                                                val token = extractAccessTokenFromUrl(urlStr)
                                                val expires = extractExpiresInFromUrl(urlStr)
                                                if (!token.isNullOrBlank()) {
                                                    onTokenReceived(token, expires)
                                                    return true
                                                }
                                            }
                                            return false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val urlStr = url.orEmpty()
                                            if (urlStr.contains("access_token=")) {
                                                val token = extractAccessTokenFromUrl(urlStr)
                                                val expires = extractExpiresInFromUrl(urlStr)
                                                if (!token.isNullOrBlank()) {
                                                    onTokenReceived(token, expires)
                                                }
                                            }
                                        }
                                    }
                                    loadUrl(oauthUrl)
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl)))
                            }
                        }
                    ) {
                        Text("Buka OAuth di Browser")
                    }
                    TextButton(
                        onClick = { showEmbeddedOAuth = !showEmbeddedOAuth }
                    ) {
                        Text(if (showEmbeddedOAuth) "Input Token Manual" else "Tampilkan Layar Login")
                    }
                }

                if (!showEmbeddedOAuth) {
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        label = { Text("OAuth 2.0 Access Token (ya29...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_manual_token_input")
                    )
                }
            }
        },
        confirmButton = {
            if (!showEmbeddedOAuth) {
                Button(
                    onClick = {
                        if (manualToken.isNotBlank()) {
                            onTokenReceived(manualToken.trim(), 3600L)
                        }
                    },
                    enabled = manualToken.isNotBlank()
                ) {
                    Text("Simpan Token")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

private fun extractAccessTokenFromUrl(url: String): String? {
    val fragment = url.substringAfter("#", "")
    val query = url.substringAfter("?", "")
    val combined = "$fragment&$query"
    return combined.split("&")
        .firstOrNull { it.startsWith("access_token=") }
        ?.substringAfter("access_token=")
        ?.takeIf { it.isNotBlank() }
}

private fun extractExpiresInFromUrl(url: String): Long {
    val fragment = url.substringAfter("#", "")
    return fragment.split("&")
        .firstOrNull { it.startsWith("expires_in=") }
        ?.substringAfter("expires_in=")
        ?.toLongOrNull() ?: 3600L
}
