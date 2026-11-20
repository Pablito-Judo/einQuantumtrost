package de.worldpulse.core.scout

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

/** Quellenregister (SPEC 8): sources.yaml, neue Quellen erst Kandidat, dann manuell freigegeben. */
object SourceRegistry {
    @Serializable
    private data class File(val sources: List<SourceDef>)

    fun parse(yaml: String): List<SourceDef> = Yaml.default.decodeFromString(File.serializer(), yaml).sources

    fun bundled(): List<SourceDef> = parse(
        (SourceRegistry::class.java.classLoader.getResourceAsStream("sources.yaml") ?: error("sources.yaml fehlt"))
            .bufferedReader().use { it.readText() },
    )

    /** Prüft Regeln: eindeutige IDs, https, sanktionierte Medien nie Stufe A/B. */
    fun validate(list: List<SourceDef>): List<String> {
        val errs = mutableListOf<String>()
        list.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { errs += "doppelte ID $it" }
        list.forEach { s ->
            if (!s.url.startsWith("https://")) errs += "${s.id}: nur https"
            if (s.sanctioned && s.tier != de.worldpulse.core.gold.SourceTier.C) errs += "${s.id}: sanktionierte Quelle muss Stufe C sein"
        }
        return errs
    }
}
