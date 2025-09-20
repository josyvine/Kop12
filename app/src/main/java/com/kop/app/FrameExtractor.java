package com.kop.app;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Session;

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
        // -vf fps=[fps]        -> Sets a video filter (-vf) to force the output frame rate to the desired fps.
        // [outDir]/frame...png -> Sets the output location and filename pattern.
        //                        %05d creates a 5-digit number (00001, 00002, etc.).
        String command = String.format("-i \"%s\" -vf fps=%d \"%s/frame_%%05d.png\"", videoPath, fps, outDir);

        // Execute the command synchronously.
        Session session = FFmpegKit.execute(command);

        // Check if the command was successful. If not, throw an exception.
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            // Include FFmpeg logs in the exception for easier debugging.
            String logs = session.getAllLogsAsString();
            throw new Exception("FFmpeg frame extraction failed. Return code: " + session.getReturnCode() + ". Log: " + logs);
        }
    }
}
