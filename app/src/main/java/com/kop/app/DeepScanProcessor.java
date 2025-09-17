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

    private static final int TOTAL_PASSES = 5;
    private static final long DELAY_PER_PASS_MS = 2000; // 2 seconds per pass

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

    public static void performDeepScan(Bitmap originalBitmap, ScanListener listener) {
        if (originalBitmap == null) {
            listener.onScanComplete(new ProcessingResult(null, 0));
            return;
        }

        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // --- THE NEW BILATERAL FILTER PIPELINE ---

        Mat accumulatedLines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        int totalObjects = 0;

        // --- PASS 1: SIMPLIFY SURFACES (THE KEY STEP) ---
        listener.onScanProgress(1, TOTAL_PASSES, getStatusForPass(1), createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat simplifiedMat = new Mat();
        // This is an edge-preserving smoothing filter. It removes texture while keeping main outlines sharp.
        // Parameters: d=15 (large neighborhood), sigmaColor=80, sigmaSpace=80 (strong smoothing effect).
        Imgproc.bilateralFilter(grayMat, simplifiedMat, 15, 80, 80);

        // --- PASS 2: TRACE LARGE SHAPES ---
        listener.onScanProgress(2, TOTAL_PASSES, getStatusForPass(2), createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat largeShapes = new Mat();
        // Use adaptiveThreshold on the CLEAN, simplified image. Block size is large to find major shapes.
        Imgproc.adaptiveThreshold(simplifiedMat, largeShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 25, 2);
        Core.bitwise_or(accumulatedLines, largeShapes, accumulatedLines);

        // --- PASS 3: TRACE FINE DETAILS ---
        listener.onScanProgress(3, TOTAL_PASSES, getStatusForPass(3), createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat detailShapes = new Mat();
        // Use a smaller block size to find smaller, more detailed shapes from the simplified image.
        Imgproc.adaptiveThreshold(simplifiedMat, detailShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);
        Core.bitwise_or(accumulatedLines, detailShapes, accumulatedLines);

        // --- PASS 4: CLEAN LINES AND COUNT OBJECTS ---
        listener.onScanProgress(4, TOTAL_PASSES, getStatusForPass(4), createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat cleanedLines = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,2));
        // A closing operation removes small holes and gaps in the lines.
        Imgproc.morphologyEx(accumulatedLines, cleanedLines, Imgproc.MORPH_CLOSE, kernel);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(cleanedLines, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        totalObjects = contours.size();
        for (MatOfPoint p : contours) {
            p.release();
        }
        hierarchy.release();

        // --- PASS 5: FINALIZE LINES (DILATION) ---
        listener.onScanProgress(5, TOTAL_PASSES, getStatusForPass(5), createBitmapFromMask(cleanedLines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalLines = new Mat();
        // Dilate the clean lines to make them slightly bolder and more visually appealing.
        Imgproc.dilate(cleanedLines, finalLines, kernel);
        
        // --- FINALIZE AND REPORT COMPLETION ---
        Bitmap finalBitmap = createBitmapFromMask(finalLines, originalMat.size());
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalObjects);
        listener.onScanComplete(finalResult);

        // Release all memory
        originalMat.release();
        grayMat.release();
        simplifiedMat.release();
        accumulatedLines.release();
        largeShapes.release();
        detailShapes.release();
        cleanedLines.release();
        kernel.release();
        finalLines.release();
    }

    private static String getStatusForPass(int pass) {
        switch (pass) {
            case 1: return "Pass 1/5: Simplifying Surfaces...";
            case 2: return "Pass 2/5: Tracing Large Shapes...";
            case 3: return "Pass 3/5: Tracing Fine Details...";
            case 4: return "Pass 4/5: Cleaning Lines...";
            case 5: return "Pass 5/5: Finalizing Artwork...";
            default: return "Scanning...";
        }
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
