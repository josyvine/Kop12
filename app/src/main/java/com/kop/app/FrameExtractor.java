package com.kop.app; 

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class FrameExtractor {

    private static final String TAG = "FrameExtractorFFmpeg";

    /**
     * Extracts frames from a video file at a specified frames-per-second rate using an ffmpeg binary.
     * @param context   The application context, needed to locate the ffmpeg binary.
     * @param videoPath Absolute path to the video file.
     * @param outDir    The directory where the extracted frames will be saved.
     * @param fps       The desired number of frames to extract per second.
     * @throws Exception if there is an error during extraction.
     */
    public static void extractFrames(Context context, String videoPath, String outDir, int fps) throws Exception {
        File outputDir = new File(outDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File ffmpegFile = new File(context.getFilesDir(), "ffmpeg");
        if (!ffmpegFile.exists() || !ffmpegFile.canExecute()) {
            throw new Exception("ffmpeg executable not found or not executable. Please ensure it was copied correctly on app startup. Expected path: " + ffmpegFile.getAbsolutePath());
        }

        // The output pattern uses %05d to maintain compatibility with the app's frame sorting logic.
        String outputPattern = new File(outDir, "frame_%05d.png").getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
            ffmpegFile.getAbsolutePath(),
            "-y", // Overwrite output files if they exist
            "-i",
            videoPath,
            "-vf",
            "fps=" + fps,
            outputPattern
        );

        Log.d(TAG, "Executing FFMPEG command: " + pb.command().toString());

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // This block reads ffmpeg's output. It is useful for debugging.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "ffmpeg: " + line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            Log.d(TAG, "ffmpeg process completed successfully.");
        } else {
            Log.e(TAG, "ffmpeg process failed with exit code: " + exitCode);
            throw new Exception("ffmpeg process failed. Check logs for details.");
        }
    }
}
