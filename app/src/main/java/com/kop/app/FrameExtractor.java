package com.kop.app;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.io.FileOutputStream;

public class FrameExtractor {

    private static final String TAG = "FrameExtractorOpenCV";

    /**
     * Extracts frames from a video file at a specified frames-per-second rate using the high-performance OpenCV library.
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

        VideoCapture cap = new VideoCapture();
        cap.open(videoPath);

        if (!cap.isOpened()) {
            throw new Exception("Error: Could not open video file with OpenCV: " + videoPath);
        }

        Mat frame = new Mat();
        double videoFps = cap.get(Videoio.CAP_PROP_FPS);
        if (videoFps <= 0) {
            // If FPS is not available, default to 30 to avoid division by zero.
            videoFps = 30.0;
            Log.w(TAG, "Could not get video FPS. Defaulting to 30.");
        }

        // Calculate how many frames to skip.
        // For example, if video is 30 FPS and we want 10 FPS, we need to save every 3rd frame (30 / 10 = 3).
        double frameInterval = videoFps / fps;
        if (frameInterval < 1) {
            frameInterval = 1; // Cannot skip less than one frame.
        }

        int frameCounter = 0;
        int savedFrameIndex = 0;
        double nextFrameToSave = 0.0;

        // Loop through all frames in the video by reading them sequentially. This is very fast.
        while (cap.read(frame)) {
            if (frame.empty()) {
                Log.w(TAG, "Encountered an empty frame.");
                continue;
            }

            // Check if the current frame is one we need to save.
            if (frameCounter >= nextFrameToSave) {
                Bitmap bmp = null;
                FileOutputStream out = null;
                try {
                    // Convert the frame (which OpenCV reads as BGR) to a standard RGBA Bitmap.
                    Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2RGBA);
                    bmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(frame, bmp);

                    // Save the bitmap as a PNG file.
                    String filename = String.format("%s/frame_%05d.png", outDir, savedFrameIndex++);
                    out = new FileOutputStream(filename);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

                    // Set the next frame number to save.
                    nextFrameToSave += frameInterval;

                } finally {
                    if (bmp != null) {
                        bmp.recycle();
                    }
                    if (out != null) {
                        out.close();
                    }
                }
            }
            frameCounter++;
        }

        // Release all resources.
        if (frame != null) {
            frame.release();
        }
        if (cap != null) {
            cap.release();
        }
    }
}
