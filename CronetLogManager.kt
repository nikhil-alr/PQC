package com.example.myapplication.network

import android.content.Context
import android.util.Log
import org.chromium.net.CronetEngine
import java.io.File

object CronetLogManager {

    private const val TAG = "CronetLogManager"
    private const val DUMP_FILE_NAME = "cronet_netlog.json"
    
    private var dumpFile: File? = null
    private var isLoggingActive = false

    fun startNetLog(context: Context, cronetEngine: CronetEngine) {
        try {
            // Keep dump file in filesDir for persistent file storage
            val filesDir = context.filesDir
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }
            
            dumpFile = File(filesDir, DUMP_FILE_NAME)
            
            // Delete old dump on new session start if needed
            if (dumpFile?.exists() == true) {
                dumpFile?.delete()
            }

            // Start Cronet native file logging
            cronetEngine.startNetLogToFile(dumpFile?.absolutePath, true)
            isLoggingActive = true
            Log.d(TAG, "Cronet native NetLog dumping started at file path: ${dumpFile?.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Cronet NetLog dumping", e)
        }
    }

    fun stopNetLog(cronetEngine: CronetEngine) {
        if (!isLoggingActive) return
        try {
            cronetEngine.stopNetLog()
            isLoggingActive = false
            Log.d(TAG, "Cronet NetLog dumping stopped & flushed to disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Cronet NetLog", e)
        }
    }

    fun isLogging(): Boolean = isLoggingActive

    fun getDumpFilePath(): String {
        return dumpFile?.absolutePath ?: "Not Initialized"
    }

    fun getDumpFile(): File? = dumpFile

    /**
     * Flushes & parses the local Cronet dump file and populates ConnectionLogRepository.
     */
    fun parseAndLoadDumpFile(cronetEngine: CronetEngine? = null): Int {
        if (isLoggingActive && cronetEngine != null) {
            try {
                // Stop to flush data to disk, then restart
                cronetEngine.stopNetLog()
                cronetEngine.startNetLogToFile(dumpFile?.absolutePath, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing NetLog during parse", e)
            }
        }

        val file = dumpFile ?: return 0
        val parsedLogs = CronetDumpParser.parseNetLogFile(file)

        ConnectionLogRepository.clearLogs()
        for (log in parsedLogs) {
            ConnectionLogRepository.addLog(log)
        }

        return parsedLogs.size
    }
}
