package com.kop.app; 

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFilterActivity extends AppCompatActivity {

    private static final String TAG = "CameraFilterActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private static final String MODEL_FILE = "selfie_segmenter.tflite";

    private GLSurfaceView glSurfaceView;
    private CameraGLRenderer renderer;
    private ImageSegmenter imageSegmenter;
    private ExecutorService cameraExecutor;
    private YuvToRgbConverter yuvToRgbConverter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_filter);

        glSurfaceView = findViewById(R.id.camera_gl_surface_view);
        glSurfaceView.setEGLContextClientVersion(2);
        renderer = new CameraGLRenderer(this, glSurfaceView);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        cameraExecutor = Executors.newSingleThreadExecutor();
        yuvToRgbConverter = new YuvToRgbConverter(this);

        if (checkCameraPermission()) {
            setupImageSegmenter();
            startCameraAnalysis();
        } else {
            requestCameraPermission();
        }

        setupUI();
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
                renderer.switchCamera();
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
                // This is a placeholder for future implementation of capturing from GLSurfaceView
                Toast.makeText(CameraFilterActivity.this, "Capture not yet implemented.", Toast.LENGTH_SHORT).show();
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

    private void startCameraAnalysis() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindImageAnalysis(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Failed to get camera provider", e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                if (imageSegmenter != null) {
                    // This bitmap is for analysis only, not for display
                    Bitmap bitmapForAnalysis = yuvToRgbConverter.yuvToRgb(image);
                    if (bitmapForAnalysis != null) {
                        runSegmentation(bitmapForAnalysis);
                        bitmapForAnalysis.recycle(); // Clean up the analysis bitmap
                    }
                }
                image.close();
            }
        });

        // We only bind the analysis use case with CameraX.
        // The preview is handled by Camera1 API inside the renderer.
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis);
    }

    private void runSegmentation(Bitmap bitmap) {
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
            int alpha = 255;
            // We use the confidence value for all R, G, B channels to create a grayscale mask
            int grayValue = (int) (confidence * 255);
            pixels[i] = Color.argb(alpha, grayValue, grayValue, grayValue);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
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
        renderer.releaseCamera();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (imageSegmenter != null) {
            imageSegmenter.close();
        }
    }

    // --- Permission Handling ---
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupImageSegmenter();
                startCameraAnalysis();
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
