package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.util.List;

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

    // --- START OF NEW, INTELLIGENT MASK CREATION METHOD ---
    /**
     * Creates an intelligent, unified mask from a list of disconnected rectangular regions.
     * This method merges separate rectangles (e.g., for a head, torso, arm) into a single,
     * cohesive silhouette using morphological operations, which prevents "patchy" artifacts.
     *
     * @param width The width of the mask to create.
     * @param height The height of the mask to create.
     * @param rects A list of Rect objects defining the parts of the subject.
     * @return A new Bitmap representing a single, solid mask of the subject.
     */
    public static Bitmap createUnifiedMaskFromRects(int width, int height, List<Rect> rects) {
        // Step 1: Get the initial bitmap with disconnected white rectangles.
        Bitmap initialMaskBitmap = createMaskFromRects(width, height, rects);
        if (rects == null || rects.isEmpty()) {
            return initialMaskBitmap; // Return the empty black mask immediately.
        }

        Mat maskMat = new Mat();
        Mat grayMat = new Mat();
        Mat kernel = null;
        Bitmap finalMaskBitmap = null;

        try {
            // Step 2: Convert the initial bitmap to an OpenCV Mat.
            Utils.bitmapToMat(initialMaskBitmap, maskMat);

            // Step 3: Convert to a single-channel grayscale image.
            Imgproc.cvtColor(maskMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

            // Step 4: Create a large structuring element (kernel) to close gaps.
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(25, 25));

            // Step 5: Perform a morphological closing operation. This is the key step that
            // fills in the gaps and merges the separate rectangles into one solid shape.
            Imgproc.morphologyEx(grayMat, grayMat, Imgproc.MORPH_CLOSE, kernel);

            // Step 6: Convert the final, unified Mat back into a Bitmap.
            // We need to convert it back to RGBA for it to be compatible as a standard bitmap.
            Imgproc.cvtColor(grayMat, maskMat, Imgproc.COLOR_GRAY2RGBA);
            finalMaskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(maskMat, finalMaskBitmap);

        } finally {
            // Step 7: Ensure all intermediate Mats and the initial bitmap are properly released.
            if (maskMat != null) maskMat.release();
            if (grayMat != null) grayMat.release();
            if (kernel != null) kernel.release();
            if (initialMaskBitmap != null) initialMaskBitmap.recycle();
        }

        return finalMaskBitmap;
    }
    // --- END OF NEW, INTELLIGENT MASK CREATION METHOD ---
}
