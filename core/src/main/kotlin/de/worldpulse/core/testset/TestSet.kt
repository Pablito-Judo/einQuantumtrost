package de.worldpulse.core.testset

import de.worldpulse.core.claims.ClaimType
import de.worldpulse.core.eval.ExpectedClaim
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mitgelieferte Testdaten für S0 (Test 6 Extraktion, Test 7 Embedding). */
@Serializable
data class TestArticle(
    val id: String,
    val language: String,
    val synthetic: Boolean = true,
    val text: String,
    @SerialName("translated_from") val translatedFrom: String? = null,
    val original: String? = null,
    val expected: List<TestClaim>,
) {
    fun expectedClaims() = expected.map { ExpectedClaim(it.id, it.text, it.speaker, it.span) }
}

@Serializable
data class TestClaim(val id: String, val text: String, val speaker: String, val type: ClaimType, val span: String)

@Serializable
data class EmbeddingPair(val id: String, val same: Boolean, val a: String, val b: String)

@Serializable
private data class ArticleFile(val version: Int, val description: String, val articles: List<TestArticle>)

@Serializable
private data class PairFile(val version: Int, val description: String, val pairs: List<EmbeddingPair>)

object TestSet {
    private val json = Json { ignoreUnknownKeys = true }

    fun articles(): List<TestArticle> = json.decodeFromString(ArticleFile.serializer(), res("testset/articles.json")).articles
    fun embeddingPairs(): List<EmbeddingPair> = json.decodeFromString(PairFile.serializer(), res("testset/embedding_pairs.json")).pairs

    private fun res(p: String) = (TestSet::class.java.classLoader.getResourceAsStream(p) ?: error("Ressource fehlt: $p"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
