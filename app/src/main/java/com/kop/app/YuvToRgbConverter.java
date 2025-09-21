package com.kop.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public class YuvToRgbConverter {
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB script;
    private Allocation in, out;
    private int width = 0;
    private int height = 0;
    private byte[] yuvBytes;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public synchronized void yuvToRgb(ImageProxy image, Bitmap output) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        // Re-create allocations if the image size changes
        if (in == null || image.getWidth() != width || image.getHeight() != height) {
            width = image.getWidth();
            height = image.getHeight();
            
            if (in != null) {
                in.destroy();
            }
            if (out != null) {
                out.destroy();
            }

            // The yuvBytes array needs to be sized for the unpadded image data.
            int yuvByteCount = width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
            yuvBytes = new byte[yuvByteCount];
            Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuvByteCount);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

            Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }

        // *** START OF FINAL CRASH FIX ***
        // This is a robust implementation to convert a YUV_420_888 ImageProxy to a flattened NV21 byte array.
        // It handles memory padding (strides) by copying each plane's data row by row, which is
        // necessary for compatibility across different Android devices.

        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        // 1. Copy the Y (Luminance) plane
        // We need to copy `width` bytes from each row, but the buffer's `rowStride`
        // might be larger than `width` due to padding.
        int yuvIndex = 0;
        for (int y = 0; y < height; y++) {
            yBuffer.position(y * yRowStride);
            yBuffer.get(yuvBytes, yuvIndex, width);
            yuvIndex += width;
        }

        // 2. Copy the U (Chrominance) and V (Chrominance) planes
        // YUV_420_888 has U/V planes subsampled by 2. We need to interleave them
        // into the `yuvBytes` array in V, U order (NV21 format) for RenderScript.
        int chromaHeight = height / 2;
        int chromaWidth = width / 2;
        int uvStartIndex = width * height; // Start of the UV data in the yuvBytes array

        for (int y = 0; y < chromaHeight; y++) {
            for (int x = 0; x < chromaWidth; x++) {
                int uPixelOffset = y * uvRowStride + x * uvPixelStride;
                int vPixelOffset = y * uvRowStride + x * uvPixelStride;

                // The destination index for this V,U pair in the flat array.
                int destPixelIndex = uvStartIndex + y * width + x * 2;
                
                // Safety check to avoid writing out of bounds
                if (destPixelIndex + 1 < yuvBytes.length) {
                    // Place V value. Read from the V buffer at its calculated offset.
                    vBuffer.position(vPixelOffset);
                    yuvBytes[destPixelIndex] = vBuffer.get();
                    
                    // Place U value. Read from the U buffer at its calculated offset.
                    uBuffer.position(uPixelOffset);
                    yuvBytes[destPixelIndex + 1] = uBuffer.get();
                }
            }
        }
        // *** END OF FINAL CRASH FIX ***
        
        in.copyFrom(yuvBytes);
        script.setInput(in);
        script.forEach(out);
        out.copyTo(output);
    }
}
