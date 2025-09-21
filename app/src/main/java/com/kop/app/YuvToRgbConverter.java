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

    public synchronized Bitmap yuvToRgb(ImageProxy image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

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

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int yuvIndex = 0;
        for (int y = 0; y < height; y++) {
            yBuffer.position(y * yRowStride);
            yBuffer.get(yuvBytes, yuvIndex, width);
            yuvIndex += width;
        }

        int chromaHeight = height / 2;
        int chromaWidth = width / 2;
        int uvStartIndex = width * height; 

        for (int y = 0; y < chromaHeight; y++) {
            for (int x = 0; x < chromaWidth; x++) {
                // *** THIS IS THE FIX. THE VARIABLE IS NOW CORRECTLY SPELLED 'uvRowStride' ***
                int uPixelOffset = y * uvRowStride + x * uvPixelStride;
                int vPixelOffset = y * uvRowStride + x * uvPixelStride;

                int destPixelIndex = uvStartIndex + y * width + x * 2;
                
                if (destPixelIndex + 1 < yuvBytes.length) {
                    vBuffer.position(vPixelOffset);
                    yuvBytes[destPixelIndex] = vBuffer.get();
                    uBuffer.position(uPixelOffset);
                    yuvBytes[destPixelIndex + 1] = uBuffer.get();
                }
            }
        }
        
        in.copyFrom(yuvBytes);
        script.setInput(in);
        script.forEach(out);
        
        Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(outputBitmap);
        return outputBitmap;
    }
}
