package com.kop.app; 

import android.content.Context; 
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraGLRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "CameraGLRenderer";

    private final Context context;
    private final GLSurfaceView glSurfaceView;

    private OnSurfaceReadyListener surfaceReadyListener;

    private int programMethod9;
    private int programMethod11;
    private int programMethod12;

    private int cameraTextureId;
    private int maskTextureId;

    private SurfaceTexture surfaceTexture;

    // --- FIX START: This matrix will hold the camera texture's transformation data. ---
    private final float[] transformMatrix = new float[16];
    // --- FIX END ---

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texCoordBuffer;

    private volatile boolean frameAvailable = false;
    private final Object frameSyncObject = new Object();
    private final Object maskSyncObject = new Object();

    private int currentMethod = 0; // 0 for Method 9, 1 for Method 11, 2 for Method 12
    private float currentKsize = 0.5f; // Normalized 0.0 - 1.0

    private Bitmap aiMaskBitmap;

    public interface OnSurfaceReadyListener {
        void onSurfaceReady(SurfaceTexture surfaceTexture);
    }

    public CameraGLRenderer(Context context, GLSurfaceView glSurfaceView) {
        this.context = context;
        this.glSurfaceView = glSurfaceView;

        final float[] vertices = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f};
        final float[] texCoords = {0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f};

        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texCoordBuffer.put(texCoords).position(0);
    }

    public void setOnSurfaceReadyListener(OnSurfaceReadyListener listener) {
        this.surfaceReadyListener = listener;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String vertexShader = loadShaderFromAssets("vertex_shader.glsl");
        String fragmentShader9 = loadShaderFromAssets("fragment_shader_method9.glsl");
        String fragmentShader11 = loadShaderFromAssets("fragment_shader_method11.glsl");
        String fragmentShader12 = loadShaderFromAssets("fragment_shader_method12.glsl");

        programMethod9 = createProgram(vertexShader, fragmentShader9);
        programMethod11 = createProgram(vertexShader, fragmentShader11);
        programMethod12 = createProgram(vertexShader, fragmentShader12);

        int[] textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);
        cameraTextureId = textures[0];
        maskTextureId = textures[1];

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_to_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        surfaceTexture = new SurfaceTexture(cameraTextureId);
        surfaceTexture.setOnFrameAvailableListener(this);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        if (surfaceReadyListener != null) {
            surfaceReadyListener.onSurfaceReady(surfaceTexture);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        if (surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (frameSyncObject) {
            if (frameAvailable) {
                surfaceTexture.updateTexImage();
                // --- FIX START: Retrieve the transformation matrix from the SurfaceTexture. ---
                surfaceTexture.getTransformMatrix(transformMatrix);
                // --- FIX END ---
                frameAvailable = false;
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        int activeProgram;
        switch (currentMethod) {
            case 1: // Method 11
                activeProgram = programMethod11;
                break;
            case 2: // Method 12
                activeProgram = programMethod12;
                break;
            case 0: // Method 9
            default:
                activeProgram = programMethod9;
                break;
        }

        GLES20.glUseProgram(activeProgram);

        int positionHandle = GLES20.glGetAttribLocation(activeProgram, "vPosition");
        int texCoordHandle = GLES20.glGetAttribLocation(activeProgram, "vTexCoord");
        int ksizeHandle = GLES20.glGetUniformLocation(activeProgram, "uKsize");
        int cameraTextureHandle = GLES20.glGetUniformLocation(activeProgram, "uCameraTexture");
        int maskTextureHandle = GLES20.glGetUniformLocation(activeProgram, "uMaskTexture");
        // --- FIX START: Get the handle for the new transformation matrix uniform. ---
        int transformMatrixHandle = GLES20.glGetUniformLocation(activeProgram, "uTransformMatrix");
        // --- FIX END ---

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glUniform1i(cameraTextureHandle, 0);

        if (currentMethod == 1 || currentMethod == 2) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId);
            synchronized (maskSyncObject) {
                if (aiMaskBitmap != null && !aiMaskBitmap.isRecycled()) {
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, aiMaskBitmap, 0);
                }
            }
            GLES20.glUniform1i(maskTextureHandle, 1);
        }

        GLES20.glUniform1f(ksizeHandle, currentKsize);
        // --- FIX START: Pass the transformation matrix to the vertex shader. ---
        GLES20.glUniformMatrix4fv(transformMatrixHandle, 1, false, transformMatrix, 0);
        // --- FIX END ---
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (frameSyncObject) {
            frameAvailable = true;
        }
        glSurfaceView.requestRender();
    }

    public void switchMethod(int method) {
        this.currentMethod = method;
    }

    public void setKsize(int ksize) {
        this.currentKsize = ksize / 100.0f;
    }

    public void updateAiMask(Bitmap maskBitmap) {
        synchronized (maskSyncObject) {
            if (this.aiMaskBitmap != null && !this.aiMaskBitmap.isRecycled()) {
                this.aiMaskBitmap.recycle();
            }
            this.aiMaskBitmap = maskBitmap;
        }
    }

    private String loadShaderFromAssets(String fileName) {
        StringBuilder shaderSource = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)));
            String line;
            while ((line = reader.readLine()) != null) {
                shaderSource.append(line).append("\n");
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Error loading shader from assets: " + fileName, e);
            return null;
        }
        return shaderSource.toString();
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        return program;
    }
}
