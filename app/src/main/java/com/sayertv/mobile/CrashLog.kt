/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zero-dependency crash reporter for alpha testing without adb/Android Studio.
 * Uncaught exceptions are written to files/last_crash.txt; on the next launch
 * the app shows the report in a copyable dialog so testers can paste it back
 * to the dev team.
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun install(context: Context, appVersion: String) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file(appContext).writeText(
                    buildString {
                        appendLine("S.E.E.D TV Mobile $appVersion — crash report")
                        appendLine("Time: $stamp")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    },
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun pendingReport(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
