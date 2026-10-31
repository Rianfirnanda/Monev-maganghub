package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.ColumnHelperState
import com.example.viewmodel.StructuredAttendanceDraft
import org.json.JSONObject

private const val DEFAULT_DASHBOARD_URL = "https://monev.maganghub.kemnaker.go.id/dashboard"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalWebViewScreen(
    todayDateKey: String,
    todayCheckedOut: Boolean,
    savedUsername: String,
    savedPassword: String,
    autoLoginEnabled: Boolean,
    columnHelperState: ColumnHelperState,
    onToggleCheckedOut: (Boolean) -> Unit,
    onSaveCredentials: (String, String, Boolean) -> Unit,
    onNavigateToUploadPhoto: () -> Unit,
    onAutoGenerateColumn: (String, (StructuredAttendanceDraft) -> Unit) -> Unit,
    onPolishColumnSentence: (String, (StructuredAttendanceDraft) -> Unit) -> Unit,
    onSaveGeneratedToAttendanceLog: (String) -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by rememberSaveable { mutableStateOf(DEFAULT_DASHBOARD_URL) }
    var pageTitle by rememberSaveable { mutableStateOf("Dashboard Monev MagangHub") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by rememberSaveable { mutableStateOf(false) }
    var showCredentialsDialog by rememberSaveable { mutableStateOf(false) }
    var showAiColumnDialog by rememberSaveable { mutableStateOf(false) }

    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        pendingFileCallback?.onReceiveValue(uris.toTypedArray())
        pendingFileCallback = null
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Status & Quick Action Bar
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (todayCheckedOut) EmeraldSuccess else AmberGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = pageTitle.ifBlank { "Monev MagangHub Kemnaker" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (todayCheckedOut) {
                                    "Status Hari Ini: Sudah Isi Absen Pulang"
                                } else {
                                    "Status Hari Ini: Belum Absen Pulang (Pengingat Aktif)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (todayCheckedOut) EmeraldSuccess else AmberGold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showCredentialsDialog = true },
                            modifier = Modifier.testTag("auto_login_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = "Pengaturan Login Otomatis",
                                tint = if (savedUsername.isNotBlank()) EmeraldSuccess else MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                webViewRef?.let { wv ->
                                    applyDesktopMode(wv.settings, isDesktopMode)
                                    wv.reload()
                                }
                            },
                            modifier = Modifier.testTag("toggle_desktop_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
                                contentDescription = "Ubah Mode Tampilan Desktop/Mobile"
                            )
                        }
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                                }
                            },
                            modifier = Modifier.testTag("open_external_browser_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInBrowser,
                                contentDescription = "Buka di Browser Eksternal"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick action chips for AI Column Filler, Attendance & Google Drive Upload
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { showAiColumnDialog = true },
                        label = {
                            Text(
                                text = "AI Isi & Perbaiki Kolom Absen",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.testTag("open_ai_column_helper_chip")
                    )

                    AssistChip(
                        onClick = { onToggleCheckedOut(!todayCheckedOut) },
                        label = {
                            Text(
                                text = if (todayCheckedOut) "Sudah Absen Pulang" else "Tandai Sudah Absen",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (todayCheckedOut) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = if (todayCheckedOut) EmeraldSuccess else AmberGold,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (todayCheckedOut) {
                                EmeraldSuccess.copy(alpha = 0.14f)
                            } else {
                                AmberGold.copy(alpha = 0.14f)
                            }
                        ),
                        modifier = Modifier.testTag("quick_toggle_attendance_chip")
                    )

                    AssistChip(
                        onClick = onNavigateToUploadPhoto,
                        label = {
                            Text(
                                text = "Upload Foto ke Drive",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("quick_upload_drive_chip")
                    )
                }
            }
        }

        AnimatedVisibility(visible = isLoading && loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        // Main Responsive WebView
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("monev_portal_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            javaScriptCanOpenWindowsAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                pendingFileCallback?.onReceiveValue(null)
                                pendingFileCallback = filePathCallback
                                runCatching {
                                    fileChooserLauncher.launch("*/*")
                                }.onFailure {
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = null
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                CookieManager.getInstance().flush()

                                if (view != null && autoLoginEnabled && savedUsername.isNotBlank() && savedPassword.isNotBlank()) {
                                    injectAutoLoginScript(view, savedUsername, savedPassword)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return false
                                val scheme = targetUri.scheme?.lowercase().orEmpty()
                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                }
                                return true
                            }
                        }

                        loadUrl(DEFAULT_DASHBOARD_URL)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                    canGoBack = wv.canGoBack()
                    canGoForward = wv.canGoForward()
                }
            )
        }

        // Bottom WebView Navigation Toolbar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.testTag("webview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.testTag("webview_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Maju"
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.testTag("webview_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Muat Ulang"
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.loadUrl(DEFAULT_DASHBOARD_URL) },
                        modifier = Modifier.testTag("webview_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Dashboard Utama"
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { showAiColumnDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("toolbar_ai_fill_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Kolom Absen",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val wv = webViewRef
                            if (wv != null && savedUsername.isNotBlank() && savedPassword.isNotBlank()) {
                                injectAutoLoginScript(wv, savedUsername, savedPassword, forceSubmit = false)
                            } else {
                                showCredentialsDialog = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("fill_login_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (savedUsername.isNotBlank()) "Auto Login" else "Simpan Login",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    if (showCredentialsDialog) {
        AutoLoginCredentialsDialog(
            initialUsername = savedUsername,
            initialPassword = savedPassword,
            initialAutoLogin = autoLoginEnabled,
            onDismiss = { showCredentialsDialog = false },
            onSave = { user, pass, auto ->
                onSaveCredentials(user, pass, auto)
                showCredentialsDialog = false
                webViewRef?.let { wv ->
                    if (user.isNotBlank() && pass.isNotBlank()) {
                        injectAutoLoginScript(wv, user, pass)
                    }
                }
            }
        )
    }

    if (showAiColumnDialog) {
        AiWebViewColumnAssistantDialog(
            todayDateKey = todayDateKey,
            columnHelperState = columnHelperState,
            onReadCurrentWebViewColumn = { onTextExtracted ->
                val wv = webViewRef
                if (wv != null) {
                    extractCurrentColumnTextFromWebView(wv, onTextExtracted)
                }
            },
            onAutoGenerate = { explanation, onResult ->
                onAutoGenerateColumn(explanation, onResult)
            },
            onPolishSentence = { rawSentence, onResult ->
                onPolishColumnSentence(rawSentence, onResult)
            },
            onInjectIntoWebView = { draft ->
                webViewRef?.let { wv ->
                    injectAttendanceFieldsIntoWebView(wv, draft)
                }
                copyToClipboard(context, draft.kalimatLengkapKolom)
                onSaveGeneratedToAttendanceLog(draft.kalimatLengkapKolom)
                showAiColumnDialog = false
            },
            onDismiss = { showAiColumnDialog = false }
        )
    }
}

@Composable
private fun AiWebViewColumnAssistantDialog(
    todayDateKey: String,
    columnHelperState: ColumnHelperState,
    onReadCurrentWebViewColumn: ((String) -> Unit) -> Unit,
    onAutoGenerate: (String, (StructuredAttendanceDraft) -> Unit) -> Unit,
    onPolishSentence: (String, (StructuredAttendanceDraft) -> Unit) -> Unit,
    onInjectIntoWebView: (StructuredAttendanceDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var userExplanation by rememberSaveable { mutableStateOf("") }
    var editedTitle by rememberSaveable { mutableStateOf("") }
    var editedDescription by rememberSaveable { mutableStateOf("") }
    var editedOutput by rememberSaveable { mutableStateOf("") }
    var editedFullText by rememberSaveable { mutableStateOf("") }

    val isLoading = columnHelperState is ColumnHelperState.Loading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Pengisi & Perbaikan Kolom Absen",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cukup ceritakan singkat kegiatan Anda hari ini ($todayDateKey), lalu biarkan AI menyusun kalimat laporan dan mengisi kolom absen di halaman WebView secara otomatis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        onReadCurrentWebViewColumn { extracted ->
                            if (extracted.isNotBlank()) {
                                userExplanation = extracted
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("read_webview_column_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ambil Teks dari Kolom Absen WebView Saat Ini")
                }

                OutlinedTextField(
                    value = userExplanation,
                    onValueChange = { userExplanation = it },
                    label = { Text("Penjelasan Singkat Kegiatan / Kalimat Draf") },
                    placeholder = {
                        Text("Contoh: hari ini saya rekap data excel peserta magang, bantu bikin surat undangan rapat, dan ikut evaluasi...")
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webview_ai_explanation_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onAutoGenerate(userExplanation) { draft ->
                                editedTitle = draft.judulKegiatan
                                editedDescription = draft.uraianKegiatan
                                editedOutput = draft.outputCapaian
                                editedFullText = draft.kalimatLengkapKolom
                            }
                        },
                        enabled = !isLoading && userExplanation.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("webview_ai_autogenerate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Generate")
                    }

                    FilledTonalButton(
                        onClick = {
                            onPolishSentence(userExplanation) { draft ->
                                editedTitle = draft.judulKegiatan
                                editedDescription = draft.uraianKegiatan
                                editedOutput = draft.outputCapaian
                                editedFullText = draft.kalimatLengkapKolom
                            }
                        },
                        enabled = !isLoading && userExplanation.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("webview_ai_polish_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Perbaiki Kalimat")
                    }
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AI sedang menyusun kalimat kolom absen...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (columnHelperState is ColumnHelperState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = columnHelperState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (editedFullText.isNotBlank() || columnHelperState is ColumnHelperState.Success) {
                    if (editedFullText.isBlank() && columnHelperState is ColumnHelperState.Success) {
                        editedTitle = columnHelperState.draft.judulKegiatan
                        editedDescription = columnHelperState.draft.uraianKegiatan
                        editedOutput = columnHelperState.draft.outputCapaian
                        editedFullText = columnHelperState.draft.kalimatLengkapKolom
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Hasil Susunan AI (Dapat Diedit Sebelum Diisi ke Kolom):",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                label = { Text("Judul / Topik Kegiatan") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editedDescription,
                                onValueChange = {
                                    editedDescription = it
                                    editedFullText = it
                                },
                                label = { Text("Uraian Kegiatan (Kalimat Diperbaiki)") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editedOutput,
                                onValueChange = { editedOutput = it },
                                label = { Text("Output / Capaian Kerja") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, editedFullText.ifBlank { editedDescription })
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salin ke Clipboard")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (editedFullText.isNotBlank() || editedDescription.isNotBlank()) {
                Button(
                    onClick = {
                        onInjectIntoWebView(
                            StructuredAttendanceDraft(
                                judulKegiatan = editedTitle,
                                uraianKegiatan = editedDescription,
                                outputCapaian = editedOutput,
                                kalimatLengkapKolom = editedFullText.ifBlank { editedDescription }
                            )
                        )
                    },
                    modifier = Modifier.testTag("inject_ai_to_webview_button")
                ) {
                    Text("Isi Otomatis ke Kolom Absen")
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

private fun extractCurrentColumnTextFromWebView(
    webView: WebView,
    onResult: (String) -> Unit
) {
    val js = """
        (function() {
            try {
                var active = document.activeElement;
                if (active && (active.tagName === 'TEXTAREA' || (active.tagName === 'INPUT' && active.type === 'text')) && active.value) {
                    return active.value;
                }
                var textareas = document.querySelectorAll('textarea');
                for (var i = 0; i < textareas.length; i++) {
                    if (textareas[i].value && textareas[i].value.trim().length > 0) {
                        return textareas[i].value;
                    }
                }
                var editables = document.querySelectorAll('[contenteditable="true"], .ql-editor, .ck-editor__editable');
                for (var j = 0; j < editables.length; j++) {
                    if (editables[j].innerText && editables[j].innerText.trim().length > 0) {
                        return editables[j].innerText;
                    }
                }
                return "";
            } catch (e) {
                return "";
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(js) { rawResult ->
        val unquoted = rawResult
            ?.removeSurrounding("\"")
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.takeIf { it != "null" }
            .orEmpty()
        onResult(unquoted)
    }
}

private fun injectAttendanceFieldsIntoWebView(
    webView: WebView,
    draft: StructuredAttendanceDraft
) {
    val safeJudul = JSONObject.quote(draft.judulKegiatan)
    val safeUraian = JSONObject.quote(draft.uraianKegiatan.ifBlank { draft.kalimatLengkapKolom })
    val safeOutput = JSONObject.quote(draft.outputCapaian)
    val safeFull = JSONObject.quote(draft.kalimatLengkapKolom)

    val js = """
        (function() {
            try {
                function setNativeValue(el, val) {
                    var proto = el.tagName === 'TEXTAREA'
                        ? window.HTMLTextAreaElement.prototype
                        : window.HTMLInputElement.prototype;
                    var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (descriptor && descriptor.set) {
                        descriptor.set.call(el, val);
                    } else {
                        el.value = val;
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }

                var filledAny = false;
                var active = document.activeElement;
                if (active && (active.tagName === 'TEXTAREA' || (active.tagName === 'INPUT' && active.type === 'text'))) {
                    setNativeValue(active, $safeFull);
                    filledAny = true;
                }

                var textareas = document.querySelectorAll('textarea');
                if (textareas.length === 1) {
                    setNativeValue(textareas[0], $safeFull);
                    filledAny = true;
                } else if (textareas.length > 1) {
                    setNativeValue(textareas[0], $safeUraian);
                    if ($safeOutput.length > 0) {
                        setNativeValue(textareas[1], $safeOutput);
                    }
                    filledAny = true;
                }

                var inputs = document.querySelectorAll('input[type="text"]');
                for (var i = 0; i < inputs.length; i++) {
                    var nameAttr = ((inputs[i].name || '') + ' ' + (inputs[i].id || '') + ' ' + (inputs[i].placeholder || '')).toLowerCase();
                    if (nameAttr.indexOf('judul') !== -1 || nameAttr.indexOf('topik') !== -1 || nameAttr.indexOf('kegiatan') !== -1 || nameAttr.indexOf('aktivitas') !== -1) {
                        setNativeValue(inputs[i], $safeJudul);
                        filledAny = true;
                    } else if (nameAttr.indexOf('output') !== -1 || nameAttr.indexOf('hasil') !== -1 || nameAttr.indexOf('capaian') !== -1) {
                        setNativeValue(inputs[i], $safeOutput);
                        filledAny = true;
                    }
                }

                var editables = document.querySelectorAll('[contenteditable="true"], .ql-editor, .ck-editor__editable');
                for (var j = 0; j < editables.length; j++) {
                    editables[j].innerText = $safeFull;
                    editables[j].dispatchEvent(new Event('input', { bubbles: true }));
                    filledAny = true;
                }
                return filledAny;
            } catch (e) {
                return false;
            }
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Isian Kolom Absen MagangHub", text))
}

private fun applyDesktopMode(settings: WebSettings, desktop: Boolean) {
    if (desktop) {
        settings.userAgentString =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    } else {
        settings.userAgentString = null
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    }
}

private fun injectAutoLoginScript(
    webView: WebView,
    username: String,
    password: String,
    forceSubmit: Boolean = false
) {
    val safeUser = JSONObject.quote(username)
    val safePass = JSONObject.quote(password)
    val js = """
        (function() {
            try {
                var userInputs = document.querySelectorAll('input[type="email"], input[name*="email" i], input[name*="username" i], input[name*="nik" i], input[id*="email" i], input[id*="username" i], input[type="text"]');
                var passInputs = document.querySelectorAll('input[type="password"]');
                if (passInputs.length > 0 && userInputs.length > 0) {
                    var u = userInputs[0];
                    var p = passInputs[0];
                    var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    if (u && !u.value) {
                        nativeInputValueSetter.call(u, $safeUser);
                        u.dispatchEvent(new Event('input', { bubbles: true }));
                        u.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    if (p && !p.value) {
                        nativeInputValueSetter.call(p, $safePass);
                        p.dispatchEvent(new Event('input', { bubbles: true }));
                        p.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    if ($forceSubmit) {
                        var btn = document.querySelector('button[type="submit"], input[type="submit"]');
                        if (btn) btn.click();
                    }
                }
            } catch (e) {}
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

@Composable
private fun AutoLoginCredentialsDialog(
    initialUsername: String,
    initialPassword: String,
    initialAutoLogin: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by rememberSaveable { mutableStateOf(initialPassword) }
    var autoLogin by rememberSaveable { mutableStateOf(initialAutoLogin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Simpan Login Otomatis Kemnaker",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Kredensial SiapKerja / Monev MagangHub Anda disimpan secara lokal di perangkat dan sesi cookies WebView dipertahankan secara otomatis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Email / NIK / No. HP SiapKerja") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portal_username_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kata Sandi") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portal_password_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Isi Otomatis Saat Halaman Login Terbuka",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoLogin,
                        onCheckedChange = { autoLogin = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, password, autoLogin) },
                modifier = Modifier.testTag("save_portal_credentials_button")
            ) {
                Text("Simpan & Aktifkan")
            }
        },
        dismissButton = {
            Row {
                if (initialUsername.isNotBlank() || initialPassword.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onSave("", "", false) }
                    ) {
                        Text("Hapus")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}
