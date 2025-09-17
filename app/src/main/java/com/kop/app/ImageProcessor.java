package com.kop.app;

import android.graphics.Bitmap;
import java.io.FileOutputStream; 

/**
 * A utility class for handling bitmap operations, such as saving to a file.
 * The core processing logic has been moved to DeepScanProcessor.
 */
public class ImageProcessor {

    /**
     * Saves a bitmap to a specified file path as a PNG.
     * This method is now the sole responsibility of this class.
     *
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
            // Using PNG format to preserve the sharpness of the final line art.
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
