package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AiMode
import com.example.viewmodel.AiUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    selectedMode: AiMode,
    uiState: AiUiState,
    onSelectMode: (AiMode) -> Unit,
    onRunQuery: (String) -> Unit
) {
    val context = LocalContext.current
    var promptInput by rememberSaveable { mutableStateOf("") }

    val quickSuggestions = if (selectedMode == AiMode.DEEP_THINKING) {
        listOf(
            "Buatkan struktur laporan bulanan Monev MagangHub Kemnaker yang lengkap beserta indikator capaian kompetensi",
            "Susun narasi refleksi kegiatan harian magang di bidang administrasi & analisis data",
            "Bagaimana cara merangkum kendala teknis dan solusi selama magang untuk evaluasi mentor?"
        )
    } else {
        listOf(
            "Apa ketentuan terbaru program MagangHub Kemnaker RI dan manfaat bagi peserta?",
            "Kapan jadwal libur nasional dan cuti bersama tahun ini di Indonesia?",
            "Tips hak dan kewajiban peserta pemagangan dalam negeri menurut regulasi Kemnaker"
        )
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
                        Text(
                            text = "Asisten Pintar MagangHub",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = selectedMode == AiMode.DEEP_THINKING,
                                onClick = { onSelectMode(AiMode.DEEP_THINKING) },
                                label = { Text("Deep Thinking (Laporan)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mode_deep_thinking_chip")
                            )

                            FilterChip(
                                selected = selectedMode == AiMode.GOOGLE_SEARCH,
                                onClick = { onSelectMode(AiMode.GOOGLE_SEARCH) },
                                label = { Text("Google Search (Info)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mode_google_search_chip")
                            )
                        }

                        Text(
                            text = if (selectedMode == AiMode.DEEP_THINKING) {
                                "Mode Deep Thinking (gemini-4-argon • ThinkingLevel.HIGH): Analisis mendalam untuk menyusun laporan harian/bulanan Monev MagangHub dan pemecahan masalah kompleks."
                            } else {
                                "Mode Google Search Grounding (gemini-3.8-flash): Cari informasi terkini seputar regulasi Kemnaker, jadwal libur nasional, dan referensi industri."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        promptInput = suggestion
                                        onRunQuery(suggestion)
                                    },
                                    label = {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = promptInput,
                            onValueChange = { promptInput = it },
                            label = {
                                Text(
                                    if (selectedMode == AiMode.DEEP_THINKING) {
                                        "Tulis topik laporan magang atau masalah yang ingin dianalisis..."
                                    } else {
                                        "Cari informasi Kemnaker, regulasi, atau jadwal terkini..."
                                    }
                                )
                            },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_prompt_input")
                        )

                        Button(
                            onClick = { onRunQuery(promptInput) },
                            enabled = promptInput.isNotBlank() && uiState !is AiUiState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_submit_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedMode == AiMode.DEEP_THINKING) {
                                    "Analisis dengan High Thinking"
                                } else {
                                    "Cari dengan Google Search"
                                }
                            )
                        }
                    }
                }
            }

            when (uiState) {
                is AiUiState.Idle -> Unit
                is AiUiState.Loading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (selectedMode == AiMode.DEEP_THINKING) {
                                        "Sedang berpikir mendalam (ThinkingLevel.HIGH)..."
                                    } else {
                                        "Menelusuri informasi terkini dengan Google Search..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                is AiUiState.Streaming -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Hasil Analisis (Sedang menyusun...)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.text,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                is AiUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }
                is AiUiState.Success -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
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
                                Text(
                                    text = "Hasil Respons AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = uiState.text,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                if (uiState.sources.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = "Sumber Referensi Google Search:",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    uiState.sources.forEach { src ->
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(src.uri))
                                                    )
                                                }
                                            },
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
                                                text = src.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
