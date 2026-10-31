package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOut
import com.example.data.preferences.LocalPrefsManager
import com.example.data.repository.MonevRepository
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AttendanceDriveScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PortalWebViewScreen
import com.example.ui.screens.ReminderSettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AiAssistantViewModel
import com.example.viewmodel.AiUiState
import com.example.viewmodel.ColumnHelperState
import com.example.viewmodel.MonevViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class MainDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    PORTAL(
        labelRes = R.string.nav_portal,
        selectedIcon = Icons.Filled.Web,
        unselectedIcon = Icons.Outlined.Web,
        testTag = "nav_tab_portal"
    ),
    ABSENSI_DRIVE(
        labelRes = R.string.nav_absensi,
        selectedIcon = Icons.Filled.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload,
        testTag = "nav_tab_absensi"
    ),
    PENGINGAT(
        labelRes = R.string.nav_pengingat,
        selectedIcon = Icons.Filled.AccessAlarm,
        unselectedIcon = Icons.Outlined.AccessAlarm,
        testTag = "nav_tab_pengingat"
    ),
    ASISTEN_AI(
        labelRes = R.string.nav_asisten,
        selectedIcon = Icons.Filled.Psychology,
        unselectedIcon = Icons.Outlined.Psychology,
        testTag = "nav_tab_asisten"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* AuthStateListener updates currentUser automatically */ }
        )
    } else {
        AuthenticatedAppContent(
            currentUserId = user.uid,
            userEmail = user.email
        )
    }
}

