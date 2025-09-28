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
import java.nio.IntBuffer;

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

    private final float[] transformMatrix = new float[16];

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texCoordBuffer;

    private volatile boolean frameAvailable = false;
    private final Object frameSyncObject = new Object();
    private final Object maskSyncObject = new Object();

    private int currentMethod = 0;
    private float currentKsize = 0.5f;

    private Bitmap aiMaskBitmap;

    // --- FIX START: Variables to hold the texture size for the shaders ---
    private int surfaceWidth;
    private int surfaceHeight;
    // --- FIX END ---

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
        // --- FIX START: Initialize the debug logger ---
        DebugLogger.initialize();
        DebugLogger.logMessage("onSurfaceCreated: Initializing GL resources.");
        // --- FIX END ---

        String vertexShader = loadShaderFromAssets("vertex_shader.glsl");
        String fragmentShader9 = loadShaderFromAssets("fragment_shader_method9.glsl");
        String fragmentShader11 = loadShaderFromAssets("fragment_shader_method11.glsl");
        String fragmentShader12 = loadShaderFromAssets("fragment_shader_method12.glsl");

        DebugLogger.logMessage("Compiling and linking shaders for programMethod9...");
        programMethod9 = createProgram(vertexShader, fragmentShader9);
        DebugLogger.logMessage("Compiling and linking shaders for programMethod11...");
        programMethod11 = createProgram(vertexShader, fragmentShader11);
        DebugLogger.logMessage("Compiling and linking shaders for programMethod12...");
        programMethod12 = createProgram(vertexShader, fragmentShader12);

        if (programMethod9 == 0 || programMethod11 == 0 || programMethod12 == 0) {
            DebugLogger.logMessage("FATAL: One or more shader programs failed to link. Rendering will not work.");
            // A real app might show an error dialog here.
            return;
        }
        DebugLogger.logMessage("All shader programs created successfully.");

        int[] textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);
        cameraTextureId = textures[0];
        maskTextureId = textures[1];

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
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

        DebugLogger.logMessage("onSurfaceCreated: GL setup complete. Notifying activity.");
        if (surfaceReadyListener != null) {
            surfaceReadyListener.onSurfaceReady(surfaceTexture);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        DebugLogger.logMessage("onSurfaceChanged: Viewport changed to " + width + "x" + height);
        GLES20.glViewport(0, 0, width, height);
        // --- FIX START: Store the new surface dimensions ---
        this.surfaceWidth = width;
        this.surfaceHeight = height;
        // --- FIX END ---
        if (surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (frameSyncObject) {
            if (frameAvailable) {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(transformMatrix);
                frameAvailable = false;
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        int activeProgram;
        switch (currentMethod) {
            case 1: activeProgram = programMethod11; break;
            case 2: activeProgram = programMethod12; break;
            case 0: default: activeProgram = programMethod9; break;
        }

        if (activeProgram == 0) {
            return; // Don't draw if the program is invalid
        }

        GLES20.glUseProgram(activeProgram);

        int positionHandle = GLES20.glGetAttribLocation(activeProgram, "vPosition");
        int texCoordHandle = GLES20.glGetAttribLocation(activeProgram, "vTexCoord");
        int transformMatrixHandle = GLES20.glGetUniformLocation(activeProgram, "uTransformMatrix");
        int ksizeHandle = GLES20.glGetUniformLocation(activeProgram, "uKsize");
        int cameraTextureHandle = GLES20.glGetUniformLocation(activeProgram, "uCameraTexture");
        int maskTextureHandle = GLES20.glGetUniformLocation(activeProgram, "uMaskTexture");
        // --- FIX START: Get handle for the new texture size uniform ---
        int textureSizeHandle = GLES20.glGetUniformLocation(activeProgram, "uTextureSize");
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
        GLES20.glUniformMatrix4fv(transformMatrixHandle, 1, false, transformMatrix, 0);
        // --- FIX START: Pass the surface dimensions to the shader ---
        GLES20.glUniform2f(textureSizeHandle, (float)surfaceWidth, (float)surfaceHeight);
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

    public void switchMethod(int method) { this.currentMethod = method; }
    public void setKsize(int ksize) { this.currentKsize = ksize / 100.0f; }

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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                shaderSource.append(line).append("\n");
            }
        } catch (IOException e) {
            DebugLogger.logMessage("ERROR: Could not read shader file: " + fileName);
            Log.e(TAG, "Error loading shader from assets: " + fileName, e);
            return null;
        }
        return shaderSource.toString();
    }

    // --- FIX START: Rewritten to include error checking and logging ---
    private int loadShader(int type, String shaderCode) {
        if (shaderCode == null || shaderCode.isEmpty()) {
            DebugLogger.logMessage("ERROR: Shader code is empty for type " + type);
            return 0;
        }
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            DebugLogger.logMessage("ERROR: glCreateShader failed for type " + type);
            return 0;
        }
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String errorInfo = GLES20.glGetShaderInfoLog(shader);
            DebugLogger.logMessage("ERROR: Shader compilation failed. Type: " + type + "\nInfo: " + errorInfo);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        DebugLogger.logMessage("Shader compiled successfully. Type: " + type + ", ID: " + shader);
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            DebugLogger.logMessage("ERROR: glCreateProgram failed.");
            return 0;
        }
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String errorInfo = GLES20.glGetProgramInfoLog(program);
            DebugLogger.logMessage("ERROR: Program linking failed.\nInfo: " + errorInfo);
            GLES20.glDeleteProgram(program);
            return 0;
        }
        DebugLogger.logMessage("Program linked successfully. ID: " + program);
        return program;
    }
    // --- FIX END ---
}
