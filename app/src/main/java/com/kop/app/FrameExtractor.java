package com.kop.app;

import com.bihe0832.android.lib.aaf.tools.AAFDataCallback;
import com.bihe0832.android.lib.aaf.tools.FFmpegTools;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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

        // Build the FFmpeg command
        String command = String.format("-i \"%s\" -y -vf fps=%d \"%s/frame_%%05d.png\"", videoPath, fps, outDir);

        // Use CountDownLatch to wait for asynchronous execution
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger returnCode = new AtomicInteger(-1);

        // Execute the FFmpeg command using the correct library
        FFmpegTools.exec(command,
            // onSuccess callback
            new AAFDataCallback() {
                @Override
                public void onCallback(Object... args) {
                    returnCode.set(0); // success
                    latch.countDown();
                }
            },
            // onFailure callback
            new AAFDataCallback() {
                @Override
                public void onCallback(Object... args) {
                    if (args.length > 0 && args[0] instanceof Integer) {
                        returnCode.set((Integer) args[0]);
                    } else {
                        returnCode.set(-1);
                    }
                    latch.countDown();
                }
            }
        );

        // Wait until the command finishes
        latch.await();

        // Throw exception if failed
        if (returnCode.get() != 0) {
            throw new Exception("FFmpeg frame extraction failed with return code: " + returnCode.get());
        }
    }
}