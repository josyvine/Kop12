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
        File outputDir = new File(outDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long videoDurationMs = Long.parseLong(durationStr);

            long intervalUs = 1000000 / fps;

            int frameIndex = 0;
            for (long timeUs = 0; timeUs < videoDurationMs * 1000; timeUs += intervalUs) {
                
                Bitmap frame = null;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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
                        frame.recycle();
                    }
                }
            }
        } finally {
            if (retriever != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever.close();
                } else {
                    retriever.release();
                }
            }
        }
    }
}
