package com.kop.app;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;

public class FrameExtractor {

    /**
     * Extracts frames from a video file at a specified frames-per-second rate with high accuracy.
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

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long videoDurationMs = Long.parseLong(durationStr);

            // Calculate the interval between frames in microseconds.
            long intervalUs = 1000000 / fps;

            int frameIndex = 0;
            // Loop through the video's duration, stepping by the calculated interval.
            for (long timeUs = 0; timeUs < videoDurationMs * 1000; timeUs += intervalUs) {
                
                // Retrieve the frame at the specific time.
                // Using OPTION_CLOSEST is more accurate than OPTION_CLOSEST_SYNC because it finds
                // the frame closest to the requested time, not just the nearest keyframe.
                // This is essential for high-quality, smooth frame-by-frame processing.
                Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                
                if (frame != null) {
                    // Save frames as PNG to preserve sharpness and avoid JPEG compression artifacts.
                    // This is critical for getting clean input for our line art processor.
                    String filename = String.format("%s/frame_%05d.png", outDir, frameIndex++);
                    FileOutputStream out = null;
                    try {
                        out = new FileOutputStream(filename);
                        frame.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                        // It is crucial to recycle the bitmap to free up memory immediately.
                        frame.recycle();
                    }
                }
            }
        } finally {
            // Correctly release or close the retriever based on Android version.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.close();
            } else {
                retriever.release();
            }
        }
    }
}
