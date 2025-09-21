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

        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // Full Y channel
        yBuffer.get(yuvBytes, 0, yBuffer.remaining());

        // U and V channels
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        if (uSize == vSize) {
            // Interleave U and V into the YUV byte array
            int uOffset = yBuffer.capacity();
            int vOffset = uOffset + 1;
            
            for (int i = 0; i < uSize; i++) {
                yuvBytes[uOffset] = uBuffer.get(i);
                yuvBytes[vOffset] = vBuffer.get(i);
                uOffset += 2;
                vOffset += 2;
            }
        }
        
        in.copyFrom(yuvBytes);
        script.setInput(in);
        script.forEach(out);
        out.copyTo(output);
    }
}
