package com.kop.app;

import com.bihe0832.android.lib.ffmpeg.FFMpegTools;
import com.bihe0832.android.lib.ffmpeg.callback.IFFMpegCallback;

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

        // This is the high-speed FFmpeg command.
        // -i [videoPath]       -> Sets the input video file.
        // -vf fps=[fps]        -> Sets a video filter (-vf) to force the output frame rate to the desired fps.
        // [outDir]/frame...png -> Sets the output location and filename pattern.
        //                        %05d creates a 5-digit number (00001, 00002, etc.).
        String command = String.format("-i \"%s\" -vf fps=%d \"%s/frame_%%05d.png\"", videoPath, fps, outDir);

        // This new library runs asynchronously and uses a callback.
        // We use a CountDownLatch to wait for the command to finish.
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        final StringBuilder logBuilder = new StringBuilder();

        FFMpegTools.getInstance().execute(command, new IFFMpegCallback() {
            @Override
            public void onStart() {
                // Command has started.
            }

            @Override
            public void onProgress(String progress) {
                // Not needed for this task.
            }

            @Override
            public void onCancel() {
                logBuilder.append("FFmpeg command was cancelled.");
                latch.countDown();
            }

            @Override
            public void onComplete() {
                success.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                logBuilder.append("FFmpeg command failed with error: ").append(message);
                latch.countDown();
            }
        });

        // Wait for the FFmpeg command to complete.
        latch.await();

        // Check if the command was successful. If not, throw an exception.
        if (!success.get()) {
            throw new Exception("FFmpeg frame extraction failed. Log: " + logBuilder.toString());
        }
    }
}
