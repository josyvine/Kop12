package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
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

        Mat accumulatedOutlines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        List<MatOfPoint> totalContours = new ArrayList<>();

        for (int pass = 1; pass <= TOTAL_PASSES; pass++) {
            try {
                Thread.sleep(DELAY_PER_PASS_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // --- THE NEW, SUPERIOR PIPELINE ---

            // Pass 1-2: Find large, general edges. Pass 3-5: Find finer details.
            double blurSize = (pass < 3) ? 5 : 3;
            double cannyThreshold1 = 10;
            double cannyThreshold2 = (pass < 3) ? 50 : 100;

            String status = getStatusForPass(pass);
            listener.onScanProgress(pass, TOTAL_PASSES, status, createBitmapFromMask(accumulatedOutlines, originalMat.size()));

            // Step 1: Canny Edge Detection
            Mat blurredMat = new Mat();
            Imgproc.GaussianBlur(grayMat, blurredMat, new Size(blurSize, blurSize), 0);
            Mat cannyEdges = new Mat();
            Imgproc.Canny(blurredMat, cannyEdges, cannyThreshold1, cannyThreshold2);

            // Step 2: Morphological Closing to connect broken lines
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Mat closedEdges = new Mat();
            Imgproc.morphologyEx(cannyEdges, closedEdges, Imgproc.MORPH_CLOSE, kernel);

            // Step 3: Find Contours of the cleaned edges
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(closedEdges, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);

            // Step 4: Simplify and Filter Contours
            double minArea = (originalMat.total() * 0.0001); // Filter very small noise
            for (MatOfPoint contour : contours) {
                if (Imgproc.contourArea(contour) > minArea) {
                    // Convert contour to MatOfPoint2f for simplification
                    MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                    // This is the key step: Simplify the contour to make it smoother
                    double epsilon = 0.002 * Imgproc.arcLength(contour2f, true);
                    MatOfPoint2f approxContour2f = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contour2f, approxContour2f, epsilon, true);

                    // Convert back to MatOfPoint and add to our list
                    MatOfPoint approxContour = new MatOfPoint(approxContour2f.toArray());
                    totalContours.add(approxContour);

                    // Draw the newly found smooth contour onto our accumulator
                    List<MatOfPoint> toDraw = new ArrayList<>();
                    toDraw.add(approxContour);
                    Imgproc.drawContours(accumulatedOutlines, toDraw, -1, new Scalar(255), 2);
                    
                    approxContour2f.release();
                }
                contour.release(); // Release original contour
            }

            // Release memory for this pass
            blurredMat.release();
            cannyEdges.release();
            kernel.release();
            closedEdges.release();
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
// No need to release `approxContour` as it's released by `totalContours` loop
        }
    }

    private static String getStatusForPass(int pass) {
        switch (pass) {
            case 1: return "Pass 1/5: Edge Detection...";
            case 2: return "Pass 2/5: Cleaning Lines...";
            case 3: return "Pass 3/5: Finding Contours...";
            case 4: return "Pass 4/5: Smoothing Lines...";
            case 5: return "Pass 5/5: Finalizing Details...";
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
