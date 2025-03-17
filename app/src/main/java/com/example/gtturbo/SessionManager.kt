package com.example.gtturbo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * SessionManager handles persistent storage of session state
 * It keeps track of whether a session is in progress even if the app is killed
 */
class SessionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "gtturbo_prefs"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_START_TIME = "session_start_time"
        private const val KEY_SESSION_FILENAME = "session_filename"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if a session is currently in progress
     */
    fun isSessionActive(): Boolean {
        return prefs.getBoolean(KEY_SESSION_ACTIVE, false)
    }
    
    /**
     * Start a new session and save state to SharedPreferences
     */
    fun startSession() {
        val currentTime = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putLong(KEY_SESSION_START_TIME, currentTime)
            .apply()
        
        Log.d(TAG, "Session started and saved to persistent storage")
    }
    
    /**
     * End the current session and update SharedPreferences
     * @param filename Optional filename where session data was saved
     */
    fun endSession(filename: String = "") {
        prefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .putString(KEY_SESSION_FILENAME, filename)
            .apply()
        
        Log.d(TAG, "Session ended and state updated in persistent storage")
    }
    
    /**
     * Get the timestamp when the current session was started
     * @return the timestamp in milliseconds, or 0 if no session is active
     */
    fun getSessionStartTime(): Long {
        return if (isSessionActive()) {
            prefs.getLong(KEY_SESSION_START_TIME, 0)
        } else {
            0
        }
    }
    
    /**
     * Check if any active sessions exist by scanning the data directory
     * This is a backup method in case the SharedPreferences data is lost
     */
    fun checkForActiveSessionsInFileSystem(): Boolean {
        // If SharedPreferences already indicates an active session, no need to check files
        if (isSessionActive()) {
            return true
        }
        
        // Get the app's external files directory
        val fileDir = context.getExternalFilesDir(null)
        if (fileDir != null && fileDir.exists()) {
            // Search for any temporary session files that might indicate an incomplete session
            val sessionMarkerFile = File(fileDir, "current_session.tmp")
            if (sessionMarkerFile.exists()) {
                // Found a marker file, assume session is active
                startSession() // Update SharedPreferences to reflect this
                return true
            }
        }
        
        return false
    }
    
    /**
     * Create a temporary file to mark an active session (backup mechanism)
     */
    fun createSessionMarkerFile() {
        val fileDir = context.getExternalFilesDir(null)
        if (fileDir != null) {
            val sessionMarkerFile = File(fileDir, "current_session.tmp")
            try {
                sessionMarkerFile.createNewFile()
                Log.d(TAG, "Created session marker file: ${sessionMarkerFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating session marker file", e)
            }
        }
    }
    
    /**
     * Remove the temporary marker file when a session ends
     */
    fun removeSessionMarkerFile() {
        val fileDir = context.getExternalFilesDir(null)
        if (fileDir != null) {
            val sessionMarkerFile = File(fileDir, "current_session.tmp")
            if (sessionMarkerFile.exists()) {
                sessionMarkerFile.delete()
                Log.d(TAG, "Removed session marker file")
            }
        }
    }
} 