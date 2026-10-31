package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldSuccess

@Composable
fun ReminderSettingsScreen(
    userEmail: String?,
    reminderEnabled: Boolean,
    monThuHour: Int,
    monThuMinute: Int,
    friHour: Int,
    friMinute: Int,
    autoLoginEnabled: Boolean,
    driveFolderName: String,
    nextAlarmDisplay: String,
    onSaveSettings: (Int, Int, Int, Int, Boolean, Boolean, String) -> Unit,
    onTestNotification: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    var localReminderEnabled by rememberSaveable(reminderEnabled) { mutableStateOf(reminderEnabled) }
    var localMonThuHour by rememberSaveable(monThuHour) { mutableIntStateOf(monThuHour) }
    var localMonThuMinute by rememberSaveable(monThuMinute) { mutableIntStateOf(monThuMinute) }
    var localFriHour by rememberSaveable(friHour) { mutableIntStateOf(friHour) }
    var localFriMinute by rememberSaveable(friMinute) { mutableIntStateOf(friMinute) }
    var localAutoLogin by rememberSaveable(autoLoginEnabled) { mutableStateOf(autoLoginEnabled) }
    var localFolderName by rememberSaveable(driveFolderName) { mutableStateOf(driveFolderName) }

    var hasNotifPermission by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            onTestNotification()
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AccessAlarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Jadwal Pengingat Jam Pulang (WIB)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = if (localReminderEnabled) {
                                "Alarm berikutnya: $nextAlarmDisplay"
                            } else {
                                "Pengingat absensi harian sedang dinonaktifkan."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aktifkan Notifikasi Push Lokal Harian",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Berbunyi hanya jika Anda belum menandai absen pulang pada hari tersebut.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = localReminderEnabled,
                                onCheckedChange = { localReminderEnabled = it },
                                modifier = Modifier.testTag("reminder_enabled_switch")
                            )
                        }

                        HorizontalDivider()

                        // Monday - Thursday Schedule (Default 16:00 WIB)
                        TimeAdjusterRow(
                            label = "Jam Pulang Senin – Kamis",
                            subtitle = "Default: 16:00 WIB",
                            hour = localMonThuHour,
                            minute = localMonThuMinute,
                            onHourChange = { localMonThuHour = it },
                            onMinuteChange = { localMonThuMinute = it }
                        )

                        HorizontalDivider()

                        // Friday Schedule (Default 16:30 WIB)
                        TimeAdjusterRow(
                            label = "Jam Pulang Hari Jumat",
                            subtitle = "Default: 16:30 WIB",
                            hour = localFriHour,
                            minute = localFriMinute,
                            onHourChange = { localFriHour = it },
                            onMinuteChange = { localFriMinute = it }
                        )

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Login Otomatis Portal Monev",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Simpan cookies sesi & isi otomatis form login Kemnaker.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = localAutoLogin,
                                onCheckedChange = { localAutoLogin = it },
                                modifier = Modifier.testTag("auto_login_switch")
                            )
                        }

                        OutlinedTextField(
                            value = localFolderName,
                            onValueChange = { localFolderName = it },
                            label = { Text("Nama Folder Dokumentasi di Google Drive") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drive_folder_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onTestNotification()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_notification_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tes Notifikasi")
                            }

                            Button(
                                onClick = {
                                    onSaveSettings(
                                        localMonThuHour,
                                        localMonThuMinute,
                                        localFriHour,
                                        localFriMinute,
                                        localReminderEnabled,
                                        localAutoLogin,
                                        localFolderName
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_reminder_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan Jadwal")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Akun Google Aktif",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail ?: "Pengguna Terautentikasi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keluar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeAdjusterRow(
    label: String,
    subtitle: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Hour stepper
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onHourChange((hour + 23) % 24) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Kurangi Jam",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "%02d".format(hour),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onHourChange((hour + 1) % 24) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Tambah Jam",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = ":",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Minute stepper (steps of 5 minutes)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onMinuteChange((minute + 55) % 60) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Kurangi Menit",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "%02d".format(minute),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onMinuteChange((minute + 5) % 60) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Tambah Menit",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = "WIB",
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldSuccess,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
