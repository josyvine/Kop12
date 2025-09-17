package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class DeepScanProcessor {

    // Common classes for all methods
    public static class ProcessingResult {
        public final Bitmap resultBitmap;
        public final int objectsFound;

        ProcessingResult(Bitmap bitmap, int count) {
            this.resultBitmap = bitmap;
            this.objectsFound = count;
        }
    }

    public interface ScanListener {
        void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult);
        void onScanComplete(ProcessingResult finalResult);
    }

    // This method is a placeholder for the most advanced logic.
    // For now, it will be the same as Method 2 until we refine it further.
    public static void processMethod1(Bitmap originalBitmap, ScanListener listener) {
        // Currently calls Method 2 as the placeholder for "Utmost Best"
        processMethod2(originalBitmap, listener);
    }
    
    // Method 2: The "Selective Detail" Method (Hybrid Approach)
    public static void processMethod2(Bitmap originalBitmap, ScanListener listener) {
        // --- SETUP ---
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // --- PASS 1: AGGRESSIVE SIMPLIFICATION ---
        listener.onScanProgress(1, 5, "Pass 1/5: Simplifying Structure...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat simplifiedMat = getSimplifiedImage(grayMat);

        // --- PASS 2: FIND STRUCTURAL LINES ---
        Mat structuralLines = getCannyEdges(simplifiedMat, 5, 50);
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Structural Lines...", createBitmapFromMask(structuralLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        // --- PASS 3: FIND FINE DETAILS ---
        Mat detailLines = getCannyEdges(grayMat, 60, 120);
        listener.onScanProgress(3, 5, "Pass 3/5: Finding Fine Details...", createBitmapFromMask(structuralLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // --- PASS 4: INTELLIGENT COMBINATION ---
        Mat maskedDetails = new Mat();
        // Create a mask from the main structure to select details intelligently
        Mat structureMask = new Mat();
        Imgproc.dilate(structuralLines, structureMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));
        detailLines.copyTo(maskedDetails, structureMask);
        Core.bitwise_or(structuralLines, maskedDetails, structuralLines);
        listener.onScanProgress(4, 5, "Pass 4/5: Combining Lines...", createBitmapFromMask(structuralLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // --- PASS 5: FINALIZE ---
        Mat finalLines = finalizeLines(structuralLines);
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        // --- COMPLETE ---
        finalizeAndComplete(finalLines, listener);
        
        // Release Mats
        originalMat.release();
        grayMat.release();
        simplifiedMat.release();
        structuralLines.release();
        detailLines.release();
        maskedDetails.release();
        structureMask.release();
    }
    
    // Method 3: The "Artistic Abstraction" Method
    public static void processMethod3(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 5, "Pass 1/5: Abstracting Image...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat simplifiedMat = getSimplifiedImage(grayMat);
        
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Major Edges...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat majorEdges = getCannyEdges(simplifiedMat, 5, 50);
        
        listener.onScanProgress(3, 5, "Pass 3/5: Finding Detail Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat detailEdges = getCannyEdges(simplifiedMat, 60, 120);
        
        Core.bitwise_or(majorEdges, detailEdges, majorEdges);
        listener.onScanProgress(4, 5, "Pass 4/5: Combining Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        Mat finalLines = finalizeLines(majorEdges);
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        finalizeAndComplete(finalLines, listener);
        
        originalMat.release();
        grayMat.release();
        simplifiedMat.release();
        majorEdges.release();
        detailEdges.release();
    }

    // Method 4: The "Clean Structure" Method
    public static void processMethod4(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 5, "Pass 1/5: Smoothing Surfaces...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);
        
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Major Edges...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat majorEdges = getCannyEdges(blurredMat, 5, 50);
        
        listener.onScanProgress(3, 5, "Pass 3/5: Finding Detail Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat detailEdges = getCannyEdges(blurredMat, 60, 120);
        
        Core.bitwise_or(majorEdges, detailEdges, majorEdges);
        listener.onScanProgress(4, 5, "Pass 4/5: Combining Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        Mat finalLines = finalizeLines(majorEdges);
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        finalizeAndComplete(finalLines, listener);
        
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        majorEdges.release();
        detailEdges.release();
    }

    // Method 5: The "Detailed Texture" Method (The last version you approved)
    public static void processMethod5(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 5, "Pass 1/5: Simplifying Surfaces...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat simplifiedMat = new Mat();
        Imgproc.bilateralFilter(grayMat, simplifiedMat, 15, 80, 80);
        
        Mat accumulatedLines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        
        listener.onScanProgress(2, 5, "Pass 2/5: Tracing Large Shapes...", createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat largeShapes = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, largeShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 25, 2);
        Core.bitwise_or(accumulatedLines, largeShapes, accumulatedLines);
        
        listener.onScanProgress(3, 5, "Pass 3/5: Tracing Fine Details...", createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        Mat detailShapes = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, detailShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);
        Core.bitwise_or(accumulatedLines, detailShapes, accumulatedLines);
        
        Mat finalLines = finalizeLines(accumulatedLines);
        listener.onScanProgress(4, 5, "Pass 4/5: Cleaning Lines...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        
        finalizeAndComplete(finalLines, listener);
        
        originalMat.release();
        grayMat.release();
        simplifiedMat.release();
        accumulatedLines.release();
        largeShapes.release();
        detailShapes.release();
    }
    
    // --- HELPER FUNCTIONS ---

    private static Mat getSimplifiedImage(Mat grayMat) {
        Mat downscaled = new Mat();
        Imgproc.pyrDown(grayMat, downscaled);
        Mat blurred = new Mat();
        Imgproc.medianBlur(downscaled, blurred, 7);
        Mat upscaled = new Mat();
        Imgproc.pyrUp(blurred, upscaled);
        downscaled.release();
        blurred.release();
        return upscaled;
    }

    private static Mat getCannyEdges(Mat inputMat, double threshold1, double threshold2) {
        Mat edges = new Mat();
        Imgproc.Canny(inputMat, edges, threshold1, threshold2);
        return edges;
    }

    private static Mat finalizeLines(Mat inputLines) {
        Mat cleanedLines = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.morphologyEx(inputLines, cleanedLines, Imgproc.MORPH_CLOSE, kernel);
        
        Mat finalLines = new Mat();
        Imgproc.dilate(cleanedLines, finalLines, kernel);
        
        kernel.release();
        cleanedLines.release();
        
        return finalLines;
    }
    
    private static void finalizeAndComplete(Mat finalLines, ScanListener listener) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(finalLines, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        int totalObjects = contours.size();
        for (MatOfPoint p : contours) {
            p.release();
        }
        hierarchy.release();
        
        Bitmap finalBitmap = createBitmapFromMask(finalLines, finalLines.size());
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalObjects);
        listener.onScanComplete(finalResult);
        finalLines.release();
    }

    private static Bitmap createBitmapFromMask(Mat mask, Size originalSize) {
        Mat finalMat = new Mat(originalSize, CvType.CV_8UC4, new Scalar(255, 255, 255, 255));
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        finalMat.setTo(blackColor, mask);

        Bitmap bitmap = Bitmap.createBitmap((int)originalSize.width, (int)originalSize.height, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, bitmap);

        finalMat.release();
        return bitmap;
    }
}
