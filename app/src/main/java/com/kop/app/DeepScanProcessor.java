package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DeepScanProcessor {

    // --- Common Inner Classes for All Methods ---

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

    public interface LiveScanListener {
        void onScanProgress(int pass, int totalPasses, String status);
        void onFoundationReady(Bitmap foundationBitmap);
        void onLinesReady(Bitmap linesBitmap);
        void onScanComplete(ProcessingResult finalResult);
    }

    // --- Method 1: The "Utmost Best" - Live Foundational Analysis ---
    public static void processMethod1(Bitmap originalBitmap, LiveScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 3, "Pass 1/3: Finding Object Foundations...");
        try { Thread.sleep(4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(originalMat, bgrMat, Imgproc.COLOR_RGBA2BGR);
        Mat cannyForSeeds = getCannyEdges(grayMat, 10, 80);
        Mat markers = getWatershedMarkers(cannyForSeeds, bgrMat);
        Bitmap foundationBitmap = createColoredFoundation(markers, originalMat.size());
        listener.onFoundationReady(foundationBitmap);
        
        listener.onScanProgress(2, 3, "Pass 2/3: Tracing Final Lines...");
        try { Thread.sleep(4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalLines = getMethod8Lines(grayMat);
        Bitmap linesBitmap = createBitmapFromMask(finalLines, originalMat.size());
        listener.onLinesReady(linesBitmap);
        
        listener.onScanProgress(3, 3, "Pass 3/3: Finalizing Artwork...");
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        int objectCount = countContours(finalLines);
        ProcessingResult result = new ProcessingResult(linesBitmap, objectCount);
        listener.onScanComplete(result);
        
        originalMat.release();
        grayMat.release();
        bgrMat.release();
        cannyForSeeds.release();
        markers.release();
        finalLines.release();
    }

    // --- Method 2: "Artistic Crosshatching" ---
    public static void processMethod2(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 5, "Pass 1/5: Segmenting Regions...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(originalMat, bgrMat, Imgproc.COLOR_RGBA2BGR);
        Mat cannyForSeeds = getCannyEdges(grayMat, 10, 80);
        Mat markers = getWatershedMarkers(cannyForSeeds, bgrMat);
        
        listener.onScanProgress(2, 5, "Pass 2/5: Calculating Shading...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat shaded = createShadedRegions(markers, grayMat);
        
        listener.onScanProgress(3, 5, "Pass 3/5: Applying Crosshatch...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat crosshatched = applyCrosshatching(shaded);
        
        listener.onScanProgress(4, 5, "Pass 4/5: Finding Outlines...", createBitmapFromMask(crosshatched, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat outlines = getCannyEdges(grayMat, 20, 80);
        
        listener.onScanProgress(5, 5, "Pass 5/5: Combining Artwork...", createBitmapFromMask(crosshatched, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Core.bitwise_or(crosshatched, outlines, crosshatched);
        Mat finalLines = finalizeLines(crosshatched);
        
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); bgrMat.release(); cannyForSeeds.release(); markers.release(); shaded.release(); crosshatched.release(); outlines.release();
    }

    // --- Method 3: "Shaded Segmentation" ---
    public static void processMethod3(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 4, "Pass 1/4: Segmenting Regions...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(originalMat, bgrMat, Imgproc.COLOR_RGBA2BGR);
        Mat cannyForSeeds = getCannyEdges(grayMat, 10, 80);
        Mat markers = getWatershedMarkers(cannyForSeeds, bgrMat);
        
        listener.onScanProgress(2, 4, "Pass 2/4: Applying Shading...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat shaded = createShadedRegions(markers, grayMat);
        
        listener.onScanProgress(3, 4, "Pass 3/4: Finding Outlines...", createBitmapFromMask(shaded, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat outlines = getCannyEdges(grayMat, 20, 80);
        
        listener.onScanProgress(4, 4, "Pass 4/4: Combining Artwork...", createBitmapFromMask(shaded, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat invertedOutlines = new Mat();
        Core.bitwise_not(outlines, invertedOutlines);
        Core.bitwise_and(shaded, shaded, shaded, invertedOutlines); // Apply inverted outlines as a mask
        Mat finalLines = finalizeLines(shaded);

        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); bgrMat.release(); cannyForSeeds.release(); markers.release(); shaded.release(); outlines.release(); invertedOutlines.release();
    }
    
    // --- Method 4: "Segmented Detail" ---
    public static void processMethod4(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        
        listener.onScanProgress(1, 4, "Pass 1/4: Segmenting Objects...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(originalMat, bgrMat, Imgproc.COLOR_RGBA2BGR);
        Mat cannyForSeeds = getCannyEdges(grayMat, 10, 80);
        Mat markers = getWatershedMarkers(cannyForSeeds, bgrMat);
        
        Mat boundaries = new Mat(markers.size(), CvType.CV_8U, new Scalar(0));
        for (int r = 0; r < markers.rows(); r++) {
            for (int c = 0; c < markers.cols(); c++) {
                if (markers.get(r, c)[0] == -1) {
                    boundaries.put(r, c, 255);
                }
            }
        }
        listener.onScanProgress(2, 4, "Pass 2/4: Extracting Boundaries...", createBitmapFromMask(boundaries, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        Mat detailLines = getCannyEdges(grayMat, 30, 90);
        listener.onScanProgress(3, 4, "Pass 3/4: Finding Internal Details...", createBitmapFromMask(boundaries, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        Core.bitwise_or(boundaries, detailLines, boundaries);
        listener.onScanProgress(4, 4, "Pass 4/4: Combining Lines...", createBitmapFromMask(boundaries, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        Mat finalLines = finalizeLines(boundaries);
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); bgrMat.release(); cannyForSeeds.release(); markers.release(); boundaries.release(); detailLines.release();
    }

    // --- Method 5: "Pencil Sketch" ---
    public static void processMethod5(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 4, "Pass 1/4: Creating Soft Shading...", createBitmapFromMask(new Mat(), originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat inverted = new Mat();
        Core.bitwise_not(grayMat, inverted);
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(inverted, blurred, new Size(21, 21), 0);
        Mat sketch = colorDodge(grayMat, blurred);
        
        listener.onScanProgress(2, 4, "Pass 2/4: Finding Sharp Edges...", createBitmapFromMask(sketch, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat sharpLines = getMethod8Lines(grayMat);
        
        listener.onScanProgress(3, 4, "Pass 3/4: Combining Shading & Lines...", createBitmapFromMask(sketch, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat invertedLines = new Mat();
        Core.bitwise_not(sharpLines, invertedLines);
        Core.bitwise_and(sketch, sketch, sketch, invertedLines);

        listener.onScanProgress(4, 4, "Pass 4/4: Finalizing Artwork...", createBitmapFromMask(sketch, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalSketch = new Mat();
        Imgproc.cvtColor(sketch, finalSketch, Imgproc.COLOR_GRAY2BGRA);
        
        Bitmap finalBitmap = Bitmap.createBitmap(finalSketch.cols(), finalSketch.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalSketch, finalBitmap);
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, countContours(sharpLines));
        listener.onScanComplete(finalResult);

        originalMat.release(); grayMat.release(); inverted.release(); blurred.release(); sketch.release(); sharpLines.release(); invertedLines.release(); finalSketch.release();
    }
    
    // --- Method 6: "Selective Detail" ---
    public static void processMethod6(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Simplifying Structure...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat simplifiedMat = getSimplifiedImage(grayMat);
        Mat structuralLines = getCannyEdges(simplifiedMat, 5, 50);
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Structural Lines...", createBitmapFromMask(structuralLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat detailLines = getCannyEdges(grayMat, 60, 120);
        listener.onScanProgress(3, 5, "Pass 3/5: Finding Fine Details...", createBitmapFromMask(structuralLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat maskedDetails = new Mat();
        Mat structureMask = new Mat();
        Imgproc.dilate(structuralLines, structureMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));
        detailLines.copyTo(maskedDetails, structureMask);
        Core.bitwise_or(structuralLines, maskedDetails, structuralLines);
        listener.onScanProgress(4, 5, "Pass 4/5: Combining Lines...", createBitmapFromMask(structuralLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalLines = finalizeLines(structuralLines);
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); simplifiedMat.release(); structuralLines.release(); detailLines.release(); maskedDetails.release(); structureMask.release();
    }
    
    // --- Method 7: "Artistic Abstraction" ---
    public static void processMethod7(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Abstracting Image...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat simplifiedMat = getSimplifiedImage(grayMat);
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Major Edges...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat majorEdges = getCannyEdges(simplifiedMat, 5, 50);
        listener.onScanProgress(3, 5, "Pass 3/5: Finding Detail Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat detailEdges = getCannyEdges(simplifiedMat, 60, 120);
        Core.bitwise_or(majorEdges, detailEdges, majorEdges);
        listener.onScanProgress(4, 5, "Pass 4/5: Combining Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalLines = finalizeLines(majorEdges);
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); simplifiedMat.release(); majorEdges.release(); detailEdges.release();
    }

    // --- Method 8: "Clean Structure" ---
    public static void processMethod8(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Smoothing Surfaces...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Major Edges...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat majorEdges = getCannyEdges(blurredMat, 5, 50);
        listener.onScanProgress(3, 5, "Pass 3/5: Finding Detail Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat detailEdges = getCannyEdges(blurredMat, 60, 120);
        Core.bitwise_or(majorEdges, detailEdges, majorEdges);
        listener.onScanProgress(4, 5, "Pass 4/5: Combining Edges...", createBitmapFromMask(majorEdges, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalLines = finalizeLines(majorEdges);
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); blurredMat.release(); majorEdges.release(); detailEdges.release();
    }
    
    // --- Method 9: "Detailed Texture" ---
    public static void processMethod9(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Simplifying Surfaces...", createBitmapFromMask(new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0)), originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat simplifiedMat = new Mat();
        Imgproc.bilateralFilter(grayMat, simplifiedMat, 15, 80, 80);
        Mat accumulatedLines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        listener.onScanProgress(2, 5, "Pass 2/5: Tracing Large Shapes...", createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat largeShapes = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, largeShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 25, 2);
        Core.bitwise_or(accumulatedLines, largeShapes, accumulatedLines);
        listener.onScanProgress(3, 5, "Pass 3/5: Tracing Fine Details...", createBitmapFromMask(accumulatedLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat detailShapes = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, detailShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);
        Core.bitwise_or(accumulatedLines, detailShapes, accumulatedLines);
        Mat finalLines = finalizeLines(accumulatedLines);
        listener.onScanProgress(4, 5, "Pass 4/5: Cleaning Lines...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromMask(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); simplifiedMat.release(); accumulatedLines.release(); largeShapes.release(); detailShapes.release();
    }
    
    // --- Method 10: Legacy ---
    public static void processMethod10(Bitmap originalBitmap, ScanListener listener) {
        processMethod8(originalBitmap, listener);
    }
    
    // --- HELPER FUNCTIONS (FULLY EXPANDED) ---

    private static Mat colorDodge(Mat bottom, Mat top) {
        Mat topFloat = new Mat();
        top.convertTo(topFloat, CvType.CV_32F, 1.0/255.0);
        Mat bottomFloat = new Mat();
        bottom.convertTo(bottomFloat, CvType.CV_32F, 1.0/255.0);
        Mat one = new Mat(topFloat.size(), topFloat.type(), new Scalar(1.0));
        Mat topSub = new Mat();
        Core.subtract(one, topFloat, topSub);
        Mat result = new Mat();
        Core.divide(bottomFloat, topSub, result, 255.0);
        result.convertTo(result, CvType.CV_8U);
        topFloat.release();
        bottomFloat.release();
        one.release();
        topSub.release();
        return result;
    }

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
    
    private static Mat getMethod7Lines(Mat grayMat) {
        Mat simplified = getSimplifiedImage(grayMat);
        Mat major = getCannyEdges(simplified, 5, 50);
        Mat detail = getCannyEdges(simplified, 60, 120);
        Core.bitwise_or(major, detail, major);
        Mat finalLines = finalizeLines(major);
        simplified.release();
        detail.release();
        return finalLines;
    }

    private static Mat getMethod8Lines(Mat grayMat) {
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(grayMat, blurred, new Size(5, 5), 0);
        Mat major = getCannyEdges(blurred, 5, 50);
        Mat detail = getCannyEdges(blurred, 60, 120);
        Core.bitwise_or(major, detail, major);
        Mat finalLines = finalizeLines(major);
        blurred.release();
        detail.release();
        return finalLines;
    }

    private static int countContours(Mat mat) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        int total = contours.size();
        for (MatOfPoint p : contours) {
            p.release();
        }
        hierarchy.release();
        return total;
    }

    private static void finalizeAndComplete(Mat finalLines, ScanListener listener) {
        int totalObjects = countContours(finalLines);
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

    private static Mat getWatershedMarkers(Mat grayMat, Mat bgrMat) {
        Mat thresh = new Mat();
        Imgproc.threshold(grayMat, thresh, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3,3));
        Mat opening = new Mat();
        Imgproc.morphologyEx(thresh, opening, Imgproc.MORPH_OPEN, kernel, new Point(-1,-1), 2);
        Mat sureBg = new Mat();
        Imgproc.dilate(opening, sureBg, kernel, new Point(-1,-1), 3);
        Mat distTransform = new Mat();
        Imgproc.distanceTransform(opening, distTransform, Imgproc.DIST_L2, 5);
        Mat sureFg = new Mat();
        Imgproc.threshold(distTransform, sureFg, 0.7 * Core.minMaxLoc(distTransform).maxVal, 255, 0);
        Mat sureFg8u = new Mat();
        sureFg.convertTo(sureFg8u, CvType.CV_8U);
        Mat unknown = new Mat();
        Core.subtract(sureBg, sureFg8u, unknown);
        Mat markers = new Mat();
        Imgproc.connectedComponents(sureFg8u, markers);
        Core.add(markers, new Scalar(1), markers);
        for(int r=0; r<markers.rows(); r++) {
            for(int c=0; c<markers.cols(); c++) {
                if (unknown.get(r,c)[0] == 255) {
                    markers.put(r,c,0);
                }
            }
        }
        Imgproc.watershed(bgrMat, markers);
        thresh.release();
        kernel.release();
        opening.release();
        sureBg.release();
        distTransform.release();
        sureFg.release();
        sureFg8u.release();
        unknown.release();
        return markers;
    }

    private static Bitmap createColoredFoundation(Mat markers, Size size) {
        Mat foundation = new Mat(size, CvType.CV_8UC4, new Scalar(0,0,0,0));
        Random random = new Random();
        for(int r=0; r<markers.rows(); r++) {
            for(int c=0; c<markers.cols(); c++) {
                int index = (int)markers.get(r,c)[0];
                if (index > 0 && index != -1) {
                    random.setSeed(index);
                    int b = random.nextInt(200) + 55;
                    int g = random.nextInt(200) + 55;
                    int r_ = random.nextInt(200) + 55;
                    foundation.put(r, c, new byte[]{(byte)b, (byte)g, (byte)r_, (byte)128});
                }
            }
        }
        Bitmap b = Bitmap.createBitmap((int)size.width, (int)size.height, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(foundation, b);
        foundation.release();
        return b;
    }

    private static Mat createShadedRegions(Mat markers, Mat grayMat) {
        Mat shaded = new Mat(markers.size(), CvType.CV_8UC1, new Scalar(255));
        Mat markers8u = new Mat();
        markers.convertTo(markers8u, CvType.CV_8U);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(markers8u, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        for (int i = 0; i < contours.size(); i++) {
            Mat mask = new Mat(markers.size(), CvType.CV_8UC1, new Scalar(0));
            Imgproc.drawContours(mask, contours, i, new Scalar(255), -1);
            Scalar averageColor = Core.mean(grayMat, mask);
            Imgproc.drawContours(shaded, contours, i, averageColor, -1);
            mask.release();
        }
        markers8u.release();
        hierarchy.release();
        for(MatOfPoint p : contours) p.release();
        return shaded;
    }

    private static Mat applyCrosshatching(Mat shaded) {
        Mat hatched = new Mat(shaded.size(), CvType.CV_8UC1, new Scalar(255));
        for(int r = 0; r < shaded.rows(); r++) {
            for (int c = 0; c < shaded.cols(); c++) {
                double intensity = shaded.get(r,c)[0];
                if (intensity < 85) {
                    if ( (r + c) % 10 == 0 || (r - c) % 10 == 0) {
                        hatched.put(r,c,0);
                    }
                } 
                else if (intensity < 170) {
                     if ( (r + c) % 10 == 0) {
                        hatched.put(r,c,0);
                    }
                }
            }
        }
        return hatched;
    }
}
