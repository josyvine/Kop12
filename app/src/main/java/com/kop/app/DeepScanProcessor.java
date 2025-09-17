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
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(3, 3), 0);

        // --- THE NEW HYBRID PIPELINE ---

        // Mat to hold the foundational shapes
        Mat shapeOutlines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        // Mat to hold the fine details
        Mat detailLines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));

        List<MatOfPoint> totalContours = new ArrayList<>();

        // --- PASS 1 & 2: SHAPE ANALYSIS ---
        listener.onScanProgress(1, TOTAL_PASSES, getStatusForPass(1), createBitmapFromMask(shapeOutlines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) {}
        
        Mat threshMat = new Mat();
        Imgproc.adaptiveThreshold(blurredMat, threshMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 15, 3);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(threshMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        double minShapeArea = originalMat.total() * 0.001;
        for(MatOfPoint contour : contours) {
            if (Imgproc.contourArea(contour) > minShapeArea) {
                totalContours.add(contour);
            }
        }
        Imgproc.drawContours(shapeOutlines, totalContours, -1, new Scalar(255), 2);
        
        listener.onScanProgress(2, TOTAL_PASSES, getStatusForPass(2), createBitmapFromMask(shapeOutlines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) {}

        // --- PASS 3 & 4: DETAIL ANALYSIS ---
        listener.onScanProgress(3, TOTAL_PASSES, getStatusForPass(3), createBitmapFromMask(shapeOutlines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) {}
        
        Imgproc.Canny(blurredMat, detailLines, 10, 80);
        
        Mat combined = new Mat();
        Core.bitwise_or(shapeOutlines, detailLines, combined);
        listener.onScanProgress(4, TOTAL_PASSES, getStatusForPass(4), createBitmapFromMask(combined, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) {}

        // --- PASS 5: FINALIZATION & REFINEMENT ---
        Mat finalLines = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.dilate(combined, finalLines, kernel); // Make lines slightly bolder and more solid

        listener.onScanProgress(5, TOTAL_PASSES, getStatusForPass(5), createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) {}

        // --- FINALIZE AND REPORT COMPLETION ---
        Bitmap finalBitmap = createBitmapFromMask(finalLines, originalMat.size());
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalContours.size());
        listener.onScanComplete(finalResult);

        // Release all memory
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        shapeOutlines.release();
        detailLines.release();
        threshMat.release();
        hierarchy.release();
        combined.release();
        finalLines.release();
        kernel.release();
        for (MatOfPoint p : contours) p.release();
    }

    private static String getStatusForPass(int pass) {
        switch (pass) {
            case 1: return "Pass 1/5: Finding Broad Shapes...";
            case 2: return "Pass 2/5: Drawing Main Outlines...";
            case 3: return "Pass 3/5: Extracting Fine Details...";
            case 4: return "Pass 4/5: Combining Shapes & Details...";
            case 5: return "Pass 5/5: Refining Final Lines...";
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
