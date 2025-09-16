package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.io.FileOutputStream;
import java.util.Arrays;

public class ImageProcessor {

    /**
     * Extracts a detailed and sharp outline from a bitmap image.
     * This enhanced method uses adaptive thresholding for the Canny edge detector,
     * ensuring better results across a wide variety of images. It also dilates the
     * edges to make them more solid and visible.
     * @param originalBitmap The input image.
     * @return A new bitmap containing the black outlines on a white background.
     */
    public static Bitmap extractOutline(Bitmap originalBitmap) {
        if (originalBitmap == null) {
            return null;
        }

        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);

        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        // STEP 1: Use a smaller Gaussian blur kernel to preserve more detail.
        // The original (5, 5) kernel could smooth out fine lines before they are detected.
        // A (3, 3) kernel provides noise reduction while keeping finer edges.
        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(3, 3), 0);

        // STEP 2: Implement adaptive thresholding for the Canny edge detector.
        // Instead of fixed values (e.g., 50, 150), we calculate optimal thresholds
        // based on the image's median pixel intensity. This makes the detection
        // robust across different types of images (dark, light, low-contrast).
        double median = calculateMedian(blurredMat);
        double sigma = 0.33; // A standard value used in this technique.
        double lowerThreshold = Math.max(0, (1.0 - sigma) * median);
        double upperThreshold = Math.min(255, (1.0 + sigma) * median);
        
        Mat cannyEdges = new Mat();
        Imgproc.Canny(blurredMat, cannyEdges, lowerThreshold, upperThreshold);
        
        // STEP 3: Dilate the detected edges.
        // This process makes the lines thicker, more "sharp" to the eye, and helps
        // connect any small gaps, addressing the issue of incomplete or faint lines.
        Mat dilatedEdges = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.dilate(cannyEdges, dilatedEdges, kernel);

        // STEP 4: Create the final image.
        // We create a white background and then "paint" the black, dilated edges onto it.
        Mat finalMat = new Mat(dilatedEdges.size(), originalMat.type(), new Scalar(255, 255, 255, 255));
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        finalMat.setTo(blackColor, dilatedEdges); // Use the processed edges as the mask

        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        // IMPORTANT: Release all Mat objects to free up memory immediately.
        originalMat.release();
        grayMat.release();
        blurredMat.release();
        cannyEdges.release();
        dilatedEdges.release();
        kernel.release();
        finalMat.release();

        return resultBitmap;
    }

    /**
     * Calculates the median pixel value of a single-channel (grayscale) Mat.
     * The median is a robust measure used to automatically determine thresholds.
     * @param mat The input Mat, must be a grayscale image (e.g., CV_8UC1).
     * @return The median pixel value as a double.
     */
    private static double calculateMedian(Mat mat) {
        if (mat.empty() || mat.channels() != 1) {
            return 0.0; // Can only operate on single-channel grayscale images
        }
        
        // Flatten the image into a 1D Mat
        Mat flat = mat.reshape(1, 1);
        
        // Get all pixel values into a Java byte array
        byte[] pixels = new byte[(int) (flat.total() * flat.channels())];
        flat.get(0, 0, pixels);
        
        // Convert signed bytes (-128 to 127) to unsigned int values (0-255) for correct sorting
        int[] unsignedPixels = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            unsignedPixels[i] = pixels[i] & 0xFF;
        }
        
        // Sort the array to find the median value
        Arrays.sort(unsignedPixels);
        
        double median;
        int middle = unsignedPixels.length / 2;
        if (unsignedPixels.length % 2 == 1) {
            // If the array has an odd number of elements, the median is the middle one
            median = unsignedPixels[middle];
        } else {
            // If the array has an even number of elements, the median is the average of the two middle ones
            median = (unsignedPixels[middle - 1] + unsignedPixels[middle]) / 2.0;
        }
        
        return median;
    }


    /**
     * Saves a bitmap to a specified file path as a PNG.
     * @param bmp The bitmap to save.
     * @param path The absolute path where the file will be saved.
     * @throws Exception if there is an error during file writing.
     */
    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
        if (bmp == null) {
            return;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // Using PNG to preserve line sharpness
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
