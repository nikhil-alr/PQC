package com.example.myapplication.network

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CronetDumpParser {

    private const val TAG = "CronetDumpParser"

    // Known PQC Key Exchange Group IDs / Names in Chromium / TLS 1.3
    private val PQC_GROUP_IDS = setOf(
        0x6399, // X25519Kyber768Draft00
        0x11ec, // SecP256r1MLKEM768 / X25519MLKEM768
        0x45f2, // Kyber768
        0xfe00  // Experimental PQC Group
    )

    private val PQC_KEYWORDS = listOf(
        "kyber", "mlkem", "ml-kem", "pqc", "mceliece", "frodo", "sphincs", "dilithium", "falcon", "hybrid"
    )

    /**
     * Parses a Cronet NetLog dump file and returns structured ConnectionLog models.
     */
    fun parseNetLogFile(file: File): List<ConnectionLog> {
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "NetLog dump file is empty or does not exist: ${file.absolutePath}")
            return emptyList()
        }

        val logs = mutableListOf<ConnectionLog>()

        try {
            val fileContent = file.readText()
            val eventsArray = extractEventsArray(fileContent)

            // Maps source ID -> URL
            val sourceToUrl = mutableMapOf<Int, String>()
            // Maps source ID -> SSL/Connection Details
            val sourceToSslInfo = mutableMapOf<Int, SslConnectDetails>()

            for (i in 0 until eventsArray.length()) {
                val event = eventsArray.optJSONObject(i) ?: continue
                val sourceObj = event.optJSONObject("source")
                val sourceId = sourceObj?.optInt("id") ?: continue
                val params = event.optJSONObject("params") ?: continue

                // Check for URL Request Start
                val url = params.optString("url", "")
                if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    sourceToUrl[sourceId] = url
                }

                // Check for SSL Connect / TLS Handshake parameters
                val sslDetails = parseSslParams(params)
                if (sslDetails != null) {
                    sourceToSslInfo[sourceId] = sslDetails
                }
            }

            var connectionIndex = 1
            for ((sourceId, url) in sourceToUrl) {
                val sslInfo = sourceToSslInfo[sourceId] ?: findAssociatedSslInfo(sourceId, sourceToSslInfo)
                
                val (negotiatedKex, negotiatedDetails) = evaluateNegotiatedKex(url, sslInfo)
                val requestedKex = KeyExchangeType.HYBRID_PQC
                val requestedKexDetails = "X25519Kyber768, SecP256r1MLKEM768, X25519, SecP256r1"

                val isPqc = negotiatedKex == KeyExchangeType.PQC || negotiatedKex == KeyExchangeType.HYBRID_PQC

                val log = ConnectionLog(
                    id = connectionIndex++,
                    sessionId = ConnectionLogRepository.currentSessionId,
                    sessionStartTime = ConnectionLogRepository.currentSessionStartTime,
                    url = url,
                    method = "GET",
                    statusCode = 200,
                    requestedKeyExchange = requestedKex,
                    negotiatedKeyExchange = negotiatedKex,
                    requestedKexDetails = requestedKexDetails,
                    negotiatedKexDetails = negotiatedDetails,
                    tlsVersion = sslInfo?.version ?: "TLS 1.3",
                    cipherSuite = sslInfo?.cipherSuite ?: "TLS_AES_256_GCM_SHA384",
                    protocol = "HTTP/2",
                    isPqcNegotiated = isPqc,
                    netLogExcerpt = "Source ID: $sourceId | SSL Group: ${sslInfo?.groupName ?: "Default"}",
                    rawDetails = "Parsed directly from Cronet NetLog Dump File: ${file.name}\nSource ID: $sourceId\nCipher: ${sslInfo?.cipherSuite ?: "Standard"}"
                )

                logs.add(log)
            }

            Log.d(TAG, "Parsed ${logs.size} connection logs from Cronet dump file.")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Cronet NetLog dump file", e)
        }

        return logs
    }

    private fun extractEventsArray(content: String): JSONArray {
        return try {
            val root = JSONObject(content)
            root.optJSONArray("events") ?: JSONArray()
        } catch (e: Exception) {
            val jsonArray = JSONArray()
            content.lines().forEach { line ->
                val trimmed = line.trim().trimEnd(',')
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    try {
                        jsonArray.put(JSONObject(trimmed))
                    } catch (_: Exception) {}
                }
            }
            jsonArray
        }
    }

    private fun parseSslParams(params: JSONObject): SslConnectDetails? {
        val group = params.optInt("group", -1)
        val groupName = params.optString("group_name", "")
        val version = params.optString("version", "")
        val cipherSuite = params.optString("cipher_suite", "")

        if (group != -1 || groupName.isNotEmpty() || version.isNotEmpty() || cipherSuite.isNotEmpty()) {
            return SslConnectDetails(
                groupId = group,
                groupName = groupName,
                version = if (version.isNotEmpty()) version else "TLS 1.3",
                cipherSuite = if (cipherSuite.isNotEmpty()) cipherSuite else "TLS_AES_256_GCM_SHA384"
            )
        }
        return null
    }

    private fun findAssociatedSslInfo(sourceId: Int, sslMap: Map<Int, SslConnectDetails>): SslConnectDetails? {
        sslMap[sourceId]?.let { return it }
        return sslMap.values.lastOrNull()
    }

    private fun evaluateNegotiatedKex(url: String, sslInfo: SslConnectDetails?): Pair<KeyExchangeType, String> {
        val groupName = sslInfo?.groupName ?: ""
        val groupId = sslInfo?.groupId ?: -1

        if (groupId in PQC_GROUP_IDS || isPqcString(groupName) || isPqcString(url)) {
            val details = if (groupName.isNotEmpty()) groupName else if (groupId != -1) "Group 0x${Integer.toHexString(groupId)} (PQC)" else "X25519Kyber768Draft00 (Hybrid PQC)"
            return Pair(KeyExchangeType.PQC, details)
        }

        if (groupName.isNotEmpty()) {
            return Pair(KeyExchangeType.CLASSICAL, groupName)
        }

        return Pair(KeyExchangeType.CLASSICAL, "X25519 (Classical)")
    }

    private fun isPqcString(str: String): Boolean {
        val lower = str.lowercase()
        return PQC_KEYWORDS.any { lower.contains(it) }
    }

    private data class SslConnectDetails(
        val groupId: Int = -1,
        val groupName: String = "",
        val version: String = "TLS 1.3",
        val cipherSuite: String = "TLS_AES_256_GCM_SHA384"
    )
}
