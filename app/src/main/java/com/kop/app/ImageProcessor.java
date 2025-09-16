package com.kop.app;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.io.FileOutputStream;

public class ImageProcessor {

    public static Bitmap extractOutline(Bitmap originalBitmap) {
        if (originalBitmap == null) {
            return null;
        }

        Mat originalMat = new Mat();
        Utils.bitmapToMat(originalBitmap, originalMat);

        Mat grayMat = new Mat();
        Imgproc.cvtColor(originalMat, grayMat, Imgproc.COLOR_RGBA2GRAY);

        Mat blurredMat = new Mat();
        Imgproc.GaussianBlur(grayMat, blurredMat, new Size(5, 5), 0);

        Mat cannyEdges = new Mat();
        double threshold1 = 50;
        double threshold2 = 150;
        Imgproc.Canny(blurredMat, cannyEdges, threshold1, threshold2);

        Mat finalMat = new Mat(cannyEdges.size(), originalMat.type(), new Scalar(255, 255, 255, 255));
        
        Scalar blackColor = new Scalar(0, 0, 0, 255);
        
        finalMat.setTo(blackColor, cannyEdges);

        Bitmap resultBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(finalMat, resultBitmap);

        originalMat.release();
        grayMat.release();
        blurredMat.release();
        cannyEdges.release();
        finalMat.release();

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
