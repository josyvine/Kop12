package com.kop.app;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugLogger {

    private static final String TAG = "DebugLogger";
    private static File logFile;

    public static void initialize() {
        try {
            File debugDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/debug_reports");
            if (!debugDir.exists()) {
                debugDir.mkdirs();
            }
            logFile = new File(debugDir, "gl_debug_report.txt");
            // Clear the old log file at the start of a new session
            if (logFile.exists()) {
                logFile.delete();
            }
            logFile.createNewFile();
            logMessage("DebugLogger initialized at " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize DebugLogger", e);
            logFile = null;
        }
    }

    public static void logMessage(String message) {
        Log.d(TAG, message); // Also log to standard Logcat
        if (logFile == null) {
            return;
        }
        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            writer.append("[").append(timestamp).append("] ").append(message).append("\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }
}
