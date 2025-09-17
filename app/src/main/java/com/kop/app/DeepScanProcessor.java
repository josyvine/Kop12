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
        // This is now only used to return the final result for methods 2-10
        void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult);
        void onScanComplete(ProcessingResult finalResult);
    }

    public interface LiveScanListener {
        void onScanProgress(int pass, int totalPasses, String status);
        void onFoundationReady(Bitmap foundationBitmap);
        void onLinesReady(Bitmap linesBitmap);
        void onScanComplete(ProcessingResult finalResult);
    }

    // --- Method 1: The "Utmost Best" - Live Foundational Analysis (UNCHANGED) ---
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

    // --- NEW UNIFIED PROCESSING METHOD FOR FINE-TUNING (METHODS 2-10) ---

    /**
     * Processes a bitmap using a staged pipeline controlled by depth and sharpness.
     * @param originalBitmap The source image.
     * @param method The method ID (2-10) to use.
     * @param depth The artistic stage to stop at (0-4).
     * @param sharpness The fine-tuning value for parameters (0-100).
     * @param listener The listener to call with the final result.
     */
    public static void processWithFineTuning(Bitmap originalBitmap, int method, int depth, int sharpness, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Mat finalLines = new Mat();

        switch (method) {
            case 2: // Crosshatch
            case 3: // Shaded
            case 4: // Segmented Detail
                // These methods are complex and less suited for the new pipeline.
                // We'll use the "Clean Structure" logic as a robust fallback.
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 5: // Pencil Sketch
                finalLines = getStagedPencilSketch(grayMat, depth, sharpness);
                break;
            case 6: // Selective Detail
            case 7: // Artistic
                // These Canny-based methods map well to the Clean Structure pipeline.
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 8: // Clean Structure
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 9: // Detailed Texture
                finalLines = getStagedDetailedTextureLines(grayMat, depth, sharpness);
                break;
            case 10: // Legacy Detailed
            default:
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
        }
        
        finalizeAndComplete(finalLines, listener);
        originalMat.release();
        grayMat.release();
    }

    // --- STAGED PIPELINE IMPLEMENTATIONS ---

    private static Mat getStagedCleanStructureLines(Mat grayMat, int depth, int sharpness) {
        // --- Parameters controlled by sharpness (0-100) ---
        // Higher sharpness = less blur
        int blurKernelSize = mapSharpnessToOdd(sharpness, 11, 3); 
        // Higher sharpness = more sensitive Canny (more lines)
        int cannyLow = mapSharpnessToInt(sharpness, 60, 10); 
        // Higher sharpness = smaller cleaning kernel (less aggressive cleaning)
        int cleanKernelSize = mapSharpnessToInt(sharpness, 4, 2);
        // Higher sharpness = thinner bolding lines
        int boldKernelSize = mapSharpnessToInt(sharpness, 4, 1);

        // STAGE 0: Smoothing
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(blurKernelSize, blurKernelSize), 0);

        // STAGE 1: Foundation (Large Shapes)
        Mat foundation = getCannyEdges(blurredMat, cannyLow, cannyLow * 2);
        if (depth == 0) {
            blurredMat.release();
            return foundation;
        }

        // STAGE 2: Detail (Fine Details)
        Mat detail = getCannyEdges(blurredMat, cannyLow * 2, cannyLow * 3);
        Core.bitwise_or(foundation, detail, foundation);
        if (depth == 1) {
            blurredMat.release();
            detail.release();
            return foundation;
        }

        // STAGE 3: Cleaned
        Mat cleaned = new Mat();
        Mat cleanKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(cleanKernelSize, cleanKernelSize));
        Imgproc.morphologyEx(foundation, cleaned, Imgproc.MORPH_CLOSE, cleanKernel);
        if (depth == 2) {
            blurredMat.release();
            detail.release();
            foundation.release();
            cleanKernel.release();
            return cleaned;
        }

        // STAGE 4: Finalized (Bolding)
        Mat finalized = new Mat();
        Mat boldKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(boldKernelSize, boldKernelSize));
        Imgproc.dilate(cleaned, finalized, boldKernel);
        if (depth == 3 || depth == 4) { // Points 4 and 5 will use this result
             blurredMat.release();
            detail.release();
            foundation.release();
            cleaned.release();
            cleanKernel.release();
            boldKernel.release();
            return finalized;
        }

        // Fallback
        return finalized;
    }

    private static Mat getStagedDetailedTextureLines(Mat grayMat, int depth, int sharpness) {
        // --- Parameters controlled by sharpness (0-100) ---
        // Higher sharpness = less smoothing
        int bilateralD = mapSharpnessToInt(sharpness, 20, 5); 
        // Higher sharpness = smaller block size (more detail)
        int largeBlockSize = mapSharpnessToOdd(sharpness, 45, 15);
        int detailBlockSize = mapSharpnessToOdd(sharpness, 25, 7);
        // Higher sharpness = less aggressive cleaning
        int cleanKernelSize = mapSharpnessToInt(sharpness, 3, 1);
        // Higher sharpness = thinner bolding
        int boldKernelSize = mapSharpnessToInt(sharpness, 3, 1);
        
        // STAGE 0: Smoothing
        Mat simplifiedMat = new Mat();
        Imgproc.bilateralFilter(grayMat, simplifiedMat, bilateralD, 80, 80);

        // STAGE 1: Foundation (Large Shapes)
        Mat foundation = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, foundation, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, largeBlockSize, 2);
        if (depth == 0) {
            simplifiedMat.release();
            return foundation;
        }

        // STAGE 2: Detail (Fine Details)
        Mat detail = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, detail, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, detailBlockSize, 2);
        Core.bitwise_or(foundation, detail, foundation);
        if (depth == 1) {
            simplifiedMat.release();
            detail.release();
            return foundation;
        }

        // STAGE 3: Cleaned
        Mat cleaned = new Mat();
        Mat cleanKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(cleanKernelSize, cleanKernelSize));
        Imgproc.morphologyEx(foundation, cleaned, Imgproc.MORPH_OPEN, cleanKernel);
        if (depth == 2) {
            simplifiedMat.release();
            detail.release();
            foundation.release();
            cleanKernel.release();
            return cleaned;
        }
        
        // STAGE 4: Finalized (Bolding)
        Mat finalized = new Mat();
        Mat boldKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(boldKernelSize, boldKernelSize));
        Imgproc.dilate(cleaned, finalized, boldKernel);
        if (depth == 3 || depth == 4) { // Points 4 and 5 will use this result
            simplifiedMat.release();
            detail.release();
            foundation.release();
            cleaned.release();
            cleanKernel.release();
            boldKernel.release();
            return finalized;
        }

        // Fallback
        return finalized;
    }
    
    private static Mat getStagedPencilSketch(Mat grayMat, int depth, int sharpness) {
        // --- Parameters controlled by sharpness (0-100) ---
        // Higher sharpness = less blur for dodge effect = sharper sketch
        int blurKernelSize = mapSharpnessToOdd(sharpness, 41, 5); 
        
        // STAGE 1 & 2: Create the base sketch
        Mat inverted = new Mat();
        Core.bitwise_not(grayMat, inverted);
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(inverted, blurred, new Size(blurKernelSize, blurKernelSize), 0);
        Mat sketch = colorDodge(grayMat, blurred);
        if (depth == 0 || depth == 1) { // Foundation and Detail are the same for this method
            inverted.release();
            blurred.release();
            return sketch;
        }
        
        // STAGE 3, 4, 5: Add sharp outline lines
        Mat sharpLines = getMethod8Lines(grayMat); // Use a standard method for outlines
        Mat invertedLines = new Mat();
        Core.bitwise_not(sharpLines, invertedLines);
        // "Erase" the sketch where the sharp lines will go, then add them back
        Core.bitwise_and(sketch, invertedLines, sketch);
        Core.bitwise_or(sketch, sharpLines, sketch);
        
        // For sketch, "Cleaned" and "Finalized" are the same as this combined stage.
        inverted.release();
        blurred.release();
        sharpLines.release();
        invertedLines.release();
        return sketch;
    }

    // --- HELPER FUNCTIONS ---

    private static int mapSharpnessToInt(int sharpness, int valAt0, int valAt100) {
        float result = valAt0 + (valAt100 - valAt0) * (sharpness / 100.0f);
        return Math.max(1, (int)result);
    }

    private static int mapSharpnessToOdd(int sharpness, int valAt0, int valAt100) {
        int result = mapSharpnessToInt(sharpness, valAt0, valAt100);
        return (result % 2 == 0) ? result + 1 : result;
    }

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
        // Check if the finalLines is grayscale, if so, convert it before creating bitmap
        if (finalLines.channels() == 1) {
             Bitmap finalBitmap = createBitmapFromMask(finalLines, finalLines.size());
             ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalObjects);
             listener.onScanComplete(finalResult);
        } else {
            Bitmap finalBitmap = Bitmap.createBitmap(finalLines.cols(), finalLines.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(finalLines, finalBitmap);
            ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalObjects);
            listener.onScanComplete(finalResult);
        }
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
