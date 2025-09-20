package com.kop.app; 

import com.bihe0832.android.lib.ffmpeg.FFmpeg;

import java.io.File; 

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

        // This library uses a simple, direct `exec` command.
        // It returns 0 for success, and a non-zero value for failure.
        int returnCode = FFmpeg.getInstance().exec(command);

        // Check if the command was successful. If not, throw an exception.
        if (returnCode != 0) {
            // This library does not provide detailed logs on failure, so we include the return code.
            throw new Exception("FFmpeg frame extraction failed with return code: " + returnCode);
        }
    }
}
