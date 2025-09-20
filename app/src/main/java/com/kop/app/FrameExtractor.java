package com.kop.app;

import com.bihe0832.android.ffmpeg.FFmpegWrapper;
import com.bihe0832.android.ffmpeg.OnFFmpegListener;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class FrameExtractor {

    /**
     * Extracts frames from a video file at a specified frames-per-second rate using the high-performance FFmpeg library.
     * @param videoPath Absolute path to the video file.
     * @param outDir    The directory where the extracted frames will be saved.
     * @param fps       The desired number of frames to extract per second.
     * @throws Exception if there is an error during extraction.
     */
    public static void extractFrames(String videoPath, String outDir, int fps) throws Exception {
        File outputDir = new File(outDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // This library requires the command to be split into an array of strings.
        String[] command = new String[]{
                "-i",
                videoPath,
                "-y", // Overwrite output files without asking
                "-vf",
                "fps=" + fps,
                outDir + "/frame_%05d.png"
        };

        // This library is asynchronous. We use a CountDownLatch to make our method wait for it to finish.
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        final StringBuilder logBuilder = new StringBuilder();

        // This is the real, correct way to execute a command with this library.
        FFmpegWrapper.execute(command, new OnFFmpegListener() {
            @Override
            public void onStart() {
                // Method is called when the command starts.
            }

            @Override
            public void onProgress(int frame, int totalFrame) {
                // Method is called during processing, not needed here.
            }

            @Override
            public void onSuccess() {
                success.set(true);
                latch.countDown(); // Signal that the command is finished.
            }

            @Override
            public void onCancel() {
                success.set(false);
                logBuilder.append("FFmpeg command was cancelled.");
                latch.countDown(); // Signal that the command is finished.
            }

            @Override
            public void onFailure(String message) {
                success.set(false);
                logBuilder.append("FFmpeg command failed: ").append(message);
                latch.countDown(); // Signal that the command is finished.
            }
        });

        // Wait here until the latch is counted down by onSuccess, onFailure, or onCancel.
        latch.await();

        // Check if the command was successful. If not, throw an exception with the logs.
        if (!success.get()) {
            throw new Exception("FFmpeg frame extraction failed. Log: " + logBuilder.toString());
        }
    }
}
