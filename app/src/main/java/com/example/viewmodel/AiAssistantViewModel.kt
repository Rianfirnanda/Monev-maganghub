package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GroundingSource(
    val title: String,
    val uri: String,
    val domain: String
)

data class StructuredAttendanceDraft(
    val judulKegiatan: String,
    val uraianKegiatan: String,
    val outputCapaian: String,
    val kalimatLengkapKolom: String
)

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Success(
        val text: String,
        val sources: List<GroundingSource> = emptyList(),
        val queries: List<String> = emptyList()
    ) : AiUiState
    data class Error(val message: String) : AiUiState
}

sealed interface ColumnHelperState {
    data object Idle : ColumnHelperState
    data object Loading : ColumnHelperState
    data class Success(val draft: StructuredAttendanceDraft) : ColumnHelperState
    data class Error(val message: String) : ColumnHelperState
}

enum class AiMode {
    DEEP_THINKING,
    GOOGLE_SEARCH
}

class AiAssistantViewModel : ViewModel() {

    private val attendanceColumnSchema = Schema.obj(
        properties = mapOf(
            "judulKegiatan" to Schema.string(
                description = "Judul singkat dan padat kegiatan magang hari ini (maksimal 12 kata)"
            ),
            "uraianKegiatan" to Schema.string(
                description = "Deskripsi kegiatan harian yang sudah diperbaiki kalimatnya secara profesional, rapi, dan sesuai EYD untuk kolom uraian absen Monev MagangHub"
            ),
            "outputCapaian" to Schema.string(
                description = "Hasil kerja atau output konkret yang dicapai dari kegiatan tersebut"
            ),
            "kalimatLengkapKolom" to Schema.string(
                description = "Paragraf gabungan siap pakai untuk langsung dimasukkan ke kolom absen/log harian magang"
            )
        )
    )

