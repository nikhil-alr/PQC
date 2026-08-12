package com.example.myapplication.network

import android.content.Context
import android.util.Log
import com.google.net.cronet.okhttptransport.CronetInterceptor
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.chromium.net.CronetEngine
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {

    private lateinit var client: OkHttpClient
    private lateinit var cronetEngine: CronetEngine

    private const val TAG = "NetworkClient"

    fun init(context: Context) {
        try {
            // Start fresh session tracking for this app launch
            ConnectionLogRepository.startNewSession()

            // Build native Cronet engine with Http2 & QUIC enabled
            cronetEngine = CronetEngine.Builder(context)
                .enableHttp2(true)
                .enableQuic(true)
                .build()

            // Start native Cronet NetLog file dumping directly to filesystem
            CronetLogManager.startNetLog(context, cronetEngine)

            val cronetInterceptor = CronetInterceptor.newBuilder(cronetEngine).build()

            val cache = Cache(
                File(context.cacheDir, "okhttp-cache"),
                100L * 1024L * 1024L // 100 MB
            )

            client = OkHttpClient.Builder()
                .addInterceptor(cronetInterceptor)
                .cache(cache)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            Log.d(TAG, "Cronet initialized for ${ConnectionLogRepository.currentSessionId}. NetLog dump path: ${CronetLogManager.getDumpFilePath()}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cronet NetworkClient", e)
        }
    }

    fun parseDumpFile(): Int {
        return CronetLogManager.parseAndLoadDumpFile(getCronetEngine())
    }

    fun fetchData(
        url: String,
        callback: (String?) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed: $url", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val bodyString = response.body?.string()

                        // If response is from /cdn-cgi/trace, parse kex line (e.g. kex=X25519Kyber768Draft00 or kex=X25519)
                        var traceKex: String? = null
                        if (bodyString != null && bodyString.contains("kex=")) {
                            for (line in bodyString.lines()) {
                                if (line.startsWith("kex=")) {
                                    traceKex = line.substringAfter("kex=").trim()
                                    break
                                }
                            }
                        }

                        // Record explicit log entry tagged with currentSessionId into repository
                        val isPqc = traceKex != null && (traceKex.contains("Kyber", ignoreCase = true) || traceKex.contains("MLKEM", ignoreCase = true) || traceKex.contains("pqc", ignoreCase = true))
                        val kexType = if (isPqc) KeyExchangeType.PQC else KeyExchangeType.CLASSICAL

                        val log = ConnectionLog(
                            id = ConnectionLogRepository.generateNextId(),
                            sessionId = ConnectionLogRepository.currentSessionId,
                            sessionStartTime = ConnectionLogRepository.currentSessionStartTime,
                            url = url,
                            method = request.method,
                            statusCode = response.code,
                            requestedKeyExchange = KeyExchangeType.HYBRID_PQC,
                            negotiatedKeyExchange = kexType,
                            requestedKexDetails = "X25519Kyber768, SecP256r1MLKEM768, X25519, SecP256r1",
                            negotiatedKexDetails = traceKex ?: if (isPqc) "X25519Kyber768Draft00" else "X25519",
                            tlsVersion = response.handshake?.tlsVersion?.javaName ?: "TLS 1.3",
                            cipherSuite = response.handshake?.cipherSuite?.javaName ?: "TLS_AES_256_GCM_SHA384",
                            protocol = response.protocol.toString(),
                            isPqcNegotiated = isPqc,
                            netLogExcerpt = "Cronet NetLog Dump File: ${CronetLogManager.getDumpFilePath()}"
                        )
                        ConnectionLogRepository.addLog(log)

                        if (!response.isSuccessful) {
                            Log.e(TAG, "HTTP ${response.code}")
                            callback(null)
                        } else {
                            callback(bodyString)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Response parsing failed", e)
                        callback(null)
                    }
                }
            }
        })
    }

    fun getCronetEngine(): CronetEngine? {
        return if (::cronetEngine.isInitialized) cronetEngine else null
    }
}