package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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
}
