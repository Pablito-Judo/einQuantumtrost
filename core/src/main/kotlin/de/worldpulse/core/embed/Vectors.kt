package de.worldpulse.core.embed

import kotlin.math.sqrt

/** Vektor-Hilfen für EmbeddingGemma (Matryoshka: vordere Dimensionen abschneiden + neu normieren). */
object Vectors {
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Dimensionen ungleich: ${a.size} vs ${b.size}" }
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
    }

    fun truncate(v: FloatArray, dim: Int): FloatArray {
        require(dim in 1..v.size)
        val t = v.copyOf(dim)
        val n = sqrt(t.sumOf { (it * it).toDouble() }).toFloat()
        if (n > 0f) for (i in t.indices) t[i] /= n
        return t
    }

    /** Float32 little-endian als BLOB (für SQLite). */
    fun toBlob(v: FloatArray): ByteArray {
        val bb = java.nio.ByteBuffer.allocate(v.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        v.forEach { bb.putFloat(it) }
        return bb.array()
    }

    fun fromBlob(b: ByteArray): FloatArray {
        val bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return FloatArray(b.size / 4) { bb.getFloat() }
    }
}

/** Auswertung des Satzpaar-Tests: trennt Kosinus "gleiche Meldung" von "andere Meldung"? */
data class PairQuality(
    val meanSame: Double,
    val meanDifferent: Double,
    val minSame: Double,
    val maxDifferent: Double,
    /** Anteil korrekt, wenn mit bester Einzelschwelle getrennt wird. */
    val bestThresholdAccuracy: Double,
    val bestThreshold: Double,
) {
    val margin: Double get() = minSame - maxDifferent

    companion object {
        fun of(same: List<Double>, different: List<Double>): PairQuality {
            require(same.isNotEmpty() && different.isNotEmpty())
            val all = (same.map { it to true } + different.map { it to false })
            val cands = all.map { it.first }.sorted()
            var bestAcc = 0.0; var bestT = 0.0
            for (t in cands) {
                val acc = all.count { (c, s) -> (c >= t) == s }.toDouble() / all.size
                if (acc > bestAcc) { bestAcc = acc; bestT = t }
            }
            return PairQuality(same.average(), different.average(), same.min(), different.max(), bestAcc, bestT)
        }
    }
}
