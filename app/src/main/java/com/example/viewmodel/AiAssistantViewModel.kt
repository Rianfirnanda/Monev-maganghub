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
    val uraianAktivitas: String,
    val pembelajaranDiperoleh: String,
    val kendalaDialami: String,
    val uraianKegiatan: String = uraianAktivitas,
    val outputCapaian: String = pembelajaranDiperoleh,
    val kalimatLengkapKolom: String = ""
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
                description = "Judul singkat kegiatan magang hari ini (maksimal 12 kata)"
            ),
            "uraianAktivitas" to Schema.string(
                description = "Isi kolom 'Uraian Aktivitas' Monev MagangHub menggunakan kalimat aktif Bahasa Indonesia baku (PUEBI/EYD), jelas, rapi, dan profesional"
            ),
            "pembelajaranDiperoleh" to Schema.string(
                description = "Isi kolom 'Pembelajaran yang Diperoleh' dari kegiatan yang dilakukan hari ini secara relevan, konkret, dan profesional"
            ),
            "kendalaDialami" to Schema.string(
                description = "Isi kolom 'Kendala yang Dialami' beserta cara mengatasinya secara sopan dan solutif (atau pernyataan profesional jika kegiatan berjalan lancar)"
            ),
            "kalimatLengkapKolom" to Schema.string(
                description = "Ringkasan gabungan Uraian Aktivitas, Pembelajaran yang Diperoleh, dan Kendala yang Dialami"
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
     * Mode A: AI Auto-Generate 3 Kolom Absen (Uraian Aktivitas, Pembelajaran yang Diperoleh, Kendala yang Dialami).
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
                    Anda adalah asisten otomatis pengisi laporan harian Monev MagangHub Kemnaker RI.
                    Peserta magang memberikan penjelasan singkat tentang kegiatan pada tanggal $dateKey:
                    "$cleanInput"
                    
                    Tugas Anda adalah menyusun isian otomatis untuk 3 kolom wajib Monev MagangHub dengan Bahasa Indonesia baku (PUEBI/EYD), kalimat aktif, lugas, dan profesional:
                    1. uraianAktivitas: Kembangkan penjelasan singkat peserta menjadi uraian aktivitas harian yang runtut, jelas, dan profesional.
                    2. pembelajaranDiperoleh: Rumuskan pembelajaran, peningkatan kompetensi teknis/non-teknis, atau wawasan baru yang diperoleh dari aktivitas tersebut.
                    3. kendalaDialami: Tuliskan kendala yang dihadapi beserta solusi yang dilakukan secara profesional (jika peserta tidak menyebutkan kendala khusus, tuliskan bahwa kegiatan berjalan lancar sesuai arahan mentor atau kendala teknis kecil berhasil diatasi melalui koordinasi).
                    4. judulKegiatan & kalimatLengkapKolom.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    structuredColumnModel.generateContent(prompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Respons AI kosong.")
                val draft = parseStructuredDraft(jsonText)
                _columnHelperState.value = ColumnHelperState.Success(draft)
                onDraftReady(draft)
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Auto-generate timed out, using smart local structured generator", e)
                    val fallback = buildSmartLocalIndonesianDraft(cleanInput, isPolishMode = false)
                    _columnHelperState.value = ColumnHelperState.Success(fallback)
                    onDraftReady(fallback)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI unavailable (${e.message}), using smart local structured generator", e)
                val fallback = buildSmartLocalIndonesianDraft(cleanInput, isPolishMode = false)
                _columnHelperState.value = ColumnHelperState.Success(fallback)
                onDraftReady(fallback)
            }
        }
    }

    /**
     * Mode B: Perbaiki Kalimat & Tata Bahasa untuk ke-3 kolom absen (Uraian Aktivitas, Pembelajaran, Kendala).
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
                    Anda adalah editor bahasa profesional untuk 3 kolom laporan harian Monev MagangHub Kemnaker RI.
                    Perbaiki kalimat, tata bahasa, ejaan (EYD/PUEBI), dan struktur kalimat dari teks draf berikut (tanggal $dateKey) tanpa mengubah inti kegiatan aslinya:
                    "$cleanInput"
                    
                    Tugas Anda:
                    1. uraianAktivitas: Ubah kalimat draf menjadi kalimat laporan aktivitas kerja yang sopan, efektif, lugas, dan profesional.
                    2. pembelajaranDiperoleh: Susun kalimat pembelajaran yang diperoleh dari kegiatan tersebut dengan bahasa baku.
                    3. kendalaDialami: Susun kalimat kendala yang dialami beserta tindak lanjutnya secara sopan dan profesional.
                    4. judulKegiatan & kalimatLengkapKolom.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    structuredColumnModel.generateContent(prompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Respons AI kosong.")
                val draft = parseStructuredDraft(jsonText)
                _columnHelperState.value = ColumnHelperState.Success(draft)
                onDraftReady(draft)
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    val fallback = buildSmartLocalIndonesianDraft(cleanInput, isPolishMode = true)
                    _columnHelperState.value = ColumnHelperState.Success(fallback)
                    onDraftReady(fallback)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI polish unavailable (${e.message}), using smart local polisher", e)
                val fallback = buildSmartLocalIndonesianDraft(cleanInput, isPolishMode = true)
                _columnHelperState.value = ColumnHelperState.Success(fallback)
                onDraftReady(fallback)
            }
        }
    }

    private fun parseStructuredDraft(jsonString: String): StructuredAttendanceDraft {
        val json = JSONObject(jsonString)
        val judul = json.optString("judulKegiatan", "Kegiatan Harian Magang")
        val uraian = json.optString("uraianAktivitas", "").ifBlank {
            json.optString("uraianKegiatan", "")
        }
        val pembelajaran = json.optString("pembelajaranDiperoleh", "").ifBlank {
            json.optString("outputCapaian", "")
        }
        val kendala = json.optString("kendalaDialami", "").ifBlank {
            "Tidak terdapat kendala berarti selama pelaksanaan kegiatan; seluruh tugas dapat diselesaikan dengan baik sesuai arahan."
        }
        val lengkap = buildFullSummaryText(uraian, pembelajaran, kendala)
        return StructuredAttendanceDraft(
            judulKegiatan = judul,
            uraianAktivitas = uraian,
            pembelajaranDiperoleh = pembelajaran,
            kendalaDialami = kendala,
            uraianKegiatan = uraian,
            outputCapaian = pembelajaran,
            kalimatLengkapKolom = lengkap
        )
    }

    private fun buildFullSummaryText(
        uraian: String,
        pembelajaran: String,
        kendala: String
    ): String {
        return buildString {
            append("Uraian Aktivitas:\n$uraian")
            if (pembelajaran.isNotBlank()) {
                append("\n\nPembelajaran yang Diperoleh:\n$pembelajaran")
            }
            if (kendala.isNotBlank()) {
                append("\n\nKendala yang Dialami:\n$kendala")
            }
        }.trim()
    }

    /**
     * Mesin penyusun kalimat PUEBI/EYD lokal otomatis untuk memastikan ke-3 kolom
     * (Uraian Aktivitas, Pembelajaran yang Diperoleh, Kendala yang Dialami) selalu berhasil
     * disusun dengan rapi meskipun perangkat pengguna sedang terkendala jaringan atau App Check.
     */
    private fun buildSmartLocalIndonesianDraft(
        rawInput: String,
        isPolishMode: Boolean
    ): StructuredAttendanceDraft {
        val normalized = rawInput.trim().replace(Regex("\\s+"), " ")
        val lower = normalized.lowercase()

        // Bersihkan awalan waktu/subjek informal agar menjadi kalimat laporan formal
        var cleanedClause = normalized
            .replace(Regex("^(hari ini|tadi pagi|pagi ini|pada hari ini)\\s+(saya|aku|kami)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(saya|aku|kami)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(siangnya|siang hari)\\s+(saya|aku|kami)\\s+", RegexOption.IGNORE_CASE), "pada siang hari ")
            .replace(Regex("\\b(sorenya|sore hari)\\s+(saya|aku|kami)\\s+", RegexOption.IGNORE_CASE), "pada sore hari ")
            .replace(Regex("\\b(paginya|pagi hari)\\s+(saya|aku|kami)\\s+", RegexOption.IGNORE_CASE), "pada pagi hari ")
            .replace(Regex("\\b(lalu|kemudian|terus|habis itu)\\s+(saya|aku|kami)\\s+", RegexOption.IGNORE_CASE), "kemudian ")
            .replace(Regex("\\bbikin\\b", RegexOption.IGNORE_CASE), "membuat")
            .replace(Regex("\\bbantu\\b", RegexOption.IGNORE_CASE), "membantu pelaksanaan")
            .replace(Regex("\\bcek\\b", RegexOption.IGNORE_CASE), "melakukan pemeriksaan")
            .replace(Regex("\\bngecek\\b", RegexOption.IGNORE_CASE), "melakukan pengecekan")
            .replace(Regex("\\bnerima\\b", RegexOption.IGNORE_CASE), "menerima")
            .replace(Regex("\\bnginput\\b|\\binput\\b", RegexOption.IGNORE_CASE), "melakukan penginputan")
            .replace(Regex("\\brekap\\b|\\bmerekap\\b", RegexOption.IGNORE_CASE), "melakukan perekapan")
            .replace(Regex("\\bikut\\b", RegexOption.IGNORE_CASE), "mengikuti")
            .replace(Regex("\\bsama\\b", RegexOption.IGNORE_CASE), "bersama")
            .replace(Regex("\\bdi situs\\b", RegexOption.IGNORE_CASE), "pada sistem situs web")
            .trim()
            .removeSuffix(".")

        cleanedClause = cleanedClause.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        val uraianAktivitas = if (isPolishMode) {
            if (cleanedClause.startsWith("Melaksanakan", ignoreCase = true) ||
                cleanedClause.startsWith("Mengikuti", ignoreCase = true) ||
                cleanedClause.startsWith("Melakukan", ignoreCase = true) ||
                cleanedClause.startsWith("Membantu", ignoreCase = true) ||
                cleanedClause.startsWith("Menyusun", ignoreCase = true)
            ) {
                "$cleanedClause secara tertib, terstruktur, dan sesuai dengan prosedur kerja di lingkungan instansi."
            } else {
                "Melaksanakan kegiatan operasional magang berupa ${cleanedClause.replaceFirstChar { it.lowercase() }} secara tertib dan sesuai arahan pembimbing."
            }
        } else {
            val baseAction = if (
                cleanedClause.startsWith("Mengikuti", ignoreCase = true) ||
                cleanedClause.startsWith("Melakukan", ignoreCase = true) ||
                cleanedClause.startsWith("Membantu", ignoreCase = true) ||
                cleanedClause.startsWith("Membuat", ignoreCase = true) ||
                cleanedClause.startsWith("Menyusun", ignoreCase = true) ||
                cleanedClause.startsWith("Memperbaiki", ignoreCase = true)
            ) {
                cleanedClause
            } else {
                "Melaksanakan kegiatan ${cleanedClause.replaceFirstChar { it.lowercase() }}"
            }
            "$baseAction dengan penuh tanggung jawab guna mendukung kelancaran pelaksanaan tugas dan program kerja di unit penempatan magang."
        }

        // Susun Pembelajaran yang Diperoleh secara kontekstual dari kata kunci kegiatan
        val learningPoints = mutableListOf<String>()
        if (lower.contains("upacara") || lower.contains("apel") || lower.contains("peringatan")) {
            learningPoints.add("meningkatkan kedisiplinan, etos kerja, serta nilai kebersamaan di lingkungan instansi")
        }
        if (lower.contains("kesehatan") || lower.contains("cek kesehatan") || lower.contains("senam") || lower.contains("olahraga")) {
            learningPoints.add("memahami pentingnya menjaga kebugaran dan kesehatan kerja guna menunjang produktivitas harian")
        }
        if (lower.contains("bug") || lower.contains("situs") || lower.contains("web") || lower.contains("aplikasi") || lower.contains("kode") || lower.contains("coding") || lower.contains("sistem")) {
            learningPoints.add("memperdalam keterampilan teknis dalam melakukan pelacakan masalah (debugging), pemeliharaan sistem web, serta pengujian fungsionalitas fitur agar berjalan stabil")
        }
        if (lower.contains("data") || lower.contains("excel") || lower.contains("rekap") || lower.contains("input") || lower.contains("statistik") || lower.contains("tabel")) {
            learningPoints.add("meningkatkan ketelitian dan akurasi dalam pengolahan, verifikasi, serta penyajian data administrasi/statistik")
        }
        if (lower.contains("rapat") || lower.contains("koordinasi") || lower.contains("diskusi") || lower.contains("evaluasi") || lower.contains("mentor")) {
            learningPoints.add("mempelajari cara berkomunikasi efektif, menyampaikan progres kerja, dan berkoordinasi secara profesional dalam tim")
        }
        if (lower.contains("surat") || lower.contains("dokumen") || lower.contains("laporan") || lower.contains("arsip")) {
            learningPoints.add("memahami standar tata naskah dinas dan penyusunan dokumentasi kerja yang rapi serta terstandarisasi")
        }

        val pembelajaranDiperoleh = if (learningPoints.isNotEmpty()) {
            "Melalui pelaksanaan kegiatan hari ini, pembelajaran yang diperoleh adalah ${learningPoints.joinToString(", serta ")}."
        } else {
            "Memperoleh pemahaman praktis mengenai alur pelaksanaan tugas harian di unit kerja, meningkatkan ketelitian dan tanggung jawab profesional, serta memperkuat kemampuan koordinasi dengan pembimbing lapangan."
        }

        // Susun Kendala yang Dialami secara kontekstual
        val kendalaDialami = when {
            lower.contains("kendala") || lower.contains("masalah") || lower.contains("kesulitan") || lower.contains("hambatan") -> {
                "Terdapat penyesuaian teknis selama proses pengerjaan tugas, namun kendala tersebut dapat diatasi dengan baik melalui pengecekan ulang secara bertahap dan konsultasi langsung bersama pembimbing lapangan."
            }
            lower.contains("bug") || lower.contains("error") || lower.contains("perbaikan") -> {
                "Selama proses penelusuran dan perbaikan bug pada sistem, diperlukan ketelitian ekstra untuk mengidentifikasi sumber permasalahan kode, namun hal tersebut berhasil diselesaikan melalui pengujian bertahap."
            }
            else -> {
                "Tidak terdapat kendala yang berarti selama pelaksanaan kegiatan hari ini; seluruh rangkaian aktivitas berjalan lancar sesuai jadwal dan arahan pembimbing."
            }
        }

        val shortWords = normalized.split(" ").take(7).joinToString(" ")
        val judul = "Laporan Aktivitas: ${shortWords.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"

        return StructuredAttendanceDraft(
            judulKegiatan = judul,
            uraianAktivitas = uraianAktivitas,
            pembelajaranDiperoleh = pembelajaranDiperoleh,
            kendalaDialami = kendalaDialami,
            uraianKegiatan = uraianAktivitas,
            outputCapaian = pembelajaranDiperoleh,
            kalimatLengkapKolom = buildFullSummaryText(uraianAktivitas, pembelajaranDiperoleh, kendalaDialami)
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
                    Bantu peserta magang menyusun laporan harian/bulanan (Uraian Aktivitas, Pembelajaran yang Diperoleh, dan Kendala yang Dialami) yang profesional, terstruktur,
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
                    val draft = buildSmartLocalIndonesianDraft(prompt, isPolishMode = false)
                    _uiState.value = AiUiState.Success(text = draft.kalimatLengkapKolom)
                    onDraftReady?.invoke(draft.kalimatLengkapKolom)
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    val draft = buildSmartLocalIndonesianDraft(prompt, isPolishMode = false)
                    _uiState.value = AiUiState.Success(text = draft.kalimatLengkapKolom)
                    onDraftReady?.invoke(draft.kalimatLengkapKolom)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "High thinking fallback triggered (${e.message})", e)
                val draft = buildSmartLocalIndonesianDraft(prompt, isPolishMode = false)
                _uiState.value = AiUiState.Success(text = draft.kalimatLengkapKolom)
                onDraftReady?.invoke(draft.kalimatLengkapKolom)
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
                val draft = buildSmartLocalIndonesianDraft(prompt, isPolishMode = false)
                _uiState.value = AiUiState.Success(text = draft.kalimatLengkapKolom)
            }
        }
    }

    companion object {
        private const val TAG = "AiAssistantVM"
    }
}
