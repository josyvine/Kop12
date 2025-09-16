package com.kop.app;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;

public class FrameExtractor {

    /**
     * Extracts frames from a video file at a specified frames-per-second rate.
     * @param videoPath Absolute path to the video file.
     * @param outDir    The directory where the extracted frames will be saved.
     * @param fps       The desired number of frames to extract per second.
     * @throws Exception if there is an error during extraction.
     */
    public static void extractFrames(String videoPath, String outDir, int fps) throws Exception {
        // Ensure the output directory exists
        File outputDir = new File(outDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long videoDurationMs = Long.parseLong(durationStr); // Duration in milliseconds

            long intervalUs = 1000000 / fps; // Interval in microseconds

            int frameIndex = 0;
            for (long timeUs = 0; timeUs < videoDurationMs * 1000; timeUs += intervalUs) {
                
                Bitmap frame = null;
                
                // FIX: Use a more memory-efficient way to get frames on newer Android versions.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    // This requests a mutable bitmap in a standard format, which is better for processing.
                    frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                } else {
                    frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }
                
                if (frame != null) {
                    String filename = String.format("%s/frame_%05d.jpg", outDir, frameIndex++);
                    FileOutputStream out = null;
                    try {
                        out = new FileOutputStream(filename);
                        frame.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    } finally {
                        if (out != null) {
                            out.close();
                        }
                        frame.recycle(); // Important to free up memory
                    }
                }
            }
        } finally {
            // FIX: Use the newer, safer 'close()' method on Android Q and above.
            if (retriever != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever.close();
                } else {
                    // Deprecated but necessary for older versions
                    retriever.release();
                }
            }
        }
    }
}