    // 1. Deep Thinking Model: gemini-4-argon with ThinkingLevel.HIGH (no maxOutputTokens)
    private val thinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    // 2. Structured Column Auto-Fill & Grammar Polisher Model (gemini-4-argon + ThinkingLevel.HIGH + Schema.obj)
    private val structuredColumnModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = attendanceColumnSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    // 3. Search Grounding Model: gemini-3.8-flash with Google Search tool
    private val searchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch())
        )
    }

    private val _selectedMode = MutableStateFlow(AiMode.DEEP_THINKING)
    val selectedMode: StateFlow<AiMode> = _selectedMode.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val _columnHelperState = MutableStateFlow<ColumnHelperState>(ColumnHelperState.Idle)
    val columnHelperState: StateFlow<ColumnHelperState> = _columnHelperState.asStateFlow()

    fun setMode(mode: AiMode) {
        _selectedMode.value = mode
    }

    fun resetColumnHelperState() {
        _columnHelperState.value = ColumnHelperState.Idle
    }

    fun runQuery(userPrompt: String) {
        if (userPrompt.isBlank()) return
        when (_selectedMode.value) {
            AiMode.DEEP_THINKING -> generateWithHighThinking(userPrompt)
            AiMode.GOOGLE_SEARCH -> generateWithSearchGrounding(userPrompt)
        }
    }

    /**
     * Mode A: AI Auto-Generate Isi Kolom Absen dari penjelasan singkat pengguna.
     */
    fun autoGenerateAttendanceColumn(
        briefExplanation: String,
        dateKey: String,
        onDraftReady: (StructuredAttendanceDraft) -> Unit = {}
    ) {
        val cleanInput = briefExplanation.trim()
        if (cleanInput.isEmpty()) {
            _columnHelperState.value = ColumnHelperState.Error(
                "Tuliskan penjelasan singkat kegiatan yang Anda lakukan hari ini terlebih dahulu."
            )
            return
        }

        viewModelScope.launch {
            _columnHelperState.value = ColumnHelperState.Loading
            try {
                val prompt = """
                    Anda adalah asisten otomatis pengisi kolom absen & laporan harian Monev MagangHub Kemnaker RI.
                    Peserta magang memberikan penjelasan singkat tentang kegiatan yang dilakukan pada tanggal $dateKey:
                    "$cleanInput"
                    
                    Tugas Anda:
                    1. Kembangkan penjelasan singkat tersebut menjadi isian kolom absen magang yang lengkap, jelas, profesional, dan menggunakan kalimat aktif Bahasa Indonesia yang baku (PUEBI/EYD).
                    2. Buat judulKegiatan singkat, uraianKegiatan yang detail dan mudah dipahami mentor/supervisor, outputCapaian hasil kerja, serta kalimatLengkapKolom yang siap langsung ditempel/diisi otomatis ke kolom absen.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    structuredColumnModel.generateContent(prompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Respons AI kosong.")
                val draft = parseStructuredDraft(jsonText)
                _columnHelperState.value = ColumnHelperState.Success(draft)
                onDraftReady(draft)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Auto-generate column timed out", e)
                _columnHelperState.value = ColumnHelperState.Error(
                    "Pembuatan otomatis melewati batas waktu 60 detik. Silakan coba lagi."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Auto-generate column failed", e)
                _columnHelperState.value = ColumnHelperState.Error(
                    e.localizedMessage ?: "Gagal membuat isian kolom absen otomatis."
                )
            }
        }
    }

    /**
     * Mode B: Perbaiki Kalimat & Tata Bahasa (Grammar/Sentence Polish) untuk kolom absen.
     */
    fun polishAttendanceSentence(
        rawText: String,
        dateKey: String,
        onDraftReady: (StructuredAttendanceDraft) -> Unit = {}
    ) {
        val cleanInput = rawText.trim()
        if (cleanInput.isEmpty()) {
            _columnHelperState.value = ColumnHelperState.Error(
                "Kolom masih kosong. Ketik kalimat draf atau penjelasan kegiatan terlebih dahulu untuk diperbaiki."
            )
            return
        }

        viewModelScope.launch {
            _columnHelperState.value = ColumnHelperState.Loading
            try {
                val prompt = """
                    Anda adalah editor bahasa profesional untuk laporan absen harian Monev MagangHub Kemnaker RI.
                    Perbaiki kalimat, tata bahasa, ejaan (EYD/PUEBI), dan struktur kalimat dari teks draf berikut (tanggal $dateKey) tanpa mengubah inti kegiatan aslinya:
                    "$cleanInput"
                    
                    Tugas Anda:
                    1. Ubah bahasa sehari-hari/informal atau kalimat yang kurang rapi menjadi kalimat laporan kerja yang sopan, efektif, lugas, dan profesional.
                    2. Ekstrak judulKegiatan, uraianKegiatan yang sudah diperbaiki kalimatnya, outputCapaian, serta kalimatLengkapKolom yang siap mengisi kolom absen.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    structuredColumnModel.generateContent(prompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Respons AI kosong.")
                val draft = parseStructuredDraft(jsonText)
                _columnHelperState.value = ColumnHelperState.Success(draft)
                onDraftReady(draft)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Polish sentence timed out", e)
                _columnHelperState.value = ColumnHelperState.Error(
                    "Perbaikan kalimat melewati batas waktu 60 detik. Silakan coba lagi."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Polish sentence failed", e)
                _columnHelperState.value = ColumnHelperState.Error(
                    e.localizedMessage ?: "Gagal memperbaiki kalimat absen."
                )
            }
        }
    }

    private fun parseStructuredDraft(jsonString: String): StructuredAttendanceDraft {
        val json = JSONObject(jsonString)
        val judul = json.optString("judulKegiatan", "Kegiatan Harian Magang")
        val uraian = json.optString("uraianKegiatan", "")
        val output = json.optString("outputCapaian", "")
        val lengkap = json.optString("kalimatLengkapKolom", "").ifBlank {
            buildString {
                if (judul.isNotBlank()) append("Kegiatan: $judul. ")
                if (uraian.isNotBlank()) append("$uraian ")
                if (output.isNotBlank()) append("Output/Capaian: $output.")
            }.trim()
        }
        return StructuredAttendanceDraft(
            judulKegiatan = judul,
            uraianKegiatan = uraian,
            outputCapaian = output,
            kalimatLengkapKolom = lengkap
        )
    }

    fun generateWithHighThinking(
        prompt: String,
        onDraftReady: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            val accumulatedText = StringBuilder()
            try {
                val systemContextualPrompt = """
                    Anda adalah Asisten Ahli Laporan Monitoring & Evaluasi (Monev) MagangHub Kemnaker RI.
                    Bantu peserta magang menyusun laporan harian/bulanan yang profesional, terstruktur,
                    mencerminkan kompetensi kerja, atau memecahkan permasalahan teknis di tempat magang.
                    Gunakan Bahasa Indonesia yang baik, jelas, dan profesional.
                    
                    Permintaan Pengguna:
                    $prompt
                """.trimIndent()

                withTimeout(60_000L) {
                    thinkingModel.generateContentStream(systemContextualPrompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulatedText.append(chunkText)
                        _uiState.value = AiUiState.Streaming(accumulatedText.toString())
                    }
                }
                val finalText = accumulatedText.toString().trim()
                if (finalText.isNotBlank()) {
                    _uiState.value = AiUiState.Success(text = finalText)
                    onDraftReady?.invoke(finalText)
                } else {
                    _uiState.value = AiUiState.Error("Model mengembalikan respons kosong.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "High thinking generation timed out", e)
                _uiState.value = AiUiState.Error("Waktu pemrosesan habis setelah 60 detik. Silakan coba lagi.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "High thinking generation failed", e)
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Gagal menghasilkan analisis.")
            }
        }
    }

    fun generateWithSearchGrounding(prompt: String) {
        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            try {
                val response = withTimeout(60_000L) {
                    searchModel.generateContent(prompt)
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    val metadata = response.candidates.firstOrNull()?.groundingMetadata
                    val sources = metadata?.groundingChunks
                        ?.mapNotNull { chunk ->
                            val web = chunk.web
                            if (web != null && !web.uri.isNullOrBlank()) {
                                GroundingSource(
                                    title = web.title ?: web.domain ?: "Sumber Web",
                                    uri = web.uri!!,
                                    domain = web.domain.orEmpty()
                                )
                            } else {
                                null
                            }
                        }
                        ?.distinctBy { it.uri }
                        .orEmpty()

                    val queries = metadata?.webSearchQueries.orEmpty()
                    _uiState.value = AiUiState.Success(
                        text = text,
                        sources = sources,
                        queries = queries
                    )
                } else {
                    _uiState.value = AiUiState.Error("Model mengembalikan respons kosong.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Search grounding timed out", e)
                _uiState.value = AiUiState.Error("Pencarian melewati batas waktu 60 detik. Silakan coba lagi.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Search grounding failed", e)
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Gagal melakukan pencarian.")
            }
        }
    }

    companion object {
        private const val TAG = "AiAssistantVM"
    }
}
