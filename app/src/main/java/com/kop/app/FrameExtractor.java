package com.kop.app;

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

        // This is the high-speed FFmpeg command.
        // -i [videoPath]       -> Sets the input video file.
        // -y                   -> Overwrite output files without asking.
        // -vf fps=[fps]        -> Sets a video filter (-vf) to force the output frame rate to the desired fps.
        // [outDir]/frame...png -> Sets the output location and filename pattern.
        //                        %05d creates a 5-digit number (00001, 00002, etc.).
        String command = String.format("-i \"%s\" -y -vf fps=%d \"%s/frame_%%05d.png\"", videoPath, fps, outDir);

        // This library is asynchronous and uses a CountDownLatch to wait for completion.
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger returnCode = new AtomicInteger(-1); // Use AtomicInteger to store result from callback

        // The correct class is FFmpegTools. It does not use a callback interface.
        // We pass lambdas for success and failure directly.
        FFmpegTools.exec(command,
            // onSuccess callback
            new com.bihe0832.android.lib.aaf.tools.AAFDataCallback() {
                @Override
                public void onCallback(Object... o) {
                    returnCode.set(0); // Set 0 for success
                    latch.countDown();
                }
            },
            // onFailure callback
            new com.bihe0832.android.lib.aaf.tools.AAFDataCallback() {
                @Override
                public void onCallback(Object... o) {
                    if (o.length > 0 && o[0] instanceof Integer) {
                        returnCode.set((Integer) o[0]);
                    }
                    latch.countDown();
                }
            }
        );

        // Wait for the FFmpeg command to finish executing.
        latch.await();

        // Check if the command was successful. A return code of 0 means success.
        if (returnCode.get() != 0) {
            throw new Exception("FFmpeg frame extraction failed with return code: " + returnCode.get());
        }
    }
}
