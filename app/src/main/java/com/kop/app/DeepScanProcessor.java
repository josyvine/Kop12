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

    // --- NEW SUPERIOR AI METHOD 01 ---
    public static void processMethod01_AiEnhanced(Context context, Bitmap originalBitmap, AiScanListener listener) {
        ImageSegmenter imageSegmenter = null;
        try {
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
                    ByteBuffer byteBuffer = ByteBufferExtractor.extract(mask);
                    FloatBuffer confidenceMaskBuffer = byteBuffer.asFloatBuffer();
                    int maskWidth = mask.getWidth();
                    int maskHeight = mask.getHeight();
                    Mat maskMat = new Mat(maskHeight, maskWidth, CvType.CV_32F);
                    float[] floatArray = new float[confidenceMaskBuffer.remaining()];
                    confidenceMaskBuffer.get(floatArray);
                    maskMat.put(0, 0, floatArray);
                    Mat mask8u = new Mat();
                    maskMat.convertTo(mask8u, CvType.CV_8U, 255.0);
                    Mat thresholdMat = new Mat();
                    Imgproc.threshold(mask8u, thresholdMat, 128, 255, Imgproc.THRESH_BINARY);

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
                    Imgproc.findContours(resizedMask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                    Mat finalDrawing = new Mat(originalBitmap.getHeight(), originalBitmap.getWidth(), CvType.CV_8UC4, new Scalar(255, 255, 255, 255));
                    Imgproc.drawContours(finalDrawing, contours, -1, new Scalar(0, 0, 0, 255), 2);
                    finalDrawing.setTo(new Scalar(0, 0, 0, 255), detailLines);

                    Bitmap finalBitmap = Bitmap.createBitmap(finalDrawing.cols(), finalDrawing.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(finalDrawing, finalBitmap);

                    ProcessingResult result = new ProcessingResult(finalBitmap, contours.size());
                    listener.onAiScanComplete(result);

                    maskMat.release(); mask8u.release(); thresholdMat.release(); hierarchy.release(); finalDrawing.release();
                    originalMat.release(); resizedMask.release(); isolatedSubjectMat.release(); grayIsolated.release(); detailLines.release();
                    for (MatOfPoint contour : contours) { contour.release(); }
                }
            } else {
                throw new Exception("MediaPipe segmentation returned a null or empty result.");
            }
        } catch (Exception e) {
            Log.e(TAG, "MediaPipe AI segmentation has CRITICALLY FAILED.", e);
            listener.onAiScanComplete(new ProcessingResult(null, 0));
        } finally {
            if (imageSegmenter != null) {
                imageSegmenter.close();
            }
        }
    }

    // --- ORIGINAL AI METHOD 0 (LOGIC UNCHANGED) ---
    public static void processMethod0(Context context, Bitmap originalBitmap, AiScanListener listener) {
        // This method is your original 'processMethod0'. The logic has not been altered.
        ImageSegmenter imageSegmenter = null;
        try {
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
                    Mat thresholdMat = new Mat();
                    Imgproc.threshold(mask8u, thresholdMat, 128, 255, Imgproc.THRESH_BINARY);

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
                    Imgproc.findContours(resizedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                    Mat finalDrawing = new Mat(originalBitmap.getHeight(), originalBitmap.getWidth(), CvType.CV_8UC4, new Scalar(255, 255, 255, 255));
                    Imgproc.drawContours(finalDrawing, contours, -1, new Scalar(0, 0, 0, 255), 2);
                    finalDrawing.setTo(new Scalar(0, 0, 0, 255), detailLines);

                    Bitmap finalBitmap = Bitmap.createBitmap(finalDrawing.cols(), finalDrawing.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(finalDrawing, finalBitmap);

                    ProcessingResult result = new ProcessingResult(finalBitmap, contours.size());
                    listener.onAiScanComplete(result);

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

    // --- NEW PREMIUM METHOD 1: "Vector Cartoon" ---
    public static void processMethod1_VectorCartoon(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 2, "Pass 1/2: Smoothing colors...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat smoothed = new Mat();
        Imgproc.bilateralFilter(grayMat, smoothed, 9, 90, 90);

        listener.onScanProgress(2, 2, "Pass 2/2: Extracting vector lines...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat lines = new Mat();
        Imgproc.adaptiveThreshold(smoothed, lines, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 2);

        Mat inverted = new Mat();
        Core.bitwise_not(lines, inverted);

        finalizeAndComplete(inverted, listener);
        originalMat.release(); grayMat.release(); smoothed.release(); lines.release(); inverted.release();
    }

    // --- NEW PREMIUM METHOD 2: "Fine Etching" ---
    public static void processMethod2_FineEtching(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 2, "Pass 1/2: Finding micro-contours...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(grayMat, blurred, new Size(3, 3), 0);
        Mat laplacian = new Mat();
        Imgproc.Laplacian(blurred, laplacian, CvType.CV_16S, 3, 1, 0, Core.BORDER_DEFAULT);

        Mat absLaplacian = new Mat();
        Core.convertScaleAbs(laplacian, absLaplacian);

        listener.onScanProgress(2, 2, "Pass 2/2: Inking the plate...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat inverted = new Mat();
        Core.bitwise_not(absLaplacian, inverted);

        finalizeAndComplete(inverted, listener);
        originalMat.release(); grayMat.release(); blurred.release(); laplacian.release(); absLaplacian.release(); inverted.release();
    }

    // --- NEW PREMIUM METHOD 3: "Geometric" ---
    public static void processMethod3_Geometric(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 2, "Pass 1/2: Identifying hard edges...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat edges = new Mat();
        Imgproc.Canny(grayMat, edges, 50, 150);

        listener.onScanProgress(2, 2, "Pass 2/2: Drafting straight lines...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat lines = new Mat();
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, 50, 50, 10);

        Mat lineArt = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(255));
        if (lines.rows() > 0) {
            for (int i = 0; i < lines.rows(); i++) {
                double[] l = lines.get(i, 0);
                Imgproc.line(lineArt, new Point(l[0], l[1]), new Point(l[2], l[3]), new Scalar(0), 2, Imgproc.LINE_AA, 0);
            }
        }

        finalizeAndComplete(lineArt, listener);
        originalMat.release(); grayMat.release(); edges.release(); lines.release();
    }

    // --- NEW PREMIUM METHOD 4: "Impressionist" ---
    public static void processMethod4_Impressionist(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat hsv = new Mat();
        Imgproc.cvtColor(originalMat, hsv, Imgproc.COLOR_RGB2HSV);

        listener.onScanProgress(1, 2, "Pass 1/2: Quantizing color palette...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat quant = new Mat(hsv.size(), hsv.type());
        int nColors = 8;
        for(int y = 0; y < hsv.rows(); y++) {
            for(int x = 0; x < hsv.cols(); x++) {
                double[] p = hsv.get(y,x);
                p[1] = (Math.round(p[1] * nColors) / (double)nColors);
                p[2] = (Math.round(p[2] * nColors) / (double)nColors);
                quant.put(y, x, p);
            }
        }
        Mat bgr = new Mat();
        Imgproc.cvtColor(quant, bgr, Imgproc.COLOR_HSV2RGB);

        listener.onScanProgress(2, 2, "Pass 2/2: Applying brush strokes...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_RGB2GRAY);
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 100, 200);
        Mat inverted = new Mat();
        Core.bitwise_not(edges, inverted);

        finalizeAndComplete(inverted, listener);
        originalMat.release(); hsv.release(); quant.release(); bgr.release(); gray.release(); edges.release(); inverted.release();
    }

    // --- NEW PREMIUM METHOD 5: "Dual Tone" ---
    public static void processMethod5_DualTone(Bitmap originalBitmap, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 2, "Pass 1/2: Separating light & shadow...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat highlights = new Mat();
        Imgproc.threshold(grayMat, highlights, 180, 255, Imgproc.THRESH_BINARY);
        Mat shadows = new Mat();
        Imgproc.threshold(grayMat, shadows, 80, 255, Imgproc.THRESH_BINARY_INV);

        listener.onScanProgress(2, 2, "Pass 2/2: Drawing boundaries...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat combined = new Mat();
        Core.bitwise_or(highlights, shadows, combined);

        finalizeAndComplete(combined, listener);
        originalMat.release(); grayMat.release(); highlights.release(); shadows.release(); combined.release();
    }

    // --- YOUR ORIGINAL METHOD 1 (Now Method 6) - LOGIC UNCHANGED ---
    public static void processMethod6_LiveAnalysis(Bitmap originalBitmap, LiveScanListener listener) {
        // This is your original 'processMethod1'. The logic has not been altered.
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
        Mat finalLines = getMethod8Lines(grayMat); // Note: This refers to a helper function, not the full method
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

    // --- YOUR ORIGINAL METHOD 2 (Now Method 7) - LOGIC UNCHANGED ---
    public static void processMethod7_Crosshatch(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod2'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 5, "Pass 1/5: Segmenting Regions...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(originalMat, bgrMat, Imgproc.COLOR_RGBA2BGR);
        Mat cannyForSeeds = getCannyEdges(grayMat, 10, 80);
        Mat markers = getWatershedMarkers(cannyForSeeds, bgrMat);

        listener.onScanProgress(2, 5, "Pass 2/5: Calculating Shading...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat shaded = createShadedRegions(markers, grayMat);

        listener.onScanProgress(3, 5, "Pass 3/5: Applying Crosshatch...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat crosshatched = applyCrosshatching(shaded);

        listener.onScanProgress(4, 5, "Pass 4/5: Finding Outlines...", createBitmapFromGrayscale(crosshatched, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat outlines = getCannyEdges(grayMat, 20, 80);

        listener.onScanProgress(5, 5, "Pass 5/5: Combining Artwork...", createBitmapFromGrayscale(crosshatched, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Core.bitwise_or(crosshatched, outlines, crosshatched);
        Mat finalLines = finalizeLines(crosshatched);

        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); bgrMat.release(); cannyForSeeds.release(); markers.release(); shaded.release(); crosshatched.release(); outlines.release();
    }

    // --- YOUR ORIGINAL METHOD 3 (Now Method 8) - LOGIC UNCHANGED ---
    public static void processMethod8_Shaded(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod3'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 4, "Pass 1/4: Segmenting Regions...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat bgrMat = new Mat();
        Imgproc.cvtColor(originalMat, bgrMat, Imgproc.COLOR_RGBA2BGR);
        Mat cannyForSeeds = getCannyEdges(grayMat, 10, 80);
        Mat markers = getWatershedMarkers(cannyForSeeds, bgrMat);

        listener.onScanProgress(2, 4, "Pass 2/4: Applying Shading...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat shaded = createShadedRegions(markers, grayMat);

        listener.onScanProgress(3, 4, "Pass 3/4: Finding Outlines...", createBitmapFromGrayscale(shaded, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat outlines = getCannyEdges(grayMat, 20, 80);

        listener.onScanProgress(4, 4, "Pass 4/4: Combining Artwork...", createBitmapFromGrayscale(shaded, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat invertedOutlines = new Mat();
        Core.bitwise_not(outlines, invertedOutlines);
        Core.bitwise_and(shaded, shaded, shaded, invertedOutlines);
        Mat finalLines = finalizeLines(shaded);

        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); bgrMat.release(); cannyForSeeds.release(); markers.release(); shaded.release(); outlines.release(); invertedOutlines.release();
    }

    // --- YOUR ORIGINAL METHOD 4 (Now Method 9) - LOGIC UNCHANGED ---
    public static void processMethod9_SegmentedDetail(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod4'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 4, "Pass 1/4: Segmenting Objects...", createBlankBitmap(originalMat.size()));
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

    // --- YOUR ORIGINAL METHOD 5 (Now Method 10) - LOGIC UNCHANGED ---
    public static void processMethod10_PencilSketch(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod5'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        listener.onScanProgress(1, 4, "Pass 1/4: Creating Soft Shading...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat inverted = new Mat();
        Core.bitwise_not(grayMat, inverted);
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(inverted, blurred, new Size(21, 21), 0);
        Mat sketch = colorDodge(grayMat, blurred);

        listener.onScanProgress(2, 4, "Pass 2/4: Finding Sharp Edges...", createBitmapFromGrayscale(sketch, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat sharpLines = getMethod8Lines(grayMat); // Note: This refers to a helper function

        listener.onScanProgress(3, 4, "Pass 3/4: Combining Shading & Lines...", createBitmapFromGrayscale(sketch, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat invertedLines = new Mat();
        Core.bitwise_not(sharpLines, invertedLines);
        Core.bitwise_and(sketch, sketch, sketch, invertedLines);

        listener.onScanProgress(4, 4, "Pass 4/4: Finalizing Artwork...", createBitmapFromGrayscale(sketch, originalMat.size()));
        try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat finalSketch = new Mat();
        Imgproc.cvtColor(sketch, finalSketch, Imgproc.COLOR_GRAY2BGRA);

        Bitmap finalBitmap = Bitmap.createBitmap(finalSketch.cols(), finalSketch.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalSketch, finalBitmap);
        ProcessingResult finalResult = new ProcessingResult(finalBitmap, countContours(sharpLines));
        listener.onScanComplete(finalResult);

        originalMat.release(); grayMat.release(); inverted.release(); blurred.release(); sketch.release(); sharpLines.release(); invertedLines.release(); finalSketch.release();
    }

    // --- YOUR ORIGINAL METHOD 6 (Now Method 11) - LOGIC UNCHANGED ---
    public static void processMethod11_SelectiveDetail(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod6'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Simplifying Structure...", createBlankBitmap(originalMat.size()));
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

    // --- YOUR ORIGINAL METHOD 7 (Now Method 12) - LOGIC UNCHANGED ---
    public static void processMethod12_ArtisticAbstraction(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod7'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Abstracting Image...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat simplifiedMat = getSimplifiedImage(grayMat);
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Major Edges...", createBlankBitmap(originalMat.size()));
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

    // --- YOUR ORIGINAL METHOD 8 (Now Method 13) - LOGIC UNCHANGED ---
    public static void processMethod13_CleanStructure(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod8'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Smoothing Surfaces...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);
        listener.onScanProgress(2, 5, "Pass 2/5: Finding Major Edges...", createBlankBitmap(originalMat.size()));
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

    // --- YOUR ORIGINAL METHOD 9 (Now Method 14) - LOGIC UNCHANGED ---
    public static void processMethod14_DetailedTexture(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod9'. The logic has not been altered.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        listener.onScanProgress(1, 5, "Pass 1/5: Simplifying Surfaces...", createBlankBitmap(originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat simplifiedMat = new Mat();
        Imgproc.bilateralFilter(grayMat, simplifiedMat, 15, 80, 80);
        Mat accumulatedLines = new Mat(originalMat.size(), CvType.CV_8UC1, new Scalar(0));
        listener.onScanProgress(2, 5, "Pass 2/5: Tracing Large Shapes...", createBitmapFromGrayscale(accumulatedLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat largeShapes = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, largeShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 25, 2);
        Core.bitwise_or(accumulatedLines, largeShapes, accumulatedLines);
        listener.onScanProgress(3, 5, "Pass 3/5: Tracing Fine Details...", createBitmapFromGrayscale(accumulatedLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Mat detailShapes = new Mat();
        Imgproc.adaptiveThreshold(simplifiedMat, detailShapes, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);
        Core.bitwise_or(accumulatedLines, detailShapes, accumulatedLines);
        Mat finalLines = finalizeLines(accumulatedLines);
        listener.onScanProgress(4, 5, "Pass 4/5: Cleaning Lines...", createBitmapFromGrayscale(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        listener.onScanProgress(5, 5, "Pass 5/5: Finalizing Artwork...", createBitmapFromGrayscale(finalLines, originalMat.size()));
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finalizeAndComplete(finalLines, listener);
        originalMat.release(); grayMat.release(); simplifiedMat.release(); accumulatedLines.release(); largeShapes.release(); detailShapes.release();
    }

    // --- YOUR ORIGINAL METHOD 10 (Now Method 15) - LOGIC UNCHANGED ---
    public static void processMethod15_Legacy(Bitmap originalBitmap, ScanListener listener) {
        // This is your original 'processMethod10', which called processMethod8. Logic is preserved.
        processMethod13_CleanStructure(originalBitmap, listener);
    }


    // --- UNIFIED PROCESSING METHOD FOR FINE-TUNING ---
    public static void processWithFineTuning(Bitmap originalBitmap, int method, int depth, int sharpness, ScanListener listener) {
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Mat finalLines = new Mat();

        // This maps the new method numbers to your original fine-tuning logic.
        switch (method) {
            case 7: // Crosshatch
            case 8: // Shaded
            case 9: // Segmented Detail
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 10: // Pencil Sketch
                finalLines = getStagedPencilSketch(grayMat, depth, sharpness);
                break;
            case 11: // Selective Detail
            case 12: // Artistic
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 13: // Clean Structure
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
            case 14: // Detailed Texture
                finalLines = getStagedDetailedTextureLines(grayMat, depth, sharpness);
                break;
            case 15: // Legacy Detailed
            default:
                finalLines = getStagedCleanStructureLines(grayMat, depth, sharpness);
                break;
        }

        finalizeAndComplete(finalLines, listener);
        originalMat.release();
        grayMat.release();
    }

    // --- STAGED PIPELINE IMPLEMENTATIONS FOR FINE-TUNING (LOGIC UNCHANGED) ---

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

    // --- HELPER FUNCTIONS (YOUR ORIGINAL LOGIC IS PRESERVED) ---

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

    private static Mat getMethod7Lines(Mat grayMat) { // Corresponds to old Method 7
        Mat simplified = getSimplifiedImage(grayMat);
        Mat major = getCannyEdges(simplified, 5, 50);
        Mat detail = getCannyEdges(simplified, 60, 120);
        Core.bitwise_or(major, detail, major);
        Mat finalLines = finalizeLines(major);
        simplified.release();
        detail.release();
        return finalLines;
    }

    private static Mat getMethod8Lines(Mat grayMat) { // Corresponds to old Method 8
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
        Imgproc.findContours(mat.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        int total = contours.size();
        for (MatOfPoint p : contours) {
            p.release();
        }
        hierarchy.release();
        return total;
    }

    // --- ### CRITICAL BUG FIX IS HERE ### ---
    private static void finalizeAndComplete(Mat finalLines, ScanListener listener) {
        int totalObjects = countContours(finalLines);
        Bitmap finalBitmap;

        // The bug was here. A 1-channel (grayscale) Mat must be converted to
        // a 4-channel (BGRA) Mat before it can be made into a standard Android Bitmap.
        // The old code was trying to create a color bitmap from a grayscale mask, resulting in black images.
        if (finalLines.channels() == 1) {
            Mat bgraMat = new Mat();
            Imgproc.cvtColor(finalLines, bgraMat, Imgproc.COLOR_GRAY2BGRA); // Convert to BGRA
            finalBitmap = Bitmap.createBitmap(bgraMat.cols(), bgraMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(bgraMat, finalBitmap); // Now this works correctly.
            bgraMat.release();
        } else {
            // This part was already correct for color images.
            finalBitmap = Bitmap.createBitmap(finalLines.cols(), finalLines.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(finalLines, finalBitmap);
        }

        ProcessingResult finalResult = new ProcessingResult(finalBitmap, totalObjects);
        listener.onScanComplete(finalResult);
        finalLines.release();
    }
    // --- ### END OF BUG FIX ### ---

    private static Bitmap createBitmapFromGrayscale(Mat grayMat, Size originalSize) {
        // This is a helper for showing intermediate results during processing.
        Mat bgraMat = new Mat();
        Imgproc.cvtColor(grayMat, bgraMat, Imgproc.COLOR_GRAY2BGRA);
        Bitmap bitmap = Bitmap.createBitmap((int)originalSize.width, (int)originalSize.height, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(bgraMat, bitmap);
        bgraMat.release();
        return bitmap;
    }
    
    private static Bitmap createBlankBitmap(Size size) {
        // Creates an empty bitmap for progress updates.
        return Bitmap.createBitmap((int)size.width, (int)size.height, Bitmap.Config.ARGB_8888);
    }

    private static Bitmap createBitmapFromMask(Mat mask, Size originalSize) {
        Mat finalMat = new Mat(originalSize, CvType.CV_8UC4, new Scalar(255, 255, 255, 255));
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        if(!mask.empty()){
            finalMat.setTo(blackColor, mask);
        }
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
