package com.kop.app;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraDialogFragment extends DialogFragment {

    public static final String TAG = "CameraDialogFragment";
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 102;

    // UI Views
    private PreviewView cameraPreview;
    private ImageView processedDisplay;
    private ImageButton settingsButton, closeButton, flipCameraButton, captureButton, lockOrientationButton;
    private LinearLayout settingsPanel;
    private Spinner methodSpinner;
    private SeekBar ksizeSlider;
    private Button analyzeButton;
    private TextView recordingTimer;

    // CameraX objects
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ImageAnalysis imageAnalysis;

    // State management
    private int selectedMethod = 0;
    private int ksize = 50;
    private boolean isRecording = false;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long recordingStartTime = 0;

    // Media Handling
    private VideoEncoder videoEncoder;
    private File videoOutputFile;
    
    // RenderScript components for correct YUV to RGB conversion
    private RenderScript renderScript;
    private ScriptIntrinsicYuvToRGB yuvToRgbScript;
    private Allocation yuvAllocation, rgbAllocation;
    private byte[] yuvByteArray;

    public static CameraDialogFragment newInstance() {
        return new CameraDialogFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        cameraExecutor = Executors.newSingleThreadExecutor();
        // Initialize RenderScript
        renderScript = RenderScript.create(requireContext());
        yuvToRgbScript = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupListeners();
        setupDefaultParameters();
        startCamera();
    }

    private void initializeViews(View view) {
        cameraPreview = view.findViewById(R.id.camera_preview);
        processedDisplay = view.findViewById(R.id.iv_processed_display);
        settingsButton = view.findViewById(R.id.btn_camera_settings);
        closeButton = view.findViewById(R.id.btn_camera_close);
        flipCameraButton = view.findViewById(R.id.btn_flip_camera);
        captureButton = view.findViewById(R.id.btn_capture);
        lockOrientationButton = view.findViewById(R.id.btn_lock_orientation);
        settingsPanel = view.findViewById(R.id.settings_panel);
        methodSpinner = view.findViewById(R.id.spinner_camera_method);
        ksizeSlider = view.findViewById(R.id.slider_camera_ksize);
        analyzeButton = view.findViewById(R.id.btn_camera_analyze);
        recordingTimer = view.findViewById(R.id.tv_recording_timer);
    }

    private void setupDefaultParameters() {
        // Default to "Method 9 (Pencil Sketch)"
        selectedMethod = 0;
        ksize = 50;

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.camera_method_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        methodSpinner.setAdapter(adapter);
        methodSpinner.setSelection(selectedMethod);

        ksizeSlider.setProgress(ksize);
    }

    private void setupListeners() {
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsPanel.setVisibility(View.VISIBLE);
            }
        });

        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsPanel.setVisibility(View.GONE);
            }
        });

        flipCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                } else {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                }
                // Re-bind use cases to apply the new camera
                startCamera();
            }
        });

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    takePhoto();
                } else {
                    stopRecording();
                }
            }
        });

        captureButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isRecording) {
                    if (checkAudioPermission()) {
                        startRecording();
                    } else {
                        requestAudioPermission();
                    }
                }
                return true;
            }
        });

        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Instantly update the method used by the analyzer
                selectedMethod = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        ksizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Instantly update the ksize used by the analyzer
                ksize = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindCameraUseCases();
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Failed to get camera provider", e);
                    Toast.makeText(getContext(), "Error starting camera.", Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }

        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        
        Size targetResolution = new Size(640, 480);
        
        // Prepare RenderScript allocations based on the target resolution
        Type.Builder yuvTypeBuilder = new Type.Builder(renderScript, Element.U8(renderScript)).setYuvFormat(ImageFormat.YUV_420_888);
        yuvTypeBuilder.setX(targetResolution.getWidth());
        yuvTypeBuilder.setY(targetResolution.getHeight());
        yuvAllocation = Allocation.createTyped(renderScript, yuvTypeBuilder.create(), Allocation.USAGE_SCRIPT);

        Type.Builder rgbTypeBuilder = new Type.Builder(renderScript, Element.RGBA_8888(renderScript));
        rgbTypeBuilder.setX(targetResolution.getWidth());
        rgbTypeBuilder.setY(targetResolution.getHeight());
        rgbAllocation = Allocation.createTyped(renderScript, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT);

        int yuvByteCount = targetResolution.getWidth() * targetResolution.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        yuvByteArray = new byte[yuvByteCount];

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                try {
                    final Bitmap inputBitmap = imageProxyToBitmap(image);
                    if (inputBitmap == null) {
                        return; // Conversion failed, skip frame
                    }

                    final int currentMethod = selectedMethod;
                    final int currentKsize = ksize;

                    processFrame(inputBitmap, currentMethod, currentKsize, new DeepScanProcessor.AiScanListener() {
                        @Override
                        public void onAiScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                            if (finalResult != null && finalResult.resultBitmap != null) {
                                final Bitmap processedBitmap = finalResult.resultBitmap;
                                
                                if (isRecording && videoEncoder != null) {
                                    Bitmap bitmapForEncoder = processedBitmap.copy(processedBitmap.getConfig(), false);
                                    videoEncoder.encodeFrame(bitmapForEncoder);
                                }
                                
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            processedDisplay.setImageBitmap(processedBitmap);
                                        }
                                    });
                                }
                            }
                            // The original bitmap from the camera is no longer needed after processing
                            inputBitmap.recycle();
                        }
                    });

                } finally {
                    image.close();
                }
            }
        });

        try {
            cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void processFrame(Bitmap bitmap, int method, int ksizeVal, DeepScanProcessor.AiScanListener listener) {
        switch (method) {
            case 0: // Method 9 (Pencil Sketch)
                DeepScanProcessor.processMethod11(bitmap, ksizeVal, new DeepScanProcessor.ScanListenerWithKsize() {
                    @Override
                    public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
                    
                    @Override
                    public void onScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                        listener.onAiScanComplete(finalResult);
                    }
                });
                break;
            case 1: // AI Method 11 (Person Sketch)
                DeepScanProcessor.processMethod12(getContext(), bitmap, ksizeVal, listener);
                break;
            case 2: // AI Method 12 (Line Art BG)
                DeepScanProcessor.processMethod13(getContext(), bitmap, ksizeVal, listener);
                break;
            default:
                DeepScanProcessor.processMethod11(bitmap, ksizeVal, new DeepScanProcessor.ScanListenerWithKsize() {
                     @Override
                    public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
                    
                    @Override
                    public void onScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                        listener.onAiScanComplete(finalResult);
                    }
                });
                break;
        }
    }


    private void takePhoto() {
        if (processedDisplay.getDrawable() == null) {
            Toast.makeText(getContext(), "Camera not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        captureButton.setEnabled(false);
        Bitmap bitmapToSave = ((BitmapDrawable) processedDisplay.getDrawable()).getBitmap();

        if (bitmapToSave != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                        String fileName = "Kop_Live_" + timestamp + ".png";
                        File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Live_Captures");
                        if (!projectDir.exists()) {
                            projectDir.mkdirs();
                        }
                        
                        File outFile = new File(projectDir, fileName);

                        ImageProcessor.saveBitmap(bitmapToSave, outFile.getAbsolutePath());

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "Photo saved to " + outFile.getParent(), Toast.LENGTH_LONG).show();
                                    captureButton.setEnabled(true);
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save photo", e);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "Error saving photo.", Toast.LENGTH_SHORT).show();
                                    captureButton.setEnabled(true);
                                }
                            });
                        }
                    }
                }
            }).start();
        }
    }

    private void startRecording() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "Kop_Live_Video_" + timestamp + ".mp4";
            File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Live_Captures");
            if (!projectDir.exists()) {
                projectDir.mkdirs();
            }
            videoOutputFile = new File(projectDir, fileName);

            videoEncoder = new VideoEncoder(640, 480, 2000000, videoOutputFile);
            videoEncoder.start();
            
            isRecording = true;
            captureButton.setImageResource(R.drawable.ic_videocam);
            settingsPanel.setVisibility(View.GONE);
            settingsButton.setVisibility(View.GONE);
            flipCameraButton.setVisibility(View.GONE);

            recordingStartTime = SystemClock.elapsedRealtime();
            timerHandler.post(timerRunnable);
            recordingTimer.setVisibility(View.VISIBLE);

        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(getContext(), "Failed to start recording.", Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }

    private void stopRecording() {
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder = null;
        }

        isRecording = false;
        captureButton.setImageResource(R.drawable.ic_capture);
        settingsButton.setVisibility(View.VISIBLE);
        flipCameraButton.setVisibility(View.VISIBLE);

        timerHandler.removeCallbacks(timerRunnable);
        recordingTimer.setVisibility(View.GONE);
        
        Toast.makeText(getContext(), "Video saved to " + videoOutputFile.getParent(), Toast.LENGTH_LONG).show();
    }
    
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = SystemClock.elapsedRealtime() - recordingStartTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            recordingTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
            timerHandler.postDelayed(this, 500);
        }
    };
    
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        if (yuvAllocation == null) {
            return null; // RenderScript not ready
        }
    
        // Copy YUV data from ImageProxy planes to a single byte array
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        yBuffer.get(yuvByteArray, 0, yBuffer.remaining());

        int uOffset = image.getWidth() * image.getHeight();
        int vOffset = uOffset + 1;
        
        int uPixelStride = uPlane.getPixelStride();
        int vPixelStride = vPlane.getPixelStride();

        // This handles both planar (I420) and semi-planar (NV21) formats
        for (int i = 0; i < image.getHeight() / 2; ++i) {
            for (int j = 0; j < image.getWidth() / 2; ++j) {
                int uIndex = i * uPlane.getRowStride() + j * uPixelStride;
                int vIndex = i * vPlane.getRowStride() + j * vPixelStride;
                
                yuvByteArray[uOffset] = uBuffer.get(uIndex);
                yuvByteArray[vOffset] = vBuffer.get(vIndex);

                uOffset += 2;
                vOffset += 2;
            }
        }

        yuvAllocation.copyFrom(yuvByteArray);
        yuvToRgbScript.setInput(yuvAllocation);
        yuvToRgbScript.forEach(rgbAllocation);

        Bitmap outputBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        rgbAllocation.copyTo(outputBitmap);
        
        // --- Apply rotation and flip using OpenCV on the correctly converted bitmap ---
        Mat rgbaMat = new Mat();
        Utils.bitmapToMat(outputBitmap, rgbaMat);
        outputBitmap.recycle(); // Done with the initial bitmap

        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation != 0) {
            int openCVRotationCode = -1;
            if (rotation == 90) openCVRotationCode = Core.ROTATE_90_CLOCKWISE;
            if (rotation == 180) openCVRotationCode = Core.ROTATE_180;
            if (rotation == 270) openCVRotationCode = Core.ROTATE_90_COUNTERCLOCKWISE;
            if (openCVRotationCode != -1) {
                Core.rotate(rgbaMat, rgbaMat, openCVRotationCode);
            }
        }
        
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            Core.flip(rgbaMat, rgbaMat, 1);
        }
        
        Bitmap finalBitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgbaMat, finalBitmap);
        rgbaMat.release();

        return finalBitmap;
    }

    private boolean checkAudioPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(getContext(), "Audio permission is required to record video.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isRecording) {
            stopRecording();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (renderScript != null) {
            renderScript.destroy();
            yuvAllocation.destroy();
            rgbAllocation.destroy();
            yuvToRgbScript.destroy();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }
}
