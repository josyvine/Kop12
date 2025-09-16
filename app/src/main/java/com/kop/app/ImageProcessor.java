package com.kop.app;

// ### THE FIX IS THIS LINE HERE ###
import java.io.FileOutputStream;
// ################################

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import java.nio.ByteBuffer;

public class ImageProcessor {

    public static Bitmap extractOutlineFromMask(Bitmap originalBitmap, SegmentationMask mask) {
        if (mask == null) {
            return null;
        }

        int maskWidth = mask.getWidth();
        int maskHeight = mask.getHeight();
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        Bitmap outlineBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888);
        outlineBitmap.eraseColor(Color.TRANSPARENT);

        ByteBuffer maskBuffer = mask.getBuffer();

        for (int y = 0; y < originalHeight; y++) {
            for (int x = 0; x < originalWidth; x++) {
                float scaleX = (float) x / originalWidth;
                float scaleY = (float) y / originalHeight;
                int maskX = (int) (scaleX * maskWidth);
                int maskY = (int) (scaleY * maskHeight);

                if (isEdgePixel(maskBuffer, maskWidth, maskHeight, maskX, maskY)) {
                    outlineBitmap.setPixel(x, y, Color.BLACK);
                }
            }
        }

        return outlineBitmap;
    }

    private static boolean isEdgePixel(ByteBuffer maskBuffer, int width, int height, int x, int y) {
        float confidenceThreshold = 0.8f;

        float centerConfidence = getConfidence(maskBuffer, width, x, y);

        if (centerConfidence < confidenceThreshold) {
            return false;
        }

        float topConfidence = getConfidence(maskBuffer, width, x, y - 1);
        float bottomConfidence = getConfidence(maskBuffer, width, x, y + 1);
        float leftConfidence = getConfidence(maskBuffer, width, x - 1, y);
        float rightConfidence = getConfidence(maskBuffer, width, x + 1, y);

        if (topConfidence < confidenceThreshold || 
            bottomConfidence < confidenceThreshold || 
            leftConfidence < confidenceThreshold || 
            rightConfidence < confidenceThreshold) {
            return true;
        }

        return false;
    }

    private static float getConfidence(ByteBuffer buffer, int width, int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= buffer.capacity() / width) {
            return 0.0f;
        }

        int position = (y * width + x);

        buffer.rewind();
        return buffer.getFloat(position * 4);
    }

    public static void saveBitmap(Bitmap bmp, String path) throws Exception {
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
