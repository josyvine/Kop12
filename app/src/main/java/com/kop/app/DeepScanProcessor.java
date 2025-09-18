package com.kop.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DeepScanProcessor {

    private static final String TAG = "DeepScanProcessor";
    private static final String MODEL_FILE = "selfie_segmenter.tflite";

    // --- Common Inner Classes for All Methods ---

    public static class ProcessingResult {
        public final Bitmap resultBitmap;
        public final int objectsFound;

        ProcessingResult(Bitmap bitmap, int count) {
            this.resultBitmap = bitmap;
            this.objectsFound = count;
        }
    }

    public interface AiScanListener {
        void onAiScanComplete(ProcessingResult finalResult);
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

    // --- REBUILT AS REQUESTED: Method 01 (AI Composite) - Line art person on raw background ---
    public static void processMethod01(Context context, Bitmap originalBitmap, AiScanListener listener) {
        ImageSegmenter imageSegmenter = null;
        try {
            // Step 1: Initialize the MediaPipe Image Segmenter
            ImageSegmenterOptions.Builder optionsBuilder =
                ImageSegmenterOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputConfidenceMasks(true);
            ImageSegmenterOptions options = optionsBuilder.build();
            imageSegmenter = ImageSegmenter.createFromOptions(context, options);
            MPImage mpImage = new BitmapImageBuilder(originalBitmap).build();
            ImageSegmenterResult segmenterResult = imageSegmenter.segment(mpImage);

            if (segmenterResult != null && segmenterResult.confidenceMasks().isPresent()) {
                try (MPImage mask = segmenterResult.confidenceMasks().get().get(0)) {
                    // Convert the AI mask to an OpenCV Mat
                    ByteBuffer byteBuffer = ByteBufferExtractor.extract(mask);
                    FloatBuffer confidenceMaskBuffer = byteBuffer.asFloatBuffer();
                    confidenceMaskBuffer.rewind();
                    Mat maskMat = new Mat(mask.getHeight(), mask.getWidth(), CvType.CV_32F);
                    float[] floatArray = new float[confidenceMaskBuffer.remaining()];
                    confidenceMaskBuffer.get(floatArray);
                    maskMat.put(0, 0, floatArray);
                    Mat mask8u = new Mat();
                    maskMat.convertTo(mask8u, CvType.CV_8U, 255.0);
                    Mat thresholdMat = new Mat();
                    Imgproc.threshold(mask8u, thresholdMat, 128, 255, Imgproc.THRESH_BINARY);

                    // --- START: Generate line art for the person ONLY using the logic from your Method 0 ---
                    Mat originalMat = new Mat();
                    Utils.bitmapToMat(originalBitmap, originalMat);
                    Mat resizedMask = new Mat();
                    Imgproc.resize(thresholdMat, resizedMask, originalMat.size());
                    Mat isolatedSubjectMat = new Mat();
                    Core.bitwise_and(originalMat, originalMat, isolatedSubjectMat, resizedMask);

                    Mat grayIsolated = new Mat();
                    Imgproc.cvtColor(isolatedSubjectMat, grayIsolated, Imgproc.COLOR_RGBA2GRAY);
                    Imgproc.GaussianBlur(grayIsolated, grayIsolated, new Size(3, 3), 0);
                    Mat detailLines = new Mat();
                    Imgproc.Canny(grayIsolated, detailLines, 50, 150);

                    List<MatOfPoint> contours = new ArrayList<>();
                    Mat hierarchy = new Mat();
                    // Use a copy of resizedMask for findContours as it can be modified by the function
                    Mat contoursMask = resizedMask.clone();
                    Imgproc.findContours(contoursMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                    contoursMask.release();

                    Mat personLineArt = new Mat(originalBitmap.getHeight(), originalBitmap.getWidth(), CvType.CV_8UC4, new Scalar(255, 255, 255, 255));
                    Imgproc.drawContours(personLineArt, contours, -1, new Scalar(0, 0, 0, 255), 2);
                    personLineArt.setTo(new Scalar(0, 0, 0, 255), detailLines);
                    // --- END: Line art of person is now in 'personLineArt' ---

                    // --- START: Composite the line art onto the original raw background ---
                    Mat finalComposite = new Mat();
                    Utils.bitmapToMat(originalBitmap, finalComposite);
                    if (finalComposite.channels() == 3) {
                        Imgproc.cvtColor(finalComposite, finalComposite, Imgproc.COLOR_RGB2RGBA);
                    }
                    
                    personLineArt.copyTo(finalComposite, resizedMask);
                    // --- END: Composite ---

                    Bitmap finalBitmap = Bitmap.createBitmap(finalComposite.cols(), finalComposite.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(finalComposite, finalBitmap);
                    ProcessingResult result = new ProcessingResult(finalBitmap, contours.size());
                    listener.onAiScanComplete(result);

                    // Clean up all OpenCV Mats
                    maskMat.release();
                    mask8u.release();
                    thresholdMat.release();
                    originalMat.release();
                    resizedMask.release();
                    isolatedSubjectMat.release();
                    grayIsolated.release();
                    detailLines.release();
                    hierarchy.release();
                    personLineArt.release();
                    finalComposite.release();
                    for (MatOfPoint contour : contours) {
                        contour.release();
                    }
                }
            } else {
                throw new Exception("MediaPipe segmentation returned a null or empty result.");
            }
        } catch (Exception e) {
            Log.e(TAG, "MediaPipe AI segmentation has CRITICALLY FAILED. See exception below.", e);
            listener.onAiScanComplete(new ProcessingResult(null, 0));
        } finally {
            if (imageSegmenter != null) {
                imageSegmenter.close();
            }
        }
    }


    // --- Method 0 is "AI-Powered Detail Scan" (RESTORED to your original logic with one small fix) ---
    public static void processMethod0(Context context, Bitmap originalBitmap, AiScanListener listener) {
        ImageSegmenter imageSegmenter = null;
        try {
            // Step 1: Initialize the MediaPipe Image Segmenter
            ImageSegmenterOptions.Builder optionsBuilder =
                ImageSegmenterOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputConfidenceMasks(true);

            ImageSegmenterOptions options = optionsBuilder.build();
            imageSegmenter = ImageSegmenter.createFromOptions(context, options);

            MPImage mpImage = new BitmapImageBuilder(originalBitmap).build();

            // Step 2: Run AI Segmentation to get the mask
            ImageSegmenterResult segmenterResult = imageSegmenter.segment(mpImage);

            if (segmenterResult != null && segmenterResult.confidenceMasks().isPresent()) {
                try (MPImage mask = segmenterResult.confidenceMasks().get().get(0)) {

                    // Convert the AI mask to an OpenCV Mat
                    ByteBuffer byteBuffer = ByteBufferExtractor.extract(mask);
                    FloatBuffer confidenceMaskBuffer = byteBuffer.asFloatBuffer();
                    confidenceMaskBuffer.rewind();
                    int maskWidth = mask.getWidth();
                    int maskHeight = mask.getHeight();
                    Mat maskMat = new Mat(maskHeight, maskWidth, CvType.CV_32F);
                    float[] floatArray = new float[confidenceMaskBuffer.remaining()];
                    confidenceMaskBuffer.get(floatArray);
                    maskMat.put(0, 0, floatArray);
                    Mat mask8u = new Mat();
                    maskMat.convertTo(mask8u, CvType.CV_8U, 255.0);
                    Mat thresholdMat = new Mat(); // This is our final AI silhouette mask
                    Imgproc.threshold(mask8u, thresholdMat, 128, 255, Imgproc.THRESH_BINARY);

                    // --- START OF LOGIC FOR DETAIL SCAN ---

                    // Step 3: Isolate the subject from the original image using the AI mask
                    Mat originalMat = new Mat();
                    Utils.bitmapToMat(originalBitmap, originalMat);
                    Mat resizedMask = new Mat();
                    Imgproc.resize(thresholdMat, resizedMask, originalMat.size()); // Ensure mask and image are same size
                    Mat isolatedSubjectMat = new Mat();
                    Core.bitwise_and(originalMat, originalMat, isolatedSubjectMat, resizedMask);
                    
                    // GENTLE FIX: Add denoising here to reduce patches and darkness before processing.
                    Photo.fastNlMeansDenoisingColored(isolatedSubjectMat, isolatedSubjectMat, 3, 3, 7, 21);

                    // Step 4: Find all internal details on the isolated subject
                    Mat grayIsolated = new Mat();
                    Imgproc.cvtColor(isolatedSubjectMat, grayIsolated, Imgproc.COLOR_RGBA2GRAY);
                    Imgproc.GaussianBlur(grayIsolated, grayIsolated, new Size(3, 3), 0); // Denoise for cleaner lines
                    Mat detailLines = new Mat();
                    Imgproc.Canny(grayIsolated, detailLines, 50, 150); // Canny edge detection finds all lines

                    // Step 5: Find the strong outer outline from the AI mask
                    List<MatOfPoint> contours = new ArrayList<>();
                    Mat hierarchy = new Mat();
                    // Use a copy of resizedMask for findContours as it can be modified by the function
                    Mat contoursMask = resizedMask.clone();
                    Imgproc.findContours(contoursMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                    contoursMask.release();

                    // Step 6: Combine the outer outline and internal details
                    Mat finalDrawing = new Mat(originalBitmap.getHeight(), originalBitmap.getWidth(), CvType.CV_8UC4, new Scalar(255, 255, 255, 255));
                    Imgproc.drawContours(finalDrawing, contours, -1, new Scalar(0, 0, 0, 255), 2); // Draw the strong outline
                    finalDrawing.setTo(new Scalar(0, 0, 0, 255), detailLines); // Paint the internal details on top

                    // --- END OF LOGIC ---

                    // Convert the final result to a Bitmap
                    Bitmap finalBitmap = Bitmap.createBitmap(finalDrawing.cols(), finalDrawing.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(finalDrawing, finalBitmap);

                    ProcessingResult result = new ProcessingResult(finalBitmap, contours.size());
                    listener.onAiScanComplete(result);

                    // Clean up all OpenCV Mats to prevent memory leaks
                    maskMat.release();
                    mask8u.release();
                    thresholdMat.release();
                    hierarchy.release();
                    finalDrawing.release();
                    originalMat.release();
                    resizedMask.release();
                    isolatedSubjectMat.release();
                    grayIsolated.release();
                    detailLines.release();
                    for (MatOfPoint contour : contours) {
                        contour.release();
                    }
                }
            } else {
                throw new Exception("MediaPipe segmentation returned a null or empty result.");
            }
        } catch (Exception e) {
            Log.e(TAG, "MediaPipe AI segmentation has CRITICALLY FAILED. See exception below.", e);
            ProcessingResult failureResult = new ProcessingResult(null, 0);
            listener.onAiScanComplete(failureResult);
        } finally {
            if (imageSegmenter != null) {
                imageSegmenter.close();
            }
        }
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

    // --- Method 4: "Segmented Detail" (Now Method 2 in UI) ---
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

    // --- Method 5: "Pencil Sketch" (Now Method 3 in UI) ---
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

        // CRASH FIX: Get the object count from the valid single-channel 'sharpLines' Mat.
        int objectCount = countContours(sharpLines.clone());

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
        // CRASH FIX: Manually create the result with the correct count and the final bitmap.
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, objectCount);
        listener.onScanComplete(finalResult);

        originalMat.release(); grayMat.release(); inverted.release(); blurred.release(); sketch.release(); sharpLines.release(); invertedLines.release(); finalSketch.release();
    }

    // --- Method 6: "Selective Detail" (Now Method 4 in UI) ---
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

    // --- Method 7: "Artistic Abstraction" (Now Method 5 in UI) ---
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

    // --- Method 8: "Clean Structure" (Now Method 6 in UI) ---
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

    // --- Method 9: "Detailed Texture" (Now Method 7 in UI) ---
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

    // --- Method 10: Legacy (Now Method 8 in UI) ---
    public static void processMethod10(Bitmap originalBitmap, ScanListener listener) {
        processMethod8(originalBitmap, listener);
    }


    // --- UNIFIED PROCESSING METHOD FOR FINE-TUNING (MANUAL MODE) ---
    public static void processWithFineTuning(Bitmap originalBitmap, int method, int depth, int sharpness, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Mat finalLines = new Mat();

        // Note: The 'method' parameter is the spinner index. This switch must be updated
        // in ProcessingActivity to pass the correct logical method number.
        // Assuming the mapping is handled there, this code remains similar.
        switch (method) {
            // Cases for old methods 2 and 3 are removed.
            case 2: // Now Segmented Detail
            case 4: // Now Selective Detail
            case 5: // Now Artistic
            case 6: // Now Clean Structure
            case 8: // Now Legacy
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 3: // Now Pencil Sketch
                finalLines = getStagedPencilSketch(grayMat, depth, sharpness);
                break;
            case 7: // Now Detailed Texture
                finalLines = getStagedDetailedTextureLines(grayMat, depth, sharpness);
                break;
            default:
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
        }

        finalizeAndComplete(finalLines, listener);
        originalMat.release();
        grayMat.release();
    }

    // --- STAGED PIPELINE IMPLEMENTATIONS FOR FINE-TUNING ---

    private static Mat getStagedCleanStructureLines(Mat grayMat, int depth, int sharpness) {
        int blurKernelSize = mapSharpnessToOdd(sharpness, 11, 3);
        int cannyLow = mapSharpnessToInt(sharpness, 60, 10);
        int cleanKernelSize = mapSharpnessToInt(sharpness, 4, 2);
        int boldKernelSize = mapSharpnessToInt(sharpness, 4, 1);

        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(blurKernelSize, blurKernelSize), 0);

        Mat foundation = getCannyEdges(blurredMat, cannyLow, cannyLow * 2);
        if (depth == 0) {
            blurredMat.release();
            return foundation;
        }

        Mat detail = getCannyEdges(blurredMat, cannyLow * 2, cannyLow * 3);
        Core.bitwise_or(foundation, detail, foundation);
        if (depth == 1) {
            blurredMat.release();
            detail.release();
            return foundation;
        }

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

        Mat finalized = new Mat();
        Mat boldKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(boldKernelSize, boldKernelSize));
        Imgproc.dilate(cleaned, finalized, boldKernel);
        if (depth == 3 || depth == 4) {
            blurredMat.release();
            detail.release();
            foundation.release();
            cleaned.release();
            cleanKernel.release();
            boldKernel.release();
            return finalized;
        }

        return finalized;
    }

    private static Mat getStagedDetailedTextureLines(Mat grayMat, int depth, int sharpness) {
        int bilateralD = mapSharpnessToInt(sharpness, 20, 5);
        int largeBlockSize = mapSharpnessToOdd(sharpness, 45, 15);
        int detailBlockSize = mapSharpnessToOdd(sharpness, 25, 7);
        int cleanKernelSize = mapSharpnessToInt(sharpness, 3, 1);
        int boldKernelSize = mapSharpnessToInt(sharpness, 3, 1);

        Mat simplifiedMat = new Mat();
        Imgproc.bilateralFilter(grayMat, simplifiedMat, bilateralD, 80, 80);

        Mat foundation = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, foundation, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, largeBlockSize, 2);
        if (depth == 0) {
            simplifiedMat.release();
            return foundation;
        }

        Mat detail = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, detail, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, detailBlockSize, 2);
        Core.bitwise_or(foundation, detail, foundation);
        if (depth == 1) {
            simplifiedMat.release();
            detail.release();
            return foundation;
        }

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

        Mat finalized = new Mat();
        Mat boldKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(boldKernelSize, boldKernelSize));
        Imgproc.dilate(cleaned, finalized, boldKernel);
        if (depth == 3 || depth == 4) {
            simplifiedMat.release();
            detail.release();
            foundation.release();
            cleaned.release();
            cleanKernel.release();
            boldKernel.release();
            return finalized;
        }

        return finalized;
    }

    private static Mat getStagedPencilSketch(Mat grayMat, int depth, int sharpness) {
        int blurKernelSize = mapSharpnessToOdd(sharpness, 41, 5);

        Mat inverted = new Mat();
        Core.bitwise_not(grayMat, inverted);
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(inverted, blurred, new Size(blurKernelSize, blurKernelSize), 0);
        Mat sketch = colorDodge(grayMat, blurred);
        if (depth == 0 || depth == 1) {
            inverted.release();
            blurred.release();
            return sketch;
        }

        Mat sharpLines = getMethod8Lines(grayMat);
        Mat invertedLines = new Mat();
        Core.bitwise_not(sharpLines, invertedLines);
        Core.bitwise_and(sketch, invertedLines, sketch);
        Core.bitwise_or(sketch, sharpLines, sketch);

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
        // SAFETY FIX: Use a clone because findContours can modify the source Mat.
        Mat matCopy = mat.clone();
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(matCopy, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        int total = contours.size();
        for (MatOfPoint p : contours) {
            p.release();
        }
        hierarchy.release();
        matCopy.release();
        return total;
    }

    private static void finalizeAndComplete(Mat finalLines, ScanListener listener) {
        int totalObjects = countContours(finalLines);
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
        // SAVING FIX: The Mat must be released here, after it has been converted to a bitmap, to prevent memory errors.
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
}
