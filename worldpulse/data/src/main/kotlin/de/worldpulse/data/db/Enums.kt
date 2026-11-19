package de.worldpulse.data.db

/*
 * Aufzählungen des Datenmodells (SPEC 5 "Datenmodell", 3.2 Pins/Symbole, 3.4 Dossiers).
 * Room (ab 2.3) speichert Enums automatisch als Name (TEXT) – Umbenennen = Migration! Neue Werte nur anhängen.
 * EnumStabilityTest friert die Namen ein.
 * ClaimType / SourceTier / GeoPrecision kommen aus :core (gemeinsam mit Goldstandard).
 */

enum class SourceKind { RSS, ATOM, ICAL, JSONLD, HTML, API, GDELT, TELEGRAM, SHARE, MANUAL }
enum class DocumentStatus { NEU, BEREINIGT, EXTRAHIERT, VERWORFEN, FEHLER }
enum class EntityType { PERSON, ORGANISATION, ORT, SONSTIGES }
enum class EventStatus { GEMELDET, BESTAETIGT, UMSTRITTEN }
enum class Urgency { EILMELDUNG, NORMAL, LANGSAM }
enum class DossierState { AKTIV, EINGEFROREN }
enum class DossierSection { KURZFASSUNG, NEU_SEIT_UPDATE, WAS_PASSIERT, HINTERGRUND, ZEITLEISTE, BETEILIGTE, PERSPEKTIVEN, OFFENE_FRAGEN, QUELLEN }
enum class Verdict { BELEGT, TEILWEISE, UNBELEGT, UNGEPRUEFT }
enum class SentenceChange { NEU, GEAENDERT, UNVERAENDERT, WIDERRUFEN }
enum class LinkRelation { STUETZT, WIDERSPRICHT, NEUTRAL, GLEICHE_AUSSAGE, GLEICHE_ENTITAET }
enum class LinkMethod { CODE, EMBEDDING, NLI, LLM, NUTZER }
enum class ResubmitReason { INTERVALL, NEUE_CLAIM, QUELLE_KORRIGIERT, WIDERSPRUCH, NUTZER_GEOEFFNET }
enum class FeedbackTarget { CLAIM, SATZ, DOSSIER, QUELLE, UI_EVENT }
enum class FeedbackKind { STIMMT, FALSCH, FEHLT, DAUMEN_HOCH, DAUMEN_RUNTER }
enum class OwnerType { CLAIM, DOKUMENT, EREIGNIS, DOSSIER }

/** Konflikt-/Ereignissymbole (SPEC 3.2). */
enum class Symbol { KAMPFHANDLUNG, EXPLOSION_BESCHUSS, GEWALT_ZIVILISTEN, PROTEST, ERDBEBEN, BRAND, UNWETTER_KATASTROPHE, POLITIK_WAHL, EVENT, SONSTIGES }

/** Status-Ring: durchgezogen / gestrichelt / halb (SPEC 3.2). */
enum class StatusRing { BESTAETIGT, GEMELDET, UMSTRITTEN }

/** Pipeline-Phasen (SPEC 5 Phasen-Batching) – für UI-Events und Lauf-Protokoll. */
enum class Phase { SAMMELN, BEREINIGEN, DEDUPE, UEBERSETZEN, EXTRAKTION, EMBEDDING, CLUSTERING, SCHREIBEN, PRUEFEN, WIEDERVORLAGE, HAUSHALT, BENCHMARK }
