package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.annotation.NonNull;

// FIX: Added all necessary import statements for ML Kit and Tasks API. 
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageProcessor {

    // Listener interface to handle the asynchronous result of ML processing.
    public interface OutlineExtractionListener {
        void onOutlineExtracted(Bitmap resultBitmap);
        void onExtractionFailed(Exception e);
    }

    /**
     * Extracts a detailed and sharp outline from a bitmap image using ML segmentation.
     * This is an asynchronous operation.
     *
     * @param originalBitmap The input image.
     * @param listener       The callback to be invoked when processing is complete or fails.
     */
    public static void extractOutline(final Bitmap originalBitmap, final OutlineExtractionListener listener) {
        if (originalBitmap == null) {
            listener.onExtractionFailed(new IllegalArgumentException("Input bitmap cannot be null."));
            return;
        }

        // Configure the ML Kit Selfie Segmenter
        SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build();
        Segmenter segmenter = Segmentation.getClient(options);

        InputImage image = InputImage.fromBitmap(originalBitmap, 0);

        Task<SegmentationMask> result = segmenter.process(image)
            .addOnSuccessListener(new OnSuccessListener<SegmentationMask>() {
                @Override
                public void onSuccess(SegmentationMask mask) {
                    // ML model successfully created a mask. Now process it with OpenCV.
                    Bitmap processedBitmap = processMaskAndCombine(originalBitmap, mask);
                    listener.onOutlineExtracted(processedBitmap);
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // ML model failed.
                    listener.onExtractionFailed(e);
                }
            });
    }

    /**
     * Core processing logic that uses the ML mask to generate the final line art.
     */
    private static Bitmap processMaskAndCombine(Bitmap originalBitmap, SegmentationMask mask) {
        // STEP 1: Convert the ML Kit SegmentationMask to an OpenCV Mat
        Mat maskMat = convertMaskToMat(mask);

        // STEP 2: Find the main outer contour from the mask for a sharp, solid outline.
        Mat contourMat = new Mat(maskMat.size(), CvType.CV_8UC1, new Scalar(0));
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(maskMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        // Draw the found contours onto our contourMat. -1 means draw all contours.
        // The thickness is 2 to make the line bold and sharp.
        Imgproc.drawContours(contourMat, contours, -1, new Scalar(255), 2);

        // STEP 3: Get detailed internal edges using Canny, but only on the subject.
        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(3, 3), 0);

        // Use adaptive thresholds for better Canny results
        double median = calculateMedian(blurredMat);
        double sigma = 0.33;
        double lowerThreshold = Math.max(0, (1.0 - sigma) * median);
        double upperThreshold = Math.min(255, (1.0 + sigma) * median);
        Mat cannyEdges = new Mat();
        Imgproc.Canny(blurredMat, cannyEdges, lowerThreshold, upperThreshold);
        
        // Use the mask to remove any Canny edges from the background.
        Mat subjectOnlyEdges = new Mat();
        cannyEdges.copyTo(subjectOnlyEdges, maskMat);

        // STEP 4: Combine the sharp outer contour and the detailed inner edges.
        Core.bitwise_or(contourMat, subjectOnlyEdges, contourMat);

        // STEP 5: Create the final black-on-white bitmap result.
        Mat finalMat = new Mat(contourMat.size(), originalMat.type(), new Scalar(255, 255, 255, 255));
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        finalMat.setTo(blackColor, contourMat); // Use the combined edges as the mask

        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        // Release all Mats
        maskMat.release();
        contourMat.release();
        hierarchy.release();
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        cannyEdges.release();
        subjectOnlyEdges.release();
        finalMat.release();

        return resultBitmap;
    }
    
    /**
     * Converts the ByteBuffer from an ML Kit SegmentationMask into a usable OpenCV Mat.
     */
    private static Mat convertMaskToMat(SegmentationMask mask) {
        ByteBuffer buffer = mask.getBuffer();
        int width = mask.getWidth();
        int height = mask.getHeight();

        Mat mat = new Mat(height, width, CvType.CV_8UC1);
        byte[] maskData = new byte[buffer.remaining()];
        buffer.get(maskData);

        byte[] matData = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // The buffer contains confidence values from 0.0 to 1.0.
                // We convert it to a binary mask: 255 (white) for the person, 0 (black) for background.
                float confidence = ((float) (maskData[y * width + x] & 0xFF)) / 255.0f;
                if (confidence > 0.5) { // Confidence threshold
                    matData[y * width + x] = (byte) 255;
                } else {
                    matData[y * width + x] = (byte) 0;
                }
            }
        }
        mat.put(0, 0, matData);
        return mat;
    }

    /**
     * Calculates the median pixel value of a single-channel (grayscale) Mat.
     */
    private static double calculateMedian(Mat mat) {
        if (mat.empty() || mat.channels() != 1) return 0.0;
        Mat flat = mat.reshape(1, 1);
        byte[] pixels = new byte[(int) (flat.total() * flat.channels())];
        flat.get(0, 0, pixels);
        int[] unsignedPixels = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            unsignedPixels[i] = pixels[i] & 0xFF;
        }
        Arrays.sort(unsignedPixels);
        double median;
        int middle = unsignedPixels.length / 2;
        if (unsignedPixels.length % 2 == 1) {
            median = unsignedPixels[middle];
        } else {
            median = (unsignedPixels[middle - 1] + unsignedPixels[middle]) / 2.0;
        }
        return median;
    }

    /**
     * Saves a bitmap to a specified file path as a PNG.
     */
    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
        if (bmp == null) return;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