@Composable
fun AuthenticatedAppContent(
    currentUserId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val monevViewModel: MonevViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val prefs = LocalPrefsManager(app)
                MonevViewModel(
                    repository = MonevRepository(db),
                    prefsManager = prefs,
                    appContext = app
                )
            }
        }
    )

    val aiViewModel: AiAssistantViewModel = viewModel(key = "ai_$currentUserId")

    // Request POST_NOTIFICATIONS permission on Android 13+ on first authenticated launch
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Scheduled alarm will now be allowed to display notifications */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(MainDestination.PORTAL) }

    BackHandler(enabled = currentTab != MainDestination.PORTAL) {
        currentTab = MainDestination.PORTAL
    }

    val logsState by monevViewModel.attendanceLogsState.collectAsStateWithLifecycle()
    val todayCheckedOut by monevViewModel.todayCheckedOut.collectAsStateWithLifecycle()
    val portalUsername by monevViewModel.portalUsername.collectAsStateWithLifecycle()
    val portalPassword by monevViewModel.portalPassword.collectAsStateWithLifecycle()
    val autoLoginEnabled by monevViewModel.autoLoginEnabled.collectAsStateWithLifecycle()
    val reminderEnabled by monevViewModel.reminderEnabled.collectAsStateWithLifecycle()
    val monThuHour by monevViewModel.monThuHour.collectAsStateWithLifecycle()
    val monThuMinute by monevViewModel.monThuMinute.collectAsStateWithLifecycle()
    val friHour by monevViewModel.friHour.collectAsStateWithLifecycle()
    val friMinute by monevViewModel.friMinute.collectAsStateWithLifecycle()
    val driveFolderName by monevViewModel.driveFolderName.collectAsStateWithLifecycle()
    val driveConnected by monevViewModel.driveConnected.collectAsStateWithLifecycle()
    val nextAlarmDisplay by monevViewModel.nextAlarmDisplay.collectAsStateWithLifecycle()
    val statusBannerMessage by monevViewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isUploadingDrive by monevViewModel.isUploadingDrive.collectAsStateWithLifecycle()

    val aiMode by aiViewModel.selectedMode.collectAsStateWithLifecycle()
    val aiUiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val columnHelperState by aiViewModel.columnHelperState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBannerMessage) {
        val msg = statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            monevViewModel.clearBannerMessage()
        }
    }

    val todayKey = remember { LocalPrefsManager(context).getTodayDateKeyWib() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar {
                        MainDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(id = dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        MainDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(id = dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentTab) {
                        MainDestination.PORTAL -> {
                            PortalWebViewScreen(
                                todayDateKey = todayKey,
                                todayCheckedOut = todayCheckedOut,
                                savedUsername = portalUsername,
                                savedPassword = portalPassword,
                                autoLoginEnabled = autoLoginEnabled,
                                columnHelperState = columnHelperState,
                                onToggleCheckedOut = { checked ->
                                    monevViewModel.toggleTodayCheckedOut(checked)
                                },
                                onSaveCredentials = { user, pass, auto ->
                                    monevViewModel.savePortalCredentials(user, pass, auto)
                                },
                                onNavigateToUploadPhoto = {
                                    currentTab = MainDestination.ABSENSI_DRIVE
                                },
                                onAutoGenerateColumn = { briefExplanation, onReady ->
                                    aiViewModel.autoGenerateAttendanceColumn(
                                        briefExplanation = briefExplanation,
                                        dateKey = todayKey,
                                        onDraftReady = onReady
                                    )
                                },
                                onPolishColumnSentence = { rawDraft, onReady ->
                                    aiViewModel.polishAttendanceSentence(
                                        rawText = rawDraft,
                                        dateKey = todayKey,
                                        onDraftReady = onReady
                                    )
                                },
                                onSaveGeneratedToAttendanceLog = { summaryText ->
                                    monevViewModel.uploadDailyPhotoAndSaveLog(
                                        bitmap = null,
                                        localUriString = null,
                                        activitySummary = summaryText,
                                        markCheckedOut = false
                                    )
                                }
                            )
                        }
                        MainDestination.ABSENSI_DRIVE -> {
                            AttendanceDriveScreen(
                                userEmail = userEmail,
                                todayDateKey = todayKey,
                                todayCheckedOut = todayCheckedOut,
                                driveConnected = driveConnected,
                                driveFolderName = driveFolderName,
                                isUploadingDrive = isUploadingDrive,
                                logsState = logsState,
                                isGeneratingAiDraft = columnHelperState is ColumnHelperState.Loading ||
                                    aiUiState is AiUiState.Loading ||
                                    aiUiState is AiUiState.Streaming,
                                columnHelperError = (columnHelperState as? ColumnHelperState.Error)?.message,
                                onConnectDriveToken = { token, expiresIn ->
                                    monevViewModel.saveDriveAccessToken(token, expiresIn)
                                },
                                onDisconnectDrive = {
                                    monevViewModel.disconnectDrive()
                                },
                                onAutoGenerateFromExplanation = { briefExplanation, onReady ->
                                    aiViewModel.autoGenerateAttendanceColumn(
                                        briefExplanation = briefExplanation,
                                        dateKey = todayKey,
                                        onDraftReady = onReady
                                    )
                                },
                                onPolishExistingSentence = { rawDraft, onReady ->
                                    aiViewModel.polishAttendanceSentence(
                                        rawText = rawDraft,
                                        dateKey = todayKey,
                                        onDraftReady = onReady
                                    )
                                },
                                onUploadAndSave = { bmp, uriStr, summary, markDone ->
                                    monevViewModel.uploadDailyPhotoAndSaveLog(
                                        bitmap = bmp,
                                        localUriString = uriStr,
                                        activitySummary = summary,
                                        markCheckedOut = markDone
                                    )
                                }
                            )
                        }
                        MainDestination.PENGINGAT -> {
                            ReminderSettingsScreen(
                                userEmail = userEmail,
                                reminderEnabled = reminderEnabled,
                                monThuHour = monThuHour,
                                monThuMinute = monThuMinute,
                                friHour = friHour,
                                friMinute = friMinute,
                                autoLoginEnabled = autoLoginEnabled,
                                driveFolderName = driveFolderName,
                                nextAlarmDisplay = nextAlarmDisplay,
                                onSaveSettings = { mth, mtm, fh, fm, rem, auto, folder ->
                                    monevViewModel.updateReminderAndSettings(
                                        monThuHour = mth,
                                        monThuMinute = mtm,
                                        friHour = fh,
                                        friMinute = fm,
                                        reminderEnabled = rem,
                                        autoLoginEnabled = auto,
                                        driveFolderName = folder
                                    )
                                },
                                onTestNotification = {
                                    monevViewModel.triggerTestNotification()
                                },
                                onSignOut = {
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = scope
                                    )
                                }
                            )
                        }
                        MainDestination.ASISTEN_AI -> {
                            AiAssistantScreen(
                                selectedMode = aiMode,
                                uiState = aiUiState,
                                onSelectMode = { aiViewModel.setMode(it) },
                                onRunQuery = { aiViewModel.runQuery(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
