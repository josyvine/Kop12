package com.kop.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
// *** THIS IS THE MISSING IMPORT THAT CAUSED THE BUILD ERROR ***
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.ByteBufferExtractor;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter;
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFilterActivity extends AppCompatActivity implements CameraGLRenderer.OnSurfaceReadyListener {

    private static final String TAG = "CameraFilterActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 201;
    private static final String MODEL_FILE = "selfie_segmenter.tflite";

    private GLSurfaceView glSurfaceView;
    private CameraGLRenderer renderer;
    private ImageSegmenter imageSegmenter;
    private ExecutorService cameraExecutor;
    private YuvToRgbConverter yuvToRgbConverter;

    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private boolean isCapturing = false;

    private SurfaceTexture rendererSurfaceTexture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_filter);

        glSurfaceView = findViewById(R.id.camera_gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setPreserveEGLContextOnPause(true);

        renderer = new CameraGLRenderer(this, glSurfaceView);
        renderer.setOnSurfaceReadyListener(this);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        cameraExecutor = Executors.newSingleThreadExecutor();
        yuvToRgbConverter = new YuvToRgbConverter(this);

        if (checkPermissions()) {
            setupImageSegmenter();
        } else {
            requestPermissions();
        }

        setupUI();
    }

    @Override
    public void onSurfaceReady(SurfaceTexture surfaceTexture) {
        this.rendererSurfaceTexture = surfaceTexture;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startCamera();
            }
        });
    }
    
    private void setupUI() {
        ImageButton closeButton = findViewById(R.id.btn_camera_close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ImageButton flipButton = findViewById(R.id.btn_flip_camera);
        flipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                } else {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                }
                startCamera();
            }
        });

        Spinner methodSpinner = findViewById(R.id.spinner_camera_method);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.camera_method_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        methodSpinner.setAdapter(adapter);
        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderer.switchMethod(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        SeekBar ksizeSlider = findViewById(R.id.slider_camera_ksize);
        ksizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                renderer.setKsize(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ImageButton captureButton = findViewById(R.id.btn_capture);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureFrame();
            }
        });
    }

    private void setupImageSegmenter() {
        try {
            BaseOptions baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_FILE).build();
            ImageSegmenter.ImageSegmenterOptions options = ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputConfidenceMasks(true)
                    .build();
            imageSegmenter = ImageSegmenter.createFromOptions(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create ImageSegmenter", e);
            Toast.makeText(this, "AI Model initialization failed.", Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindCameraUseCases();
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Failed to get camera provider", e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null || rendererSurfaceTexture == null) {
            return;
        }
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(256, 256))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (imageSegmenter != null) {
                    Bitmap bitmapForAnalysis = yuvToRgbConverter.yuvToRgb(image);
                    if (bitmapForAnalysis != null) {
                        runSegmentation(bitmapForAnalysis);
                        bitmapForAnalysis.recycle();
                    }
                }
                image.close();
            }
        });

        preview.setSurfaceProvider(new Preview.SurfaceProvider() {
            @Override
            public void onSurfaceRequested(@NonNull androidx.camera.core.SurfaceRequest request) {
                Surface surface = new Surface(rendererSurfaceTexture);
                request.provideSurface(surface, ContextCompat.getMainExecutor(CameraFilterActivity.this), new androidx.core.util.Consumer<androidx.camera.core.SurfaceRequest.Result>() {
                    @Override
                    public void accept(androidx.camera.core.SurfaceRequest.Result result) {
                        surface.release();
                    }
                });
            }
        });
        
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }


    private void runSegmentation(Bitmap bitmap) {
        if (imageSegmenter == null) return;
        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
        ImageSegmenterResult result = imageSegmenter.segment(mpImage);

        if (result != null && result.confidenceMasks().isPresent()) {
            try (MPImage mask = result.confidenceMasks().get().get(0)) {
                Bitmap maskBitmap = createBitmapFromConfidenceMask(mask);
                renderer.updateAiMask(maskBitmap);
            }
        }
    }

    private Bitmap createBitmapFromConfidenceMask(MPImage confidenceMask) {
        ByteBuffer buffer = ByteBufferExtractor.extract(confidenceMask);
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        int width = confidenceMask.getWidth();
        int height = confidenceMask.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            float confidence = floatBuffer.get(i);
            int grayValue = (int) (confidence * 255);
            pixels[i] = Color.argb(255, grayValue, grayValue, grayValue);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void captureFrame() {
        if (isCapturing) {
            return;
        }
        isCapturing = true;
        Toast.makeText(this, "Capturing...", Toast.LENGTH_SHORT).show();

        glSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                int width = glSurfaceView.getWidth();
                int height = glSurfaceView.getHeight();
                IntBuffer pixelBuffer = IntBuffer.allocate(width * height);
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);
                int[] pixels = pixelBuffer.array();
                int[] flippedPixels = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        flippedPixels[((height - 1 - y) * width) + x] = pixels[(y * width) + x];
                    }
                }
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(IntBuffer.wrap(flippedPixels));
                saveBitmap(bitmap);
            }
        });
    }

    private void saveBitmap(final Bitmap bitmap) {
        cameraExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                    String fileName = "Kop_GPU_" + timestamp + ".png";
                    File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Live_Captures");
                    if (!projectDir.exists()) {
                        projectDir.mkdirs();
                    }
                    final File outFile = new File(projectDir, fileName);
                    FileOutputStream out = new FileOutputStream(outFile);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                    out.close();
                    bitmap.recycle();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(CameraFilterActivity.this, "Saved to " + outFile.getParent(), Toast.LENGTH_LONG).show();
                            isCapturing = false;
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save bitmap", e);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(CameraFilterActivity.this, "Error saving image.", Toast.LENGTH_SHORT).show();
                            isCapturing = false;
                        }
                    });
                }
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (imageSegmenter != null) {
            imageSegmenter.close();
        }
    }

    private boolean checkPermissions() {
        int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return cameraPermission == PackageManager.PERMISSION_GRANTED && storagePermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                setupImageSegmenter();
            } else {
                Toast.makeText(this, "Camera and Storage permissions are required.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
