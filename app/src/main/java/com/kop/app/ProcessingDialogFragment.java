package com.kop.app;   

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix; 
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class ProcessingDialogFragment extends DialogFragment {

    public static final String TAG = "ProcessingDialog";
    private static final String ARG_FILE_PATH = "file_path";

    private ImageView mainDisplay;
    private ImageView overlayDisplay;
    private TextView statusTextView;
    private ProgressBar progressBar;
    private LinearLayout analysisControlsContainer, fpsControlsContainer;
    private RecyclerView filmStripRecyclerView;
    private FilmStripAdapter filmStripAdapter;
    private Button analyzeButton, closeButton;
    private ImageButton settingsButton;
    private Spinner fpsSpinner, methodSpinner;
    private Handler uiHandler;
    private String inputFilePath;
    private File[] rawFrames;
    private String rawFramesDir, processedFramesDir;
    private int selectedMethod = 0;

    private LinearLayout fineTuningControls;
    private SeekBar sliderDepth, sliderSharpness;
    private Button btnSave;
    private Switch switchAutomaticScan;
    private Bitmap sourceBitmapForTuning;
    private boolean isFirstFineTuneAnalysis = true;

    private LinearLayout ksizeControlsContainer;
    private SeekBar sliderKsize;

    private LinearLayout scanStatusContainer;
    private TextView scanStatusTextView;
    private ProgressBar scanProgressBar;

    private OnDialogClosedListener closeListener;

    public interface OnDialogClosedListener {
        void onDialogClosed();
    }

    public static ProcessingDialogFragment newInstance(String filePath) {
        ProcessingDialogFragment fragment = new ProcessingDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            inputFilePath = getArguments().getString(ARG_FILE_PATH, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_processing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        if (inputFilePath != null && !inputFilePath.isEmpty()) {
            startInitialSetup();
        } else {
            showErrorDialog("Error", "No input file path provided.", true);
        }
    }

    public void setOnDialogClosedListener(OnDialogClosedListener listener) {
        this.closeListener = listener;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (closeListener != null) {
            closeListener.onDialogClosed();
        }
    }

    private void initializeViews(View view) {
        mainDisplay = view.findViewById(R.id.iv_main_display);
        overlayDisplay = view.findViewById(R.id.iv_overlay_display);
        statusTextView = view.findViewById(R.id.tv_status);
        progressBar = view.findViewById(R.id.progress_bar);
        analysisControlsContainer = view.findViewById(R.id.analysis_controls);
        fpsControlsContainer = view.findViewById(R.id.fps_controls);
        filmStripRecyclerView = view.findViewById(R.id.rv_film_strip);
        analyzeButton = view.findViewById(R.id.btn_analyze);
        fpsSpinner = view.findViewById(R.id.spinner_fps);
        methodSpinner = view.findViewById(R.id.spinner_method);
        closeButton = view.findViewById(R.id.btn_close);
        settingsButton = view.findViewById(R.id.btn_settings);
        uiHandler = new Handler(Looper.getMainLooper());

        scanStatusTextView = view.findViewById(R.id.tv_scan_status);
        scanProgressBar = view.findViewById(R.id.scan_progress_bar);
        scanStatusContainer = view.findViewById(R.id.scan_status_container);

        fineTuningControls = view.findViewById(R.id.fine_tuning_controls);
        sliderDepth = view.findViewById(R.id.slider_depth);
        sliderSharpness = view.findViewById(R.id.slider_sharpness);
        btnSave = view.findViewById(R.id.btn_save);
        switchAutomaticScan = view.findViewById(R.id.switch_automatic_scan);

        ksizeControlsContainer = view.findViewById(R.id.ksize_controls_container);
        sliderKsize = view.findViewById(R.id.slider_ksize);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (analysisControlsContainer.getVisibility() == View.VISIBLE) {
                    analysisControlsContainer.setVisibility(View.GONE);
                } else {
                    analysisControlsContainer.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void startInitialSetup() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    prepareDirectories();
                    final boolean isVideo = isVideoFile(inputFilePath);

                    if (isVideo) {
                        extractFramesForVideo(12);
                    } else {
                        rawFrames = extractOrCopyFrames(inputFilePath);
                    }

                    if (rawFrames != null && rawFrames.length > 0) {
                        sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames[0].getAbsolutePath());

                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateMainDisplay(sourceBitmapForTuning);
                                setupAnalysisControls(isVideo);
                                statusTextView.setText("Ready. Select a method and press Analyze.");
                                progressBar.setIndeterminate(false);
                                progressBar.setVisibility(View.GONE);
                            }
                        });
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Initial setup failed", e);
                    showErrorDialog("Setup Error", "Failed to prepare files.", true);
                }
            }
        }).start();
    }

    private void setupAnalysisControls(boolean isVideo) {
        if (isVideo) {
            settingsButton.setVisibility(View.VISIBLE);
            analysisControlsContainer.setVisibility(View.GONE);
        } else {
            settingsButton.setVisibility(View.GONE);
            analysisControlsContainer.setVisibility(View.VISIBLE);
        }
        fineTuningControls.setVisibility(View.GONE);

        ArrayAdapter<CharSequence> methodAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.method_options, android.R.layout.simple_spinner_item);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        methodSpinner.setAdapter(methodAdapter);
        methodSpinner.setSelection(selectedMethod, false);
        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMethod = position;
                updateControlsVisibility();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMethod = 0;
                updateControlsVisibility();
            }
        });

        if (isVideo) {
            fpsControlsContainer.setVisibility(View.VISIBLE);
            ArrayAdapter<CharSequence> fpsAdapter = ArrayAdapter.createFromResource(getContext(),
                    R.array.fps_options, android.R.layout.simple_spinner_item);
            fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            fpsSpinner.setAdapter(fpsAdapter);
            fpsSpinner.setSelection(2);

            fpsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String selectedFpsStr = parent.getItemAtPosition(position).toString();
                    int selectedFps = Integer.parseInt(selectedFpsStr.replace(" FPS", ""));
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            extractFramesForVideo(selectedFps);
                        }
                    }).start();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        switchAutomaticScan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateControlsVisibility();
            }
        });

        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginAnalysis();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentImage();
            }
        });
    }
    
    private void updateControlsVisibility() {
        boolean isAutomatic = switchAutomaticScan.isChecked();

        // Hide all special controls by default
        fineTuningControls.setVisibility(View.GONE);
        ksizeControlsContainer.setVisibility(View.GONE);

        if (!isAutomatic) { // Manual mode is enabled, show controls based on method
            boolean needsDepthSharpness = selectedMethod > 2 && selectedMethod < 10;
            boolean needsKsize = selectedMethod >= 10 && selectedMethod <= 12;

            if (needsDepthSharpness) {
                fineTuningControls.setVisibility(View.VISIBLE);
            } else if (needsKsize) {
                fineTuningControls.setVisibility(View.VISIBLE);
                ksizeControlsContainer.setVisibility(View.VISIBLE);
            }
        }
        // If automatic is checked, all special controls remain hidden.
    }

    private void beginAnalysis() {
        analyzeButton.setEnabled(false);
        settingsButton.setEnabled(false);
        analysisControlsContainer.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);

        if (rawFrames == null || rawFrames.length == 0) {
            showErrorDialog("Processing Error", "No frames available to process.", true);
            analyzeButton.setEnabled(true);
            settingsButton.setEnabled(true);
            analysisControlsContainer.setVisibility(View.VISIBLE);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (switchAutomaticScan.isChecked()) {
                        processAllFramesAutomatically();
                    } else {
                        processSingleFrameManually();
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Analysis failed", e);
                    String message = (e.getMessage() != null) ? e.getMessage() : "An unknown error occurred.";
                    showErrorDialog("Processing Error", message, true);
                }
            }
        }).start();
    }

    private void processSingleFrameManually() throws Exception {
        if (selectedMethod == 0 || selectedMethod == 1) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusTextView.setText("Performing AI Analysis...");
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setIndeterminate(true);
                }
            });
            beginAutomaticAiScan(selectedMethod);
            return;
        }

        if (selectedMethod >= 10 && selectedMethod <= 12) {
             if (switchAutomaticScan.isChecked()) {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String methodName = "The selected method";
                        if (selectedMethod == 10) methodName = "Method 9";
                        if (selectedMethod == 11) methodName = "AI Method 11";
                        if (selectedMethod == 12) methodName = "AI Method 12";
                        Toast.makeText(getContext(), methodName + " requires Automatic Scan to be OFF for manual tuning.", Toast.LENGTH_LONG).show();
                        analysisControlsContainer.setVisibility(View.VISIBLE);
                        analyzeButton.setEnabled(true);
                        settingsButton.setEnabled(true);
                    }
                });
                return;
            }
        }

        if (isFirstFineTuneAnalysis) {
            sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames[0].getAbsolutePath());
        }

        if (selectedMethod == 10) {
            performMethod9Analysis();
        } else if (selectedMethod == 11 || selectedMethod == 12) {
            performNewAiAnalysis(selectedMethod);
        } else if (selectedMethod == 2) {
            beginMethod1LiveScan(sourceBitmapForTuning, 0);
        } else {
            performFineTuningAnalysis();
        }
    }

    private void processAllFramesAutomatically() throws Exception {
        final int totalFrames = rawFrames.length;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
            }
        });
        for (int i = 0; i < totalFrames; i++) {
            final int frameNum = i + 1;
            final int frameIndex = i;

            updateStatus("Processing frame " + frameNum + " of " + totalFrames, false);
            updateProgress(frameNum, totalFrames);
            updateCurrentFrameHighlight(frameIndex);

            Bitmap orientedBitmap = decodeAndRotateBitmap(rawFrames[frameIndex].getAbsolutePath());
            if (orientedBitmap == null) continue;

            if (selectedMethod == 0 || selectedMethod == 1) {
                beginBlockingAiScan(orientedBitmap, frameIndex);
            } else if (selectedMethod == 10 || selectedMethod == 11 || selectedMethod == 12) {
                beginBlockingPencilOrNewAiScan(orientedBitmap, frameIndex);
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            } else if (selectedMethod == 2) {
                beginMethod1LiveScan(orientedBitmap, frameIndex);
            } else {
                beginStandardScan(orientedBitmap, frameIndex);
            }

            orientedBitmap.recycle();
        }

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVideoFile(inputFilePath)) {
                    showSuccessDialog("Processing Complete", "Your rotoscoped frames have been saved to:\n\n" + processedFramesDir);
                    statusTextView.setText("Processing Complete. Open settings to run again.");
                } else {
                    statusTextView.setText("Automatic scan complete. Select another method to run again.");
                    analysisControlsContainer.setVisibility(View.VISIBLE);
                    btnSave.setVisibility(View.VISIBLE);
                }
                
                progressBar.setVisibility(View.GONE);
                analyzeButton.setEnabled(true);
                settingsButton.setEnabled(true);
            }
        });
    }
    
    private void beginAutomaticAiScan(final int methodIndex) {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "Source image not found for AI scan.", true);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
                    @Override
                    public void onAiScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (finalResult.resultBitmap == null) {
                                    showErrorDialog("AI Analysis Failed", "The AI model could not process the image. Please try a different image or method.", false);
                                    statusTextView.setText("AI Analysis Failed. Ready to try again.");
                                } else {
                                    updateMainDisplay(finalResult.resultBitmap);
                                    statusTextView.setText("AI Analysis Complete. Save or choose another method.");
                                    btnSave.setVisibility(View.VISIBLE);
                                }
                                progressBar.setVisibility(View.GONE);
                                analysisControlsContainer.setVisibility(View.VISIBLE);
                                analyzeButton.setEnabled(true);
                                settingsButton.setEnabled(true);
                            }
                        });
                    }
                };
                
                if (methodIndex == 1) { // Method 0 (AI Smart Outline) is at index 1
                    DeepScanProcessor.processMethod0(getContext(), sourceBitmapForTuning, listener);
                } else { // Method 01 (AI Composite) is at index 0
                    DeepScanProcessor.processMethod01(getContext(), sourceBitmapForTuning, listener);
                }
            }
        }).start();
    }

    private void beginBlockingAiScan(Bitmap bitmap, final int frameIndex) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
            @Override
            public void onAiScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                if (finalResult != null && finalResult.resultBitmap != null) {
                    updateMainDisplay(finalResult.resultBitmap);
                    String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                    try {
                        ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save processed AI frame.", e);
                    }
                } else {
                    Log.e(TAG, "AI analysis returned null for frame " + frameIndex);
                }
                latch.countDown();
            }
        };

        if (selectedMethod == 1) { // Method 0 (AI Smart Outline)
            DeepScanProcessor.processMethod0(getContext(), bitmap, listener);
        } else { // Method 01 (AI Composite)
            DeepScanProcessor.processMethod01(getContext(), bitmap, listener);
        }

        latch.await();
    }

    private void beginBlockingPencilOrNewAiScan(Bitmap bitmap, final int frameIndex) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final int ksize = 10; // Default ksize for automatic mode, can be adjusted if needed

        if (selectedMethod == 10) { // Method 9 (Pencil Sketch)
            DeepScanProcessor.ScanListenerWithKsize listener = new DeepScanProcessor.ScanListenerWithKsize() {
                @Override
                public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
                @Override
                public void onScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                     if (finalResult != null && finalResult.resultBitmap != null) {
                        updateMainDisplay(finalResult.resultBitmap);
                        String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                        try {
                            ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to save processed pencil sketch frame.", e);
                        }
                    }
                    latch.countDown();
                }
            };
            DeepScanProcessor.processMethod11(bitmap, ksize, listener);
        } else { // AI Methods 11 and 12
            DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
                @Override
                public void onAiScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                    if (finalResult != null && finalResult.resultBitmap != null) {
                        updateMainDisplay(finalResult.resultBitmap);
                        String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                        try {
                            ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to save processed AI pencil frame.", e);
                        }
                    }
                    latch.countDown();
                }
            };

            if (selectedMethod == 11) {
                DeepScanProcessor.processMethod12(getContext(), bitmap, ksize, listener);
            } else if (selectedMethod == 12) {
                DeepScanProcessor.processMethod13(getContext(), bitmap, ksize, listener);
            }
        }
        latch.await();
    }

    private void performNewAiAnalysis(final int methodToRun) {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "Source image not found for AI scan.", true);
            return;
        }

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                statusTextView.setText("Performing AI Analysis...");
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
            }
        });
        
        final int ksize = sliderKsize.getProgress();

        new Thread(new Runnable() {
            @Override
            public void run() {
                DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
                    @Override
                    public void onAiScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (finalResult.resultBitmap == null) {
                                    showErrorDialog("AI Analysis Failed", "The AI model could not process the image. Please try a different image or method.", false);
                                    statusTextView.setText("AI Analysis Failed. Ready to try again.");
                                } else {
                                    updateMainDisplay(finalResult.resultBitmap);
                                    statusTextView.setText("AI Analysis Complete. Adjust slider and Analyze again, or Save.");
                                }
                                
                                if (isFirstFineTuneAnalysis) {
                                    isFirstFineTuneAnalysis = false;
                                }

                                progressBar.setVisibility(View.GONE);
                                analysisControlsContainer.setVisibility(View.VISIBLE);
                                analyzeButton.setEnabled(true);
                                settingsButton.setEnabled(true);
                                btnSave.setVisibility(View.VISIBLE);
                                updateControlsVisibility();
                            }
                        });
                    }
                };

                if (methodToRun == 11) {
                    DeepScanProcessor.processMethod12(getContext(), sourceBitmapForTuning, ksize, listener);
                } else if (methodToRun == 12) {
                    DeepScanProcessor.processMethod13(getContext(), sourceBitmapForTuning, ksize, listener);
                }
            }
        }).start();
    }

    private void performMethod9Analysis() {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "No source image is loaded for tuning.", true);
            return;
        }
        updateStatus("Analyzing...", true);

        final int ksize = sliderKsize.getProgress();

        DeepScanProcessor.ScanListenerWithKsize listener = new DeepScanProcessor.ScanListenerWithKsize() {
            @Override
            public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {
                // Not needed for single pass
            }
            @Override
            public void onScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                updateMainDisplay(finalResult.resultBitmap);

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFirstFineTuneAnalysis) {
                            isFirstFineTuneAnalysis = false;
                        }
                        analysisControlsContainer.setVisibility(View.VISIBLE);
                        btnSave.setVisibility(View.VISIBLE);
                        updateStatus("Ready for fine-tuning. Adjust slider and Analyze.", false);
                        updateProgress(0, 1);
                        analyzeButton.setEnabled(true);
                        settingsButton.setEnabled(true);
                        updateControlsVisibility();
                    }
                });
            }
        };
        DeepScanProcessor.processMethod11(sourceBitmapForTuning, ksize, listener);
    }

    private void beginStandardScan(Bitmap bitmap, final int frameIndex) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        DeepScanProcessor.ScanListener listener = new DeepScanProcessor.ScanListener() {
            @Override
            public void onScanProgress(final int pass, final int totalPasses, final String status, final Bitmap intermediateResult) {
                updateScanStatus(status, pass, totalPasses);
                updateMainDisplay(intermediateResult);
            }
            @Override
            public void onScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                updateMainDisplay(finalResult.resultBitmap);
                
                String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                try {
                    ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save processed frame.", e);
                }

                updateScanStatus("Scan Complete. Found " + finalResult.objectsFound + " objects.", -1, -1);
                latch.countDown();
            }
        };

        switch (selectedMethod) {
            case 3: DeepScanProcessor.processMethod4(bitmap, listener); break;
            case 4: DeepScanProcessor.processMethod5(bitmap, listener); break;
            case 5: DeepScanProcessor.processMethod6(bitmap, listener); break;
            case 6: DeepScanProcessor.processMethod7(bitmap, listener); break;
            case 7: DeepScanProcessor.processMethod8(bitmap, listener); break;
            case 8: DeepScanProcessor.processMethod9(bitmap, listener); break;
            case 9: default: DeepScanProcessor.processMethod10(bitmap, listener); break;
        }

        latch.await();
    }

    private void performFineTuningAnalysis() {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "No source image is loaded for tuning.", true);
            return;
        }
        updateStatus("Analyzing...", true);

        final int depth = sliderDepth.getProgress();
        final int sharpness = sliderSharpness.getProgress();

        int logicalMethod = 0;
        switch(selectedMethod) {
            case 3: logicalMethod = 2; break;
            case 4: logicalMethod = 3; break;
            case 5: logicalMethod = 4; break;
            case 6: logicalMethod = 5; break;
            case 7: logicalMethod = 6; break;
            case 8: logicalMethod = 7; break;
            case 9: logicalMethod = 8; break;
        }
        final int finalLogicalMethod = logicalMethod;

        DeepScanProcessor.ScanListener listener = new DeepScanProcessor.ScanListener() {
            @Override
            public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
            @Override
            public void onScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                updateMainDisplay(finalResult.resultBitmap);
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFirstFineTuneAnalysis) {
                           isFirstFineTuneAnalysis = false;
                        }
                        analysisControlsContainer.setVisibility(View.VISIBLE);
                        btnSave.setVisibility(View.VISIBLE);
                        updateStatus("Ready for fine-tuning. Adjust sliders and Analyze.", false);
                        updateProgress(0, 1);
                        analyzeButton.setEnabled(true);
                        settingsButton.setEnabled(true);
                        updateControlsVisibility();
                    }
                });
            }
        };
        DeepScanProcessor.processWithFineTuning(sourceBitmapForTuning, finalLogicalMethod, depth, sharpness, listener);
    }

    private void saveCurrentImage() {
        if (mainDisplay.getDrawable() == null) {
            Toast.makeText(getContext(), "No image to save.", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmapToSave = ((BitmapDrawable) mainDisplay.getDrawable()).getBitmap();

        if (bitmapToSave != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                        String fileName = "Kop_Saved_" + timestamp + ".png";
                        File outFile = new File(processedFramesDir, fileName);

                        ImageProcessor.saveBitmap(bitmapToSave, outFile.getAbsolutePath());

                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), "Image saved to " + outFile.getParent(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save image", e);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), "Error saving image.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
        }
    }

    private void beginMethod1LiveScan(Bitmap bitmap, final int frameIndex) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        updateMainDisplay(bitmap);
        updateOverlayDisplay(null);

        DeepScanProcessor.LiveScanListener listener = new DeepScanProcessor.LiveScanListener() {
            @Override
            public void onScanProgress(final int pass, final int totalPasses, final String status) {
                updateScanStatus(status, pass, totalPasses);
            }
            @Override
            public void onFoundationReady(Bitmap foundationBitmap) {
                updateOverlayDisplay(foundationBitmap);
            }
            @Override
            public void onLinesReady(Bitmap linesBitmap) {
                updateOverlayDisplay(linesBitmap);
            }
            @Override
            public void onScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                try {
                    ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save processed frame.", e);
                }
                updateScanStatus("Scan Complete. Found " + finalResult.objectsFound + " objects.", -1, -1);
                latch.countDown();
            }
        };
        DeepScanProcessor.processMethod1(bitmap, listener);
        latch.await();

        if (!isVideoFile(inputFilePath)) {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    btnSave.setVisibility(View.VISIBLE);
                    analysisControlsContainer.setVisibility(View.VISIBLE);
                    analyzeButton.setEnabled(true);
                    settingsButton.setEnabled(true);
                }
            });
        }
    }

    private void extractFramesForVideo(int fps) {
        try {
            updateStatus("Extracting " + fps + " FPS...", true);
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                }
            });
            File dir = new File(rawFramesDir);
            if(dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) { for (File file : files) file.delete(); }
            }
            
            // *** THIS IS THE ONLY LINE THAT IS CHANGED IN THIS ENTIRE FILE ***
            // It now passes the required 'Context' to the new ffmpeg-based extractor.
            FrameExtractor.extractFrames(getContext(), inputFilePath, rawFramesDir, fps);
            
            rawFrames = new File(rawFramesDir).listFiles();
            if (rawFrames != null) {
                sortFrames(rawFrames);
                setupFilmStrip(Arrays.asList(rawFrames));
            }
        } catch (Exception e) {
            Log.e(TAG, "Frame extraction failed", e);
            showErrorDialog("Extraction Error", "Could not extract frames from video. Check logs for details.", false);
        }
    }

    private void prepareDirectories() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Project_" + timestamp);
        if (!projectDir.exists()) projectDir.mkdirs();
        rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
        processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
        new File(rawFramesDir).mkdirs();
        new File(processedFramesDir).mkdirs();
    }

    private File[] extractOrCopyFrames(String path) throws Exception {
        copyFile(new File(path), new File(rawFramesDir, "frame_00000.png"));
        return new File(rawFramesDir).listFiles();
    }

    private void sortFrames(File[] frames) {
        Arrays.sort(frames, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareTo(f2.getName());
            }
        });
    }

    private boolean isVideoFile(String path) {
        String lowerCasePath = path.toLowerCase(Locale.US);
        return lowerCasePath.endsWith(".mp4") || lowerCasePath.endsWith(".mov") || lowerCasePath.endsWith(".3gp") || lowerCasePath.endsWith(".mkv");
    }

    private void setupFilmStrip(final List<File> frames) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (frames != null && !frames.isEmpty()) {
                    filmStripAdapter = new FilmStripAdapter(getContext(), frames);
                    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
                    filmStripRecyclerView.setLayoutManager(layoutManager);
                    filmStripRecyclerView.setAdapter(filmStripAdapter);
                    filmStripRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    filmStripRecyclerView.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateCurrentFrameHighlight(final int position) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (filmStripAdapter != null) {
                    filmStripAdapter.setCurrentFrame(position);
                    filmStripRecyclerView.scrollToPosition(position);
                }
            }
        });
    }

    private void updateScanStatus(final String text, final int progress, final int max) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                scanStatusContainer.setVisibility(View.VISIBLE);
                scanStatusTextView.setText(text);
                if (progress > 0 && max > 0) {
                    scanProgressBar.setVisibility(View.VISIBLE);
                    scanProgressBar.setMax(max);
                    scanProgressBar.setProgress(progress);
                } else {
                    scanProgressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void hideScanStatus() {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                scanStatusContainer.setVisibility(View.GONE);
            }
        });
    }

    private void updateStatus(final String text, final boolean isIndeterminate) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                statusTextView.setText(text);
                progressBar.setIndeterminate(isIndeterminate);
            }
        });
    }

    private void updateProgress(final int current, final int max) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setMax(max);
                progressBar.setProgress(current);
            }
        });
    }

    private void updateMainDisplay(final Bitmap bitmap) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (bitmap != null) {
                    mainDisplay.setImageBitmap(bitmap);
                }
            }
        });
    }

    private void updateOverlayDisplay(final Bitmap bitmap) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (bitmap != null) {
                    overlayDisplay.setImageBitmap(bitmap);
                } else {
                    overlayDisplay.setImageDrawable(null);
                }
            }
        });
    }

    private void showSuccessDialog(final String title, final String message) {
        if (!isAdded()) return;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(getContext())
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Don't dismiss the main dialog automatically
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .show();
            }
        });
    }

    private void showErrorDialog(final String title, final String message, final boolean finishActivity) {
        if (!isAdded()) return;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(getContext())
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (finishActivity) {
                                    dismiss();
                                }
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        });
    }

    private Bitmap decodeAndRotateBitmap(String filePath) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) return null;
        ExifInterface exif = new ExifInterface(filePath);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:  matrix.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            default: return bitmap;
        }
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return rotatedBitmap;
    }

    private void copyFile(File source, File dest) throws Exception {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }
}
