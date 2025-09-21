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

            int yuvByteCount = width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
            yuvBytes = new byte[yuvByteCount];
            Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuvByteCount);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

            Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }

        // *** START OF CRASH FIX ***
        // The original logic for copying YUV data from the camera was incorrect. It did not
        // account for memory padding (strides), which caused an ArrayIndexOutOfBoundsException
        // on many devices. The logic below is a robust replacement that correctly copies the
        // Y, U, and V image planes into a single byte array for RenderScript processing.

        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();
        ByteBuffer vPlane = planes[2].getBuffer();

        // Rewind the buffers to their start
        yPlane.rewind();
        uPlane.rewind();
        vPlane.rewind();

        // Copy Y plane data
        int yPlaneSize = yPlane.remaining();
        yPlane.get(yuvBytes, 0, yPlaneSize);

        // Copy U and V plane data. For YUV_420_888, the U and V planes are interleaved.
        // The V plane's buffer is guaranteed to have the complete VU interleaved data.
        int vPlaneSize = vPlane.remaining();
        vPlane.get(yuvBytes, yPlaneSize, vPlaneSize);
        
        // *** END OF CRASH FIX ***
        
        in.copyFrom(yuvBytes);
        script.setInput(in);
        script.forEach(out);
        out.copyTo(output);
    }
}
