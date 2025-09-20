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

        // This is the high-speed FFmpeg command as a single string.
        // -i [videoPath]       -> Sets the input video file.
        // -y                   -> Overwrite output files without asking.
        // -vf fps=[fps]        -> Sets a video filter (-vf) to force the output frame rate to the desired fps.
        // [outDir]/frame...png -> Sets the output location and filename pattern.
        //                        %05d creates a 5-digit number (00001, 00002, etc.).
        String command = String.format("-i \"%s\" -y -vf fps=%d \"%s/frame_%%05d.png\"", videoPath, fps, outDir);

        // This library is asynchronous. We use a CountDownLatch to make our method wait for it to finish.
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger returnCode = new AtomicInteger(-1); // Use AtomicInteger to store result from callback

        // THIS IS THE CORRECT IMPLEMENTATION USING FFmpegTools.exec, AS YOU INSTRUCTED.
        FFmpegTools.exec(command,
                // onSuccess callback
                new AAFDataCallback() {
                    @Override
                    public void onCallback(Object... args) {
                        returnCode.set(0); // Set 0 for success
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
                            returnCode.set(-1); // General failure
                        }
                        latch.countDown();
                    }
                }
        );

        // Wait here until the latch is counted down by one of the callbacks.
        latch.await();

        // Check if the command was successful. A return code of 0 means success.
        if (returnCode.get() != 0) {
            throw new Exception("FFmpeg frame extraction failed with return code: " + returnCode.get());
        }
    }
}
