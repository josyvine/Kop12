package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.mlkit.vision.segmentation.SegmentationMask;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class ImageProcessor {

    /**
     * Extracts a detailed line drawing of a person from a bitmap.
     * 1. Uses the ML Kit mask to remove the background.
     * 2. Uses OpenCV Canny Edge Detector to find all detailed lines on the person.
     * 3. Returns a new bitmap containing ONLY the black lines on a transparent background.
     */
    public static Bitmap extractOutlineFromMask(Bitmap originalBitmap, SegmentationMask mask) {
        if (mask == null) {
            return null;
        }

        // --- Step 1: Create a new bitmap with only the segmented person on a transparent background ---
        Bitmap personBitmap = createPersonOnlyBitmap(originalBitmap, mask);
        if (personBitmap == null) {
            return null;
        }

        // --- Step 2: Convert the person-only bitmap to an OpenCV Mat data structure ---
        Mat originalMat = new Mat();
        Utils.bitmapToMat(personBitmap, originalMat);

        // --- Step 3: Convert the image to grayscale, which is required for edge detection ---
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // --- Step 4: Apply a blur to reduce noise and improve the quality of edge detection ---
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);

        // --- Step 5: Use the powerful Canny Edge Detector to find all the detailed outlines ---
        Mat cannyEdges = new Mat();
        double threshold1 = 50;
        double threshold2 = 150;
        Imgproc.Canny(blurredMat, cannyEdges, threshold1, threshold2);

        // --- Step 6: Create the final output image (black lines on a transparent background) ---
        // Create a new image that is completely transparent.
        Mat finalMat = new Mat(cannyEdges.size(), originalMat.type(), new Scalar(0, 0, 0, 0));
        
        // Define the color black for the lines.
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        
        // Use the outlines from the Canny detector as a "stencil" to draw black lines onto our transparent image.
        finalMat.setTo(blackColor, cannyEdges);

        // --- Step 7: Convert the final OpenCV Mat back to an Android Bitmap ---
        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        // --- Step 8: Clean up memory by releasing all the intermediate image objects ---
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        cannyEdges.release();
        finalMat.release();
        personBitmap.recycle();

        return resultBitmap;
    }

    /**
     * Helper method to create a bitmap containing only the person, with a transparent background.
     */
    private static Bitmap createPersonOnlyBitmap(Bitmap originalBitmap, SegmentationMask mask) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        ByteBuffer maskBuffer = mask.getBuffer();
        int maskWidth = mask.getWidth();
        int maskHeight = mask.getHeight();
        float confidenceThreshold = 0.8f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Scale coordinates to match mask dimensions
                float scaleX = (float) x / width;
                float scaleY = (float) y / height;
                int maskX = (int) (scaleX * maskWidth);
                int maskY = (int) (scaleY * maskHeight);

                // Calculate the position in the ByteBuffer (each float is 4 bytes)
                int position = (maskY * maskWidth + maskX) * 4;
                maskBuffer.rewind();

                if (position < maskBuffer.limit()) {
                    float confidence = maskBuffer.getFloat(position);
                    if (confidence >= confidenceThreshold) {
                        // If it's part of the person, copy the pixel from the original image.
                        resultBitmap.setPixel(x, y, originalBitmap.getPixel(x, y));
                    } else {
                        // If it's the background, make the pixel transparent.
                        resultBitmap.setPixel(x, y, Color.TRANSPARENT);
                    }
                } else {
                     resultBitmap.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }
        return resultBitmap;
    }

    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
        if (bmp == null) {
            // Do not attempt to save a null bitmap, which could happen if processing fails.
            return;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
