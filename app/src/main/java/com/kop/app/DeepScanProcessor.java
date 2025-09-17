package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ximgproc.Ximgproc; // Import for the thinning/skeletonization function

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

        // --- THE NEW SKELETONIZATION PIPELINE ---

        // This will hold the accumulated lines from each pass.
        Mat accumulatedLines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        int totalObjects = 0;

        for (int pass = 1; pass <= TOTAL_PASSES; pass++) {
            try { Thread.sleep(DELAY_PER_PASS_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            String status = getStatusForPass(pass);
            listener.onScanProgress(pass, TOTAL_PASSES, status, createBitmapFromMask(accumulatedLines, originalMat.size()));

            if (pass <= 3) { // Use the first 3 passes to find shapes of different sizes
                // Vary the block size to find different levels of detail in each pass.
                // Large block size for big shapes, small for details.
                int blockSize = 31 - (pass * 6); // Decreases from 25 down to 13
                
                // Step 1: Detect shapes using adaptive thresholding.
                Mat threshMat = new Mat();
                Imgproc.adaptiveThreshold(blurredMat, threshMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, blockSize, 3);

                // Step 2: SKELETONIZE. This is the key step to get clean, thin lines.
                Mat skeleton = new Mat();
                Ximgproc.thinning(threshMat, skeleton, Ximgproc.THINNING_ZHANGSUEN);
                
                // Add the new clean lines to our accumulator.
                Core.bitwise_or(accumulatedLines, skeleton, accumulatedLines);
                
                // Release memory for this pass
                threshMat.release();
                skeleton.release();
            }

            if (pass == 4) {
                // In pass 4, we count the objects based on the lines found so far.
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                // We find contours on a dilated version to connect nearby lines into single objects.
                Mat dilatedForCount = new Mat();
                Imgproc.dilate(accumulatedLines, dilatedForCount, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));
                Imgproc.findContours(dilatedForCount, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                totalObjects = contours.size();
                dilatedForCount.release();
                hierarchy.release();
            }

            if (pass == 5) {
                // In the final pass, we make the lines slightly thicker for better visibility.
                Mat finalLines = new Mat();
                Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
                Imgproc.dilate(accumulatedLines, finalLines, kernel);
                accumulatedLines.release(); // Free old accumulator
                accumulatedLines = finalLines; // Assign the new dilated mat
                kernel.release();
            }
        }

        // --- FINALIZE AND REPORT COMPLETION ---
        Bitmap finalBitmap = createBitmapFromMask(accumulatedLines, originalMat.size());
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalObjects);
        listener.onScanComplete(finalResult);

        // Release all remaining memory
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        accumulatedLines.release();
    }

    private static String getStatusForPass(int pass) {
        switch (pass) {
            case 1: return "Pass 1/5: Analyzing Large Structures...";
            case 2: return "Pass 2/5: Analyzing Medium Details...";
            case 3: return "Pass 3/5: Tracing Fine Textures...";
            case 4: return "Pass 4/5: Counting Objects...";
            case 5: return "Pass 5/5: Finalizing Lines...";
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
