package com.aismartmeasure.app

import android.app.Application
import android.content.Context

class AISmartMeasureApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Set up crash handler to save crash info
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Save crash info to SharedPreferences
            try {
                val prefs = getSharedPreferences("crash_log", Context.MODE_PRIVATE)
                val crashLog = buildString {
                    appendLine("=== CRASH LOG ===")
                    appendLine("Time: ${System.currentTimeMillis()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.simpleName}")
                    appendLine("Message: ${throwable.message}")
                    appendLine("")
                    appendLine("Stack trace:")
                    throwable.stackTrace.take(15).forEach { element ->
                        appendLine("  at $element")
                    }
                    throwable.cause?.let { cause ->
                        appendLine("")
                        appendLine("Caused by: ${cause.javaClass.simpleName}")
                        appendLine("Message: ${cause.message}")
                        cause.stackTrace.take(10).forEach { element ->
                            appendLine("  at $element")
                        }
                    }
                }
                prefs.edit().putString("last_crash", crashLog).apply()
            } catch (e: Exception) {
                // Ignore
            }
            
            // Call default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    companion object {
        fun getLastCrash(context: Context): String? {
            val prefs = context.getSharedPreferences("crash_log", Context.MODE_PRIVATE)
            return prefs.getString("last_crash", null)
        }
        
        fun clearCrashLog(context: Context) {
            val prefs = context.getSharedPreferences("crash_log", Context.MODE_PRIVATE)
            prefs.edit().remove("last_crash").apply()
        }
    }
}
