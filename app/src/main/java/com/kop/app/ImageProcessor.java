package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.mlkit.vision.segmentation.SegmentationMask;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class ImageProcessor {

    public static Bitmap extractOutlineFromMask(Bitmap originalBitmap, SegmentationMask mask) {
        if (mask == null || originalBitmap == null) {
            return null;
        }

        Bitmap personBitmap = createPersonOnlyBitmap(originalBitmap, mask);
        if (personBitmap == null) {
            return null;
        }

        Mat originalMat = new Mat();
        Utils.bitmapToMat(personBitmap, originalMat);

        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);

        Mat cannyEdges = new Mat();
        // FIX #2: Lowered the thresholds to make the edge detection more sensitive.
        // This will create stronger, more connected lines instead of faint dots.
        double threshold1 = 30;
        double threshold2 = 100;
        Imgproc.Canny(blurredMat, cannyEdges, threshold1, threshold2);

        // FIX #1: Create the final output image with a solid WHITE background.
        Mat finalMat = new Mat(cannyEdges.size(), originalMat.type(), new Scalar(255, 255, 255, 255)); // White background
        
        // Define the color black for the lines.
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        
        // Use the outlines from the Canny detector as a stencil to draw black lines onto our white image.
        finalMat.setTo(blackColor, cannyEdges);

        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        originalMat.release();
        grayMat.release();
        blurredMat.release();
        cannyEdges.release();
        finalMat.release();
        personBitmap.recycle();

        return resultBitmap;
    }

    private static Bitmap createPersonOnlyBitmap(Bitmap originalBitmap, SegmentationMask mask) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        Bitmap resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        ByteBuffer maskBuffer = mask.getBuffer();
        int maskWidth = mask.getWidth();
        int maskHeight = mask.getHeight();
        float confidenceThreshold = 0.8f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float scaleX = (float) x / width;
                float scaleY = (float) y / height;
                int maskX = (int) (scaleX * maskWidth);
                int maskY = (int) (scaleY * maskHeight);

                int position = (maskY * maskWidth + maskX) * 4;
                maskBuffer.rewind();

                if (position < maskBuffer.limit()) {
                    float confidence = maskBuffer.getFloat(position);
                    if (confidence >= confidenceThreshold) {
                        resultBitmap.setPixel(x, y, originalBitmap.getPixel(x, y));
                    } else {
                        resultBitmap.setPixel(x, y, Color.TRANSPARENT);
                    }
                } else {
                     resultBitmap.setPixel(x, y, Color.TRANSPARENT);
                }
            }
        }
        return resultBitmap;
    }

    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
        if (bmp == null) {
            return;
        }
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
