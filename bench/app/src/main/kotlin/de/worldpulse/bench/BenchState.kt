package de.worldpulse.bench

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Alle Tests aus START_PROMPT Auftrag 1 (Nummern = Punkte 1–9). */
enum class TestId(val label: String) {
    AUTO_ALL("▶ ALLES AUTOMATISCH (Modelle laden + alle Tests, ca. 10–12 h)"),
    DOWNLOAD("Modelle von Hugging Face laden"),
    MODEL_INFO("0 Modell-Info (Typ, MTP, Backends, NPU)"),
    LOAD_PREFILL_DECODE("1 Laden / Prefill 500-1000-2000 / Decode"),
    BACKEND_MATRIX("2 Backend-Matrix CPU/GPU/NPU"),
    MODEL_MATRIX("3 Modell-Matrix (alle LLM-Dateien, GPU+CPU)"),
    RAM_SUSTAIN("3b RAM-Test 10 min (E4B)"),
    MTP_COMPARE("4 MTP an/aus"),
    SUSTAINED_DUTY("5 10 min Dauerlauf, dann 10 min Duty-Cycling"),
    EXTRACTION("6 Extraktion 20 Artikel x 3 Formate"),
    EMBEDDING("7 Embedding (alle Embedding-Dateien)"),
    SCREEN_MAP("8a 10 min LLM mit Karten-Ansicht"),
    SCREEN_OFF("8b 10 min LLM bei Bildschirm AUS"),
    FGS_LONG("9 Vordergrunddienst-Langlauf (onTimeout)"),
}

data class TestSpec(
    val id: TestId,
    val llmModels: List<File>,
    val embedModels: List<File>,
    val backend: BackendChoice,
    val mtp: MtpChoice,
    val minutes: Int = 10,
    val fgsHours: Double = 8.0,
    val fgsWithLlm: Boolean = false,
)

object BenchState {
    val running = MutableStateFlow<TestId?>(null)
    val progress = MutableStateFlow("")
    /** Letzter gestreamter LLM-Text (Anzeige in der Karten-Ansicht). */
    val ticker = MutableStateFlow("")
    /** Gerade laufender Teil-Test (Auto-Modus): steuert z. B. die Karten-Ansicht. */
    val subTest = MutableStateFlow<TestId?>(null)
    /** Auto-Modus: Bildschirm an (minimale Helligkeit) bis zum Test "Bildschirm AUS". */
    val keepScreen = MutableStateFlow(false)
    @Volatile var pending: TestSpec? = null
}
