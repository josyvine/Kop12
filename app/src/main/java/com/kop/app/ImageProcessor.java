package com.kop.app;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageProcessor {

    /**
     * Extracts outlines from a bitmap using a robust multi-stage OpenCV pipeline.
     * This method is designed to find all significant objects in a scene, not just people.
     *
     * @param originalBitmap The input image.
     * @param listener A listener to receive status updates during the process.
     * @return A new bitmap containing the black outlines on a white background.
     */
    public static Bitmap extractOutline(Bitmap originalBitmap, StatusListener listener) {
        if (originalBitmap == null) {
            return null;
        }

        // --- STAGE 1: PREPARATION ---
        listener.onStatusUpdate("Preparing Image...");
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);

        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);

        // --- STAGE 2: ADAPTIVE THRESHOLDING ---
        // This is key to separating objects from backgrounds in varied lighting.
        // It calculates a local threshold for each region of the image.
        listener.onStatusUpdate("Detecting Object Regions...");
        Mat threshMat = new Mat();
        Imgproc.adaptiveThreshold(blurredMat, threshMat, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);

        // --- STAGE 3: CONTOUR DETECTION ---
        // Find the outlines of all the shapes identified in the thresholding stage.
        listener.onStatusUpdate("Finding All Contours...");
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(threshMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // --- STAGE 4: CONTOUR FILTERING ---
        // Remove very small contours that are likely noise, keeping only significant objects.
        listener.onStatusUpdate("Filtering Noise...");
        List<MatOfPoint> filteredContours = new ArrayList<>();
        double minContourArea = (double)(originalMat.width() * originalMat.height()) * 0.0005; // 0.05% of image area
        for (MatOfPoint contour : contours) {
            if (Imgproc.contourArea(contour) > minContourArea) {
                filteredContours.add(contour);
            }
        }

        // --- STAGE 5: DRAWING THE FINAL IMAGE ---
        // Create a blank white canvas and draw the filtered contours onto it.
        listener.onStatusUpdate("Drawing Final Outlines...");
        Mat finalMat = new Mat(originalMat.size(), originalMat.type(), new Scalar(255, 255, 255, 255));
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        // Draw with a thickness of 2 for a sharp, clear line.
        Imgproc.drawContours(finalMat, filteredContours, -1, blackColor, 2);

        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        // IMPORTANT: Release all Mat objects to free up memory.
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        threshMat.release();
        hierarchy.release();
        finalMat.release();
        for(MatOfPoint p : contours) p.release(); // Release all contour mats
        // filteredContours is a subset of contours, so its members are already released.

        return resultBitmap;
    }

    /**
     * Saves a bitmap to a specified file path as a PNG.
     */
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

    /**
     * A simple listener interface for providing status updates during processing.
     */
    public interface StatusListener {
        void onStatusUpdate(String status);
    }
} 
 