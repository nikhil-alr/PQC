package com.example.myapplication.network

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class KeyExchangeType(val displayName: String) {
    PQC("PQC"),
    CLASSICAL("Classical"),
    HYBRID_PQC("Hybrid PQC"),
    UNKNOWN("Unknown")
}

data class ConnectionLog(
    val id: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val sessionStartTime: Long = System.currentTimeMillis(),
    val url: String,
    val method: String = "GET",
    val statusCode: Int? = null,
    val requestedKeyExchange: KeyExchangeType = KeyExchangeType.HYBRID_PQC,
    val negotiatedKeyExchange: KeyExchangeType = KeyExchangeType.UNKNOWN,
    val requestedKexDetails: String = "X25519Kyber768, SecP256r1MLKEM768, X25519, SecP256r1",
    val negotiatedKexDetails: String = "Unknown",
    val tlsVersion: String = "TLS 1.3",
    val cipherSuite: String = "Unknown",
    val protocol: String = "HTTP/2",
    val isPqcNegotiated: Boolean = false,
    val netLogExcerpt: String = "",
    val rawDetails: String = "",
    val errorMessage: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val host: String
        get() {
            return try {
                val uri = java.net.URI(url)
                uri.host ?: url
            } catch (e: Exception) {
                url
            }
        }
}

data class ConnectionStats(
    val totalConnections: Int = 0,
    val pqcNegotiatedCount: Int = 0,
    val classicalNegotiatedCount: Int = 0,
    val unknownCount: Int = 0,
    val errorCount: Int = 0,
    val sessionStartTime: Long = System.currentTimeMillis()
) {
    val pqcPercentage: Int
        get() = if (totalConnections > 0) (pqcNegotiatedCount * 100) / totalConnections else 0

    val formattedSessionTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(sessionStartTime))
        }
}
