package de.worldpulse.core.claims

/**
 * Extraktions-Prompts für den S0-Test 6 (3 Ausgabeformate, SPEC 5 "Constraint Tax").
 * Prompt-Version wird in jedes Lauf-Protokoll geschrieben – bei JEDER Änderung hochzählen.
 */
enum class OutputFormat(val id: String) {
    /** (a) Erzwungenes JSON-Schema über LiteRT-LM ResponseFormat.json(...). */
    JSON_SCHEMA("json"),
    /** (b) Zeilenformat "Aussage | Wer sagt es | Typ | Belegspanne" + Code-Parser. */
    LINES("lines"),
    /** (c) Kurzes freies Nachdenken (Token-Limit), dann Marker, dann Zeilenformat. */
    THINK_THEN_LINES("think_lines"),
}

object ExtractionPrompts {
    const val VERSION = "extract-v1"
    const val CLAIMS_MARKER = "###CLAIMS###"
    const val THINK_WORD_LIMIT = 60

    /** Systemanweisung (gemeinsamer Präfix je Phase -> Prefix-/KV-Cache-Reuse möglich). */
    val system: String = """
        You extract atomic claims from a news paragraph. Rules:
        - One claim = one checkable statement (who/what/where/when/how many). Split compound sentences.
        - Keep numbers, names, places and dates exactly as written.
        - "speaker" = who asserts it (agency, ministry, person, "Artikel" if the outlet itself states it).
        - type: Fakt (directly observable/official data), Behauptung (asserted by a party, not verified),
          Zitat (verbatim quote), Meinung (evaluation/opinion).
        - span = an exact, verbatim substring of the paragraph that supports the claim (max ~25 words).
        - Write the claim in the language of the paragraph. Do not add knowledge that is not in the text.
        - If nothing is claimable, output nothing.
    """.trimIndent()

    /** JSON-Schema für das erzwungene Format (a). Bewusst flach und klein. */
    val jsonSchema: String = """
        {
          "type": "object",
          "properties": {
            "claims": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "claim":   {"type": "string"},
                  "speaker": {"type": "string"},
                  "type":    {"type": "string", "enum": ["Fakt", "Behauptung", "Zitat", "Meinung"]},
                  "span":    {"type": "string"}
                },
                "required": ["claim", "speaker", "type", "span"]
              }
            }
          },
          "required": ["claims"]
        }
    """.trimIndent()

    fun user(format: OutputFormat, article: String): String = when (format) {
        OutputFormat.JSON_SCHEMA -> """
            Return JSON: {"claims":[{"claim":..., "speaker":..., "type":..., "span":...}]}

            PARAGRAPH:
            $article
        """.trimIndent()

        OutputFormat.LINES -> """
            Output one claim per line, exactly 4 fields separated by " | ":
            claim | speaker | type | span
            No numbering, no header, no other text.
            Example: Der Pegel stieg auf 7,2 Meter | Hochwasserzentrale | Fakt | stieg der Pegel auf 7,2 Meter

            PARAGRAPH:
            $article
        """.trimIndent()

        OutputFormat.THINK_THEN_LINES -> """
            First write at most $THINK_WORD_LIMIT words of notes: list the distinct facts and who states them.
            Then write the line $CLAIMS_MARKER and after it one claim per line, exactly 4 fields separated by " | ":
            claim | speaker | type | span
            No other text after the claims.

            PARAGRAPH:
            $article
        """.trimIndent()
    }

    /** Ausgabe-Token-Limit je Format (Schätzung; im Benchmark prüfen). */
    fun maxOutputTokens(format: OutputFormat): Int = when (format) {
        OutputFormat.JSON_SCHEMA -> 600
        OutputFormat.LINES -> 450
        OutputFormat.THINK_THEN_LINES -> 600
    }
}
