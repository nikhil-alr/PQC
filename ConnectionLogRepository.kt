package com.example.myapplication.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

object ConnectionLogRepository {

    private val counter = AtomicInteger(1)
    
    var currentSessionId: String = "Session #" + System.currentTimeMillis()
        private set
    
    var currentSessionStartTime: Long = System.currentTimeMillis()
        private set

    private val _logs = MutableStateFlow<List<ConnectionLog>>(emptyList())
    val logs: StateFlow<List<ConnectionLog>> = _logs.asStateFlow()

    private val _allTimeStats = MutableStateFlow(ConnectionStats())
    val allTimeStats: StateFlow<ConnectionStats> = _allTimeStats.asStateFlow()

    private val _currentSessionStats = MutableStateFlow(ConnectionStats())
    val currentSessionStats: StateFlow<ConnectionStats> = _currentSessionStats.asStateFlow()

    fun startNewSession() {
        currentSessionStartTime = System.currentTimeMillis()
        currentSessionId = "Session @ " + System.currentTimeMillis()
        recalculateStats()
    }

    fun addLog(log: ConnectionLog) {
        val updatedLog = if (log.sessionId.isEmpty()) {
            log.copy(sessionId = currentSessionId, sessionStartTime = currentSessionStartTime)
        } else {
            log
        }

        _logs.update { currentList ->
            listOf(updatedLog) + currentList
        }
        recalculateStats()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        counter.set(1)
        recalculateStats()
    }

    private fun recalculateStats() {
        val currentLogs = _logs.value

        // All Time Stats
        var allTotal = currentLogs.size
        var allPqc = 0
        var allClassical = 0
        var allErrors = 0

        // Current Session Stats
        var sessionTotal = 0
        var sessionPqc = 0
        var sessionClassical = 0
        var sessionErrors = 0

        for (log in currentLogs) {
            val isError = log.errorMessage != null || (log.statusCode != null && log.statusCode >= 400)
            if (isError) allErrors++

            when (log.negotiatedKeyExchange) {
                KeyExchangeType.PQC, KeyExchangeType.HYBRID_PQC -> allPqc++
                KeyExchangeType.CLASSICAL -> allClassical++
                else -> {}
            }

            // Check if log belongs to current session
            if (log.sessionId == currentSessionId || log.timestamp >= currentSessionStartTime) {
                sessionTotal++
                if (isError) sessionErrors++
                when (log.negotiatedKeyExchange) {
                    KeyExchangeType.PQC, KeyExchangeType.HYBRID_PQC -> sessionPqc++
                    KeyExchangeType.CLASSICAL -> sessionClassical++
                    else -> {}
                }
            }
        }

        _allTimeStats.value = ConnectionStats(
            totalConnections = allTotal,
            pqcNegotiatedCount = allPqc,
            classicalNegotiatedCount = allClassical,
            errorCount = allErrors,
            sessionStartTime = currentSessionStartTime
        )

        _currentSessionStats.value = ConnectionStats(
            totalConnections = sessionTotal,
            pqcNegotiatedCount = sessionPqc,
            classicalNegotiatedCount = sessionClassical,
            errorCount = sessionErrors,
            sessionStartTime = currentSessionStartTime
        )
    }

    fun generateNextId(): Int = counter.getAndIncrement()
}
