package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.io.FileOutputStream;

public class ImageProcessor {

    /**
     * FINAL VERSION: Processes the ENTIRE bitmap to extract a detailed line drawing.
     * This version no longer uses ML Kit segmentation.
     * 1. Converts the bitmap to grayscale.
     * 2. Uses OpenCV Canny Edge Detector to find all lines in the entire image.
     * 3. Returns a new bitmap containing black lines on a solid white background.
     */
    public static Bitmap extractOutline(Bitmap originalBitmap) {
        if (originalBitmap == null) {
            return null;
        }

        // --- Step 1: Convert the correctly-rotated bitmap to an OpenCV Mat data structure ---
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);

        // --- Step 2: Convert the image to grayscale, which is required for edge detection ---
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // --- Step 3: Apply a blur to reduce noise and improve the quality of edge detection ---
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);

        // --- Step 4: Use the powerful Canny Edge Detector to find all the detailed outlines ---
        Mat cannyEdges = new Mat();
        // These thresholds are tuned for better detail.
        double threshold1 = 50;
        double threshold2 = 150;
        Imgproc.Canny(blurredMat, cannyEdges, threshold1, threshold2);

        // --- Step 5: Create the final output image with a solid WHITE background for visibility ---
        Mat finalMat = new Mat(cannyEdges.size(), originalMat.type(), new Scalar(255, 255, 255, 255));
        
        // Define the color black for the lines.
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        
        // Use the outlines from the Canny detector as a "stencil" to draw black lines onto our white image.
        finalMat.setTo(blackColor, cannyEdges);

        // --- Step 6: Convert the final OpenCV Mat back to an Android Bitmap ---
        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        // --- Step 7: Clean up memory by releasing all the intermediate image objects ---
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        cannyEdges.release();
        finalMat.release();

        return resultBitmap;
    }

    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
        if (bmp == null) {
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
