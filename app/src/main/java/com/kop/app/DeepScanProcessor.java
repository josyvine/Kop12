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

    /**
     * A class to hold the final result, including the image and object count.
     */
    public static class ProcessingResult {
        public final Bitmap resultBitmap;
        public final int objectsFound;

        ProcessingResult(Bitmap bitmap, int count) {
            this.resultBitmap = bitmap;
            this.objectsFound = count;
        }
    }

    /**
     * A listener to provide real-time feedback during the multi-pass scan.
     */
    public interface ScanListener {
        void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult);
        void onScanComplete(ProcessingResult finalResult);
    }

    /**
     * Performs a slow, deliberate, multi-pass scan of a bitmap to extract detailed outlines.
     * @param originalBitmap The bitmap to analyze.
     * @param listener The listener for progress updates.
     */
    public static void performDeepScan(Bitmap originalBitmap, ScanListener listener) {
        if (originalBitmap == null) {
            listener.onScanComplete(new ProcessingResult(null, 0));
            return;
        }

        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // This Mat will accumulate the outlines from all passes.
        Mat accumulatedOutlines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        List<MatOfPoint> totalContours = new ArrayList<>();

        for (int pass = 1; pass <= TOTAL_PASSES; pass++) {
            try {
                // Introduce the deliberate delay to make the process analytical.
                Thread.sleep(DELAY_PER_PASS_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Dynamically adjust parameters for each pass
            // Pass 1: Find large objects. Pass 5: Find fine details.
            int blockSize = 21 - (pass * 2); // Decreases from 19 down to 11
            double blurSize = 11 - (pass * 2); // Decreases from 9 down to 3
            double minArea = (originalMat.total() * 0.005) / (pass * pass); // Becomes more sensitive

            String status = getStatusForPass(pass);
            
            // --- CORE OPENCV LOGIC FOR THIS PASS ---
            Mat blurredMat = new Mat();
            Imgproc.GaussianBlur(grayMat, blurredMat, new Size(blurSize, blurSize), 0);

            Mat threshMat = new Mat();
            Imgproc.adaptiveThreshold(blurredMat, threshMat, 255,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, blockSize, 2);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(threshMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            for (MatOfPoint contour : contours) {
                if (Imgproc.contourArea(contour) > minArea) {
                    totalContours.add(contour);
                    // Draw the newly found contour onto our accumulator
                    Imgproc.drawContours(accumulatedOutlines, java.util.Collections.singletonList(contour), -1, new Scalar(255), 2);
                }
            }
            
            // --- REPORT PROGRESS ---
            Bitmap intermediateBitmap = createBitmapFromMask(accumulatedOutlines, originalMat.size());
            listener.onScanProgress(pass, TOTAL_PASSES, status, intermediateBitmap);
            
            // Release memory for this pass
            blurredMat.release();
            threshMat.release();
            hierarchy.release();
        }

        // --- FINALIZE AND REPORT COMPLETION ---
        Bitmap finalBitmap = createBitmapFromMask(accumulatedOutlines, originalMat.size());
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalContours.size());
        listener.onScanComplete(finalResult);

        // Release all remaining memory
        originalMat.release();
        grayMat.release();
        accumulatedOutlines.release();
        for (MatOfPoint p : totalContours) {
            p.release();
        }
    }

    private static String getStatusForPass(int pass) {
        switch (pass) {
            case 1: return "Pass 1 of 5: Finding Large Objects...";
            case 2: return "Pass 2 of 5: Analyzing Medium Shapes...";
            case 3: return "Pass 3 of 5: Identifying Detailed Contours...";
            case 4: return "Pass 4 of 5: Refining Edges...";
            case 5: return "Pass 5 of 5: Finalizing Lines...";
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
