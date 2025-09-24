package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import java.io.FileOutputStream;
import java.util.List;

// --- START OF ADDED IMPORTS FOR NEW METHOD ---
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
// --- END OF ADDED IMPORTS FOR NEW METHOD ---


/**
 * A utility class for handling bitmap operations, such as saving to a file.
 * The core processing logic has been moved to DeepScanProcessor.
 */
public class ImageProcessor {

    /**
     * Saves a bitmap to a specified file path as a PNG.
     * This method is now the sole responsibility of this class.
     *
     * @param bmp The bitmap to save.
     * @param path The absolute path where the file will be saved.
     * @throws Exception if there is an error during file writing.
     */
    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
        if (bmp == null) {
            return;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            // Using PNG format to preserve the sharpness of the final line art.
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    // --- START OF NEW METHOD FOR TASK 2 (AI-Guided Scanning) ---

    /**
     * Creates a black-and-white mask bitmap from a list of rectangular regions.
     * The resulting bitmap will be black, with white rectangles drawn at the specified locations.
     * This is used to guide the processing algorithms to focus on specific areas.
     *
     * @param width The width of the mask to create.
     * @param height The height of the mask to create.
     * @param rects A list of Rect objects defining the areas to be marked in white.
     * @return A new Bitmap representing the mask.
     */
    public static Bitmap createMaskFromRects(int width, int height, List<Rect> rects) {
        // Create a new mutable bitmap, defaulting to all black.
        Bitmap maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        maskBitmap.eraseColor(Color.BLACK);

        // If there are no rectangles, we can just return the black bitmap.
        if (rects == null || rects.isEmpty()) {
            return maskBitmap;
        }

        Canvas canvas = new Canvas(maskBitmap);
        Paint whitePaint = new Paint();
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL);

        // Iterate through the list of rectangles and draw each one onto the canvas in white.
        for (Rect rect : rects) {
            // Ensure the rectangle is within the bitmap bounds before drawing to prevent crashes.
            Rect clippedRect = new Rect(rect);
            if (clippedRect.intersect(0, 0, width, height)) {
                 canvas.drawRect(clippedRect, whitePaint);
            }
        }

        return maskBitmap;
    }
    // --- END OF NEW METHOD ---

    // --- START OF NEW METHOD FOR AI CONTROL SYSTEM (Version 2) ---

    /**
     * Creates a unified, solid black-and-white mask from a list of potentially disconnected
     * rectangular regions by merging them using morphological operations.
     * This is used to create a cohesive silhouette to guide the processing.
     *
     * @param width The width of the mask to create.
     * @param height The height of the mask to create.
     * @param rects A list of Rect objects defining the areas to be marked in white.
     * @return A new Bitmap representing the unified mask.
     */
    public static Bitmap createUnifiedMaskFromRects(int width, int height, List<Rect> rects) {
        // Step 1: Call the existing method to get the base bitmap with separate rectangles.
        Bitmap initialBitmap = createMaskFromRects(width, height, rects);
        if (rects == null || rects.isEmpty()) {
            return initialBitmap; // Return the all-black bitmap directly.
        }

        Mat maskMat = new Mat();
        Mat grayMat = new Mat();
        Mat kernel = null;
        try {
            // Step 2: Convert the bitmap to an OpenCV Mat.
            Utils.bitmapToMat(initialBitmap, maskMat);

            // Step 3: Convert to a single-channel grayscale image.
            Imgproc.cvtColor(maskMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

            // Step 4: Create a large rectangular kernel to close gaps.
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(25, 25));

            // Step 5: Use MORPH_CLOSE to fill gaps between the white rectangles.
            Imgproc.morphologyEx(grayMat, grayMat, Imgproc.MORPH_CLOSE, kernel);

            // Step 6: Convert the final, unified Mat back into a Bitmap.
            Bitmap finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(grayMat, finalBitmap);

            return finalBitmap;
        } finally {
            // Step 7: Ensure all intermediate resources are properly released.
            if (initialBitmap != null) {
                initialBitmap.recycle();
            }
            maskMat.release();
            grayMat.release();
            if (kernel != null) {
                kernel.release();
            }
        }
    }
    // --- END OF NEW METHOD ---
}
