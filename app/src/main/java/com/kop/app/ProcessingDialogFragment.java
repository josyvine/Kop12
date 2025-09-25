package com.kop.app; 

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
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
import android.widget.EditText;
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

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private Button analyzeButton, closeButton, btnSave;
    private ImageButton settingsButton;
    private Spinner fpsSpinner, methodSpinner;
    private Handler uiHandler;
    private String inputFilePath;
    private File[] rawFrames;
    private String rawFramesDir, processedFramesDir;
    private int selectedMethod = 0;

    private LinearLayout fineTuningControls;
    private SeekBar sliderDepth, sliderSharpness;
    private Bitmap sourceBitmapForTuning;
    private boolean isFirstFineTuneAnalysis = true;

    private LinearLayout ksizeControlsContainer;
    private SeekBar sliderKsize;

    private LinearLayout scanStatusContainer;
    private TextView scanStatusTextView;
    private ProgressBar scanProgressBar;

    private OnDialogClosedListener closeListener;

    private Switch switchAutomaticScan;
    private LinearLayout imageControlsContainer;
    private SeekBar sliderAnalysisMode;
    private LinearLayout videoControlsContainer;

    private final Handler livePreviewHandler = new Handler(Looper.getMainLooper());
    private Runnable livePreviewRunnable;

    // --- START OF CORRECTED VARIABLES FOR AI FEATURE ---
    private SharedPreferences sharedPreferences;
    private LinearLayout aiControlsContainer;
    private EditText etGeminiApiKey;
    private Button btnSaveApiKey;
    private ImageButton btnUpdateApiKey;
    private Switch switchEnableAi;
    private Bitmap goldStandardBitmap = null;
    // --- END OF CORRECTED VARIABLES FOR AI FEATURE ---

    // --- START OF NEW VARIABLES FOR METHOD 14 ---
    private Spinner styleSpinner;
    private LinearLayout aiStyleControlsContainer;
    // This array MUST match the order and contents of the 'ai_sketch_style_options' in arrays.xml
    private final String[] styleAssetFiles = {
            "style_charcoal.jpg",
            "style_graphite_soft.jpg",
            "style_crosshatch.jpg",
            "style_technical_pen.jpg",
            "style_pencil_sketch.jpg"
    };
    // --- END OF NEW VARIABLES FOR METHOD 14 ---


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

        // --- START OF NEW AI SETUP CALLS ---
        // Initialize SharedPreferences for storing the API key
        sharedPreferences = getContext().getSharedPreferences("KopAppSettings", Context.MODE_PRIVATE);
        // Set up the listeners and initial state for the AI UI components
        setupAiControls();
        // --- END OF NEW AI SETUP CALLS ---

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
        cleanupRawFiles();
        // --- START OF NEW AI CLEANUP ---
        // Ensure the gold standard bitmap is recycled if the dialog is closed mid-process
        if (goldStandardBitmap != null && !goldStandardBitmap.isRecycled()) {
            goldStandardBitmap.recycle();
            goldStandardBitmap = null;
        }
        // --- END OF NEW AI CLEANUP ---
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

        ksizeControlsContainer = view.findViewById(R.id.ksize_controls_container);
        sliderKsize = view.findViewById(R.id.slider_ksize);

        switchAutomaticScan = view.findViewById(R.id.switch_automatic_scan);
        imageControlsContainer = view.findViewById(R.id.image_controls_container);
        sliderAnalysisMode = view.findViewById(R.id.slider_analysis_mode);
        videoControlsContainer = view.findViewById(R.id.video_controls_container);

        // --- START OF CORRECTED View-Finding for AI controls ---
        aiControlsContainer = view.findViewById(R.id.ai_controls_container);
        etGeminiApiKey = view.findViewById(R.id.et_gemini_api_key);
        btnSaveApiKey = view.findViewById(R.id.btn_save_api_key);
        btnUpdateApiKey = view.findViewById(R.id.btn_update_api_key);
        switchEnableAi = view.findViewById(R.id.switch_enable_ai);
        // --- END OF CORRECTED View-Finding for AI controls ---

        // --- START OF NEW View-Finding FOR METHOD 14 ---
        styleSpinner = view.findViewById(R.id.spinner_ai_style);
        aiStyleControlsContainer = view.findViewById(R.id.ai_style_controls_container);
        // --- END OF NEW View-Finding FOR METHOD 14 ---

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
            imageControlsContainer.setVisibility(View.GONE);
            videoControlsContainer.setVisibility(View.VISIBLE);
        } else {
            settingsButton.setVisibility(View.GONE);
            analysisControlsContainer.setVisibility(View.VISIBLE);
            imageControlsContainer.setVisibility(View.VISIBLE);
            videoControlsContainer.setVisibility(View.GONE);
            // Hide AI controls for single images
            aiControlsContainer.setVisibility(View.GONE);
        }

        fineTuningControls.setVisibility(View.GONE);

        ArrayAdapter<CharSequence> methodAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.method_options, android.R.layout.simple_spinner_item);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        methodSpinner.setAdapter(methodAdapter);
        methodSpinner.setSelection(selectedMethod, false);

        // --- START OF NEW LOGIC FOR METHOD 14 ---
        ArrayAdapter<CharSequence> styleAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.ai_sketch_style_options, android.R.layout.simple_spinner_item);
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        styleSpinner.setAdapter(styleAdapter);
        // --- END OF NEW LOGIC FOR METHOD 14 ---


        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMethod = position;

                // --- START OF NEW LOGIC TO SHOW/HIDE AI CONTROLS ---
                // Only show the AI controls container if a video is loaded AND method 11 or 12 is selected.
                if (isVideo && (selectedMethod == 11 || selectedMethod == 12)) {
                    aiControlsContainer.setVisibility(View.VISIBLE);
                } else {
                    aiControlsContainer.setVisibility(View.GONE);
                    // Safety: always turn off AI if user switches away from a compatible method.
                    if (switchEnableAi != null) {
                        switchEnableAi.setChecked(false);
                    }
                }
                // --- END OF NEW LOGIC TO SHOW/HIDE AI CONTROLS ---

                // --- START OF NEW LOGIC TO SHOW/HIDE STYLE SPINNER FOR METHOD 14 ---
                // Method 14 is at index 13
                if (selectedMethod == 13) {
                    aiStyleControlsContainer.setVisibility(View.VISIBLE);
                } else {
                    aiStyleControlsContainer.setVisibility(View.GONE);
                }
                // --- END OF NEW LOGIC TO SHOW/HIDE STYLE SPINNER FOR METHOD 14 ---

                if (isVideo) {
                    updateUiForVideoMode(sliderAnalysisMode.getProgress());
                } else {
                    updateUiForImageMode(switchAutomaticScan.isChecked());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMethod = 0;
                // --- START OF NEW LOGIC TO SHOW/HIDE AI CONTROLS (for safety) ---
                aiControlsContainer.setVisibility(View.GONE);
                if (switchEnableAi != null) {
                    switchEnableAi.setChecked(false);
                }
                // --- END OF NEW LOGIC TO SHOW/HIDE AI CONTROLS (for safety) ---

                // --- START OF NEW LOGIC FOR METHOD 14 (for safety) ---
                aiStyleControlsContainer.setVisibility(View.GONE);
                // --- END OF NEW LOGIC FOR METHOD 14 (for safety) ---

                if (isVideo) {
                    updateUiForVideoMode(sliderAnalysisMode.getProgress());
                } else {
                    updateUiForImageMode(switchAutomaticScan.isChecked());
                }
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
                    final int selectedFps = Integer.parseInt(selectedFpsStr.replace(" FPS", ""));

                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setUiEnabled(false);
                            if (filmStripAdapter != null) {
                                filmStripAdapter.updateData(new ArrayList<File>());
                            }
                            statusTextView.setText("Extracting " + selectedFps + " FPS...");
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setIndeterminate(true);
                        }
                    });

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
                updateUiForImageMode(isChecked);
            }
        });

        sliderAnalysisMode.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    updateUiForVideoMode(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SeekBar.OnSeekBarChangeListener livePreviewListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                boolean isVideoLivePreview = isVideoFile(inputFilePath) && sliderAnalysisMode.getProgress() == 1;
                boolean isImageManualTune = !isVideoFile(inputFilePath) && !switchAutomaticScan.isChecked();

                if (fromUser && (isVideoLivePreview || isImageManualTune)) {
                    livePreviewHandler.removeCallbacks(livePreviewRunnable);
                    livePreviewRunnable = new Runnable() {
                        @Override
                        public void run() {
                            performLivePreviewAnalysis();
                        }
                    };
                    livePreviewHandler.postDelayed(livePreviewRunnable, 200);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        sliderKsize.setOnSeekBarChangeListener(livePreviewListener);
        sliderDepth.setOnSeekBarChangeListener(livePreviewListener);
        sliderSharpness.setOnSeekBarChangeListener(livePreviewListener);

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

        if (isVideo) {
            updateUiForVideoMode(0);
        } else {
            updateUiForImageMode(true);
        }
    }

    private void setUiEnabled(boolean isEnabled) {
        analyzeButton.setEnabled(isEnabled);
        settingsButton.setEnabled(isEnabled);
        methodSpinner.setEnabled(isEnabled);
        fpsSpinner.setEnabled(isEnabled);
        sliderAnalysisMode.setEnabled(isEnabled);
        switchAutomaticScan.setEnabled(isEnabled);
    }

    private void updateUiForImageMode(boolean isAutomatic) {
        boolean needsDepthSharpness = selectedMethod >= 3 && selectedMethod <= 9;
        boolean needsKsize = selectedMethod >= 10 && selectedMethod <= 12;

        if (isAutomatic) {
            fineTuningControls.setVisibility(View.GONE);
        } else { // Manual Tuning Mode
            if (needsDepthSharpness) {
                fineTuningControls.setVisibility(View.VISIBLE);
                ksizeControlsContainer.setVisibility(View.GONE);
            } else if (needsKsize) {
                fineTuningControls.setVisibility(View.VISIBLE);
                ksizeControlsContainer.setVisibility(View.VISIBLE);
            } else {
                fineTuningControls.setVisibility(View.GONE);
            }
        }
    }

    private void updateUiForVideoMode(int mode) {
        boolean needsDepthSharpness = selectedMethod >= 3 && selectedMethod <= 9;
        boolean needsKsize = selectedMethod >= 10 && selectedMethod <= 12;

        switch(mode) {
            case 0: // Standard Auto
                fineTuningControls.setVisibility(View.GONE);
                ksizeControlsContainer.setVisibility(View.GONE);
                analyzeButton.setText("Analyze");
                break;
            case 1: // Live Preview & Tune
                if (needsDepthSharpness) {
                    fineTuningControls.setVisibility(View.VISIBLE);
                    ksizeControlsContainer.setVisibility(View.GONE);
                } else if (needsKsize) {
                    fineTuningControls.setVisibility(View.VISIBLE);
                    ksizeControlsContainer.setVisibility(View.VISIBLE);
                } else {
                    fineTuningControls.setVisibility(View.GONE);
                    ksizeControlsContainer.setVisibility(View.GONE);
                }
                analyzeButton.setText("Analyze Preview");
                performLivePreviewAnalysis(); // Show initial preview
                break;
            case 2: // Apply Tuned Settings to Video
                if (needsDepthSharpness) {
                    fineTuningControls.setVisibility(View.VISIBLE);
                    ksizeControlsContainer.setVisibility(View.GONE);
                } else if (needsKsize) {
                    fineTuningControls.setVisibility(View.VISIBLE);
                    ksizeControlsContainer.setVisibility(View.VISIBLE);
                } else {
                    fineTuningControls.setVisibility(View.GONE);
                    ksizeControlsContainer.setVisibility(View.GONE);
                }
                analyzeButton.setText("Apply to Video");
                break;
        }
    }

    private void performLivePreviewAnalysis() {
        if (sourceBitmapForTuning == null) {
            return; // Not ready yet
        }

        if (selectedMethod <= 1) {
            beginAutomaticAiScan(selectedMethod);
        } else if (selectedMethod == 2) {
            try {
                beginMethod1LiveScan(sourceBitmapForTuning, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (selectedMethod >= 10 && selectedMethod <= 12) {
            performNewAiAnalysis();
        } else {
            performFineTuningAnalysis();
        }
    }

    private void beginAnalysis() {
        if (switchEnableAi.isChecked() && (selectedMethod == 11 || selectedMethod == 12)) {
            String apiKey = sharedPreferences.getString("GEMINI_API_KEY", "");
            if (apiKey.isEmpty()) {
                showErrorDialog("API Key Missing", "AI Assist is enabled, but the Gemini API Key has not been set. Please add your key in the settings panel.", true);
                setUiEnabled(true);
                return;
            }
        }

        livePreviewHandler.removeCallbacksAndMessages(null);

        setUiEnabled(false);
        analysisControlsContainer.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);

        if (rawFrames == null || rawFrames.length == 0) {
            showErrorDialog("Processing Error", "No frames available to process.", true);
            setUiEnabled(true);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isVideoFile(inputFilePath)) {
                        int mode = sliderAnalysisMode.getProgress();
                        int ksize = sliderKsize.getProgress();
                        int depth = sliderDepth.getProgress();
                        int sharpness = sliderSharpness.getProgress();

                        if (mode == 1) {
                            processSingleFrameManually();
                        } else {
                            processAllFrames(ksize, depth, sharpness);
                        }
                    } else { // It's a single image
                        if (switchAutomaticScan.isChecked()) {
                            processAllFrames(10, 2, 50);
                        } else {
                            processSingleFrameManually();
                        }
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Analysis failed", e);
                    String message = (e.getMessage() != null) ? e.getMessage() : "An unknown error occurred.";
                    showErrorDialog("Processing Error", message, true);
                }
            }
        }).start();
    }

    private void processSingleFrameManually() {
        if (isFirstFineTuneAnalysis) {
            try {
                if (rawFrames != null && rawFrames.length > 0) {
                    sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames[0].getAbsolutePath());
                    isFirstFineTuneAnalysis = false;
                } else {
                    showErrorDialog("Error", "Source frame missing.", true);
                    return;
                }
            } catch (IOException e) {
                showErrorDialog("Error", "Could not load source image for tuning.", true);
                return;
            }
        }
        performLivePreviewAnalysis();
    }

    // --- START OF REFACTORED AI WORKFLOW ---
    private void processAllFrames(final int ksize, final int depth, final int sharpness) throws Exception {
        final int totalFrames = rawFrames.length;
        // The ksize can now change frame-by-frame, so we need a mutable variable.
        int currentKsize = ksize;

        if (goldStandardBitmap != null && !goldStandardBitmap.isRecycled()) {
            goldStandardBitmap.recycle();
        }
        goldStandardBitmap = null; // Reset before each full run

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
            }
        });

        // --- START: NEW LOGIC BLOCK FOR METHOD 14 ---
        if (selectedMethod == 13) {
            // This entire block is dedicated to Method 14 processing
            updateStatus("Preparing AI Style Transfer...", true);

            // 1. Check for GPU support
            CompatibilityList compatList = new CompatibilityList();
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            if (!compatList.isDelegateSupportedOnThisDevice()){
                showErrorDialog("GPU Not Supported", "This device does not support hardware acceleration required for this feature.", true);
                return;
            }
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            Interpreter.Options interpreterOptions = new Interpreter.Options().addDelegate(gpuDelegate);

            Interpreter predictionInterpreter = null;
            Interpreter transferInterpreter = null;

            try {
                // 2. Get user's style choice
                int selectedStyleIndex = styleSpinner.getSelectedItemPosition();
                String styleAssetName = styleAssetFiles[selectedStyleIndex];
                Bitmap styleBitmap = loadBitmapFromAssets(styleAssetName);

                // 3. Run Style Prediction (once, before the loop)
                updateStatus("Analyzing Art Style...", true);
                MappedByteBuffer predictionModel = loadModelFile("magenta_prediction.tflite");
                predictionInterpreter = new Interpreter(predictionModel, interpreterOptions);
                float[] styleVector = DeepScanProcessor.runStylePrediction(styleBitmap, predictionInterpreter);
                styleBitmap.recycle(); // No longer need the style bitmap

                // 4. Prepare Style Transfer
                MappedByteBuffer transferModel = loadModelFile("magenta_transfer.tflite");
                transferInterpreter = new Interpreter(transferModel, interpreterOptions);

                // 5. Loop through frames and apply the style
                for (int i = 0; i < totalFrames; i++) {
                    final int frameNum = i + 1;
                    final int frameIndex = i;
                    updateStatus("Applying style to frame " + frameNum + " of " + totalFrames, false);
                    updateProgress(frameNum, totalFrames);
                    updateCurrentFrameHighlight(frameIndex);

                    Bitmap contentBitmap = decodeAndRotateBitmap(rawFrames[frameIndex].getAbsolutePath());
                    if (contentBitmap == null) continue;

                    Bitmap stylizedBitmap = DeepScanProcessor.runStyleTransfer(contentBitmap, styleVector, transferInterpreter);

                    updateMainDisplay(stylizedBitmap);
                    saveProcessedFrame(stylizedBitmap, frameIndex);

                    contentBitmap.recycle();
                    stylizedBitmap.recycle();
                }
            } finally {
                // 6. Cleanup interpreters
                if (predictionInterpreter != null) {
                    predictionInterpreter.close();
                }
                if (transferInterpreter != null) {
                    transferInterpreter.close();
                }
                gpuDelegate.close();
            }

        // --- END: NEW LOGIC BLOCK FOR METHOD 14 ---
        } else {
            // --- EXISTING LOGIC FOR ALL OTHER METHODS ---
            try {
                for (int i = 0; i < totalFrames; i++) {
                    final int frameNum = i + 1;
                    final int frameIndex = i;

                    updateStatus("Processing frame " + frameNum + " of " + totalFrames, false);
                    updateProgress(frameNum, totalFrames);
                    updateCurrentFrameHighlight(frameIndex);

                    Bitmap orientedBitmap = decodeAndRotateBitmap(rawFrames[frameIndex].getAbsolutePath());
                    if (orientedBitmap == null) continue;

                    // --- NEW AI-ENABLED WORKFLOW FOR VIDEOS ---
                    if (isVideoFile(inputFilePath) && switchEnableAi.isChecked() && (selectedMethod == 11 || selectedMethod == 12)) {
                        if (frameIndex == 0) {
                            // ** FRAME 0: ESTABLISH THE GOLD STANDARD **
                            updateStatus("Processing frame 1 to set Gold Standard...", false);
                            // Process the first frame using the user's chosen settings without guidance.
                            Bitmap processedFirstFrame = getAiPencilScanAsBitmap(orientedBitmap, currentKsize);
                            if (processedFirstFrame != null) {
                                // Store this result as the reference for all other frames.
                                goldStandardBitmap = processedFirstFrame; // This is a new bitmap, so no need to copy
                                saveProcessedFrame(goldStandardBitmap, frameIndex); // Save the first frame
                            } else {
                                throw new Exception("Failed to process the first frame to create a Gold Standard.");
                            }
                        } else {
                            // ** SUBSEQUENT FRAMES: USE AI ASSIST **
                            // First, ask AI if ksize needs adjustment for consistency.
                            CorrectedKsize correction = GeminiAiHelper.checkFrameConsistency(
                                    sharedPreferences.getString("GEMINI_API_KEY", ""),
                                    goldStandardBitmap,
                                    orientedBitmap,
                                    currentKsize
                            );
                            currentKsize = correction.ksize; // Update ksize for this frame
                            if (correction.wasCorrected) {
                                final int correctedKsize = currentKsize; // final for toast
                                uiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(), "AI adjusted ksize to " + correctedKsize, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            // Now, run the full AI-guided scan with the (potentially new) ksize.
                            beginBlockingAiGuidedScan(orientedBitmap, frameIndex, currentKsize);
                        }
                    } else {
                        // --- ORIGINAL, NON-AI PROCESSING PATH (UNCHANGED) ---
                        if (selectedMethod <= 1) {
                            beginBlockingAiScan(orientedBitmap, frameIndex);
                        } else if (selectedMethod >= 10 && selectedMethod <= 12) {
                            beginBlockingPencilOrNewAiScan(orientedBitmap, frameIndex, currentKsize);
                        } else if (selectedMethod == 2) {
                            beginMethod1LiveScan(orientedBitmap, frameIndex);
                        } else {
                            boolean isVideoStandardAuto = isVideoFile(inputFilePath) && sliderAnalysisMode.getProgress() == 0;
                            boolean isImageAutoScan = !isVideoFile(inputFilePath) && switchAutomaticScan.isChecked();
                            if (isVideoStandardAuto || isImageAutoScan) {
                                beginStandardScan(orientedBitmap, frameIndex);
                            } else {
                                beginTunedScan(orientedBitmap, frameIndex, depth, sharpness);
                            }
                        }
                    }
                    orientedBitmap.recycle();
                }
            } finally {
                if (goldStandardBitmap != null && !goldStandardBitmap.isRecycled()) {
                    goldStandardBitmap.recycle();
                    goldStandardBitmap = null;
                }
            }
        }
        // --- END OF REFACTORED AI WORKFLOW ---

        cleanupRawFiles();

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVideoFile(inputFilePath)) {
                    showSuccessDialog("Processing Complete", "Your frames have been saved to:\n\n" + processedFramesDir);
                    statusTextView.setText("Processing Complete.");
                } else {
                    statusTextView.setText("Automatic scan complete.");
                    btnSave.setVisibility(View.VISIBLE);
                }
                progressBar.setVisibility(View.GONE);
                setUiEnabled(true);
                analysisControlsContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void beginAutomaticAiScan(final int methodIndex) {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "Source image not found for AI scan.", true);
            return;
        }
        updateStatus("Performing AI Analysis...", true);

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
                                    showErrorDialog("AI Analysis Failed", "The AI model could not process the image.", false);
                                    statusTextView.setText("AI Analysis Failed. Ready to try again.");
                                } else {
                                    updateMainDisplay(finalResult.resultBitmap);
                                    statusTextView.setText("AI Analysis Complete. Save or choose another method.");
                                    btnSave.setVisibility(View.VISIBLE);
                                }
                                progressBar.setVisibility(View.GONE);
                                setUiEnabled(true);
                                analysisControlsContainer.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                };
                if (methodIndex == 1) {
                    DeepScanProcessor.processMethod0(getContext(), sourceBitmapForTuning, listener);
                } else {
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

        if (selectedMethod == 1) {
            DeepScanProcessor.processMethod0(getContext(), bitmap, listener);
        } else {
            DeepScanProcessor.processMethod01(getContext(), bitmap, listener);
        }
        latch.await();
    }

    private void beginBlockingPencilOrNewAiScan(Bitmap bitmap, final int frameIndex, final int ksize) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
            @Override
            public void onAiScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                if (finalResult != null && finalResult.resultBitmap != null) {
                    saveProcessedFrame(finalResult.resultBitmap, frameIndex);
                }
                latch.countDown();
            }
        };

        if (selectedMethod == 10) {
            DeepScanProcessor.processMethod11(bitmap, ksize, new DeepScanProcessor.ScanListenerWithKsize(){
                @Override
                public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
                @Override
                public void onScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                    listener.onAiScanComplete(finalResult);
                }
            });
        } else if (selectedMethod == 11) {
            DeepScanProcessor.processMethod12(getContext(), bitmap, ksize, listener);
        } else if (selectedMethod == 12) {
            DeepScanProcessor.processMethod13(getContext(), bitmap, ksize, listener);
        }
        latch.await();
    }

    private void performNewAiAnalysis() {
        if (sourceBitmapForTuning == null) {
            return;
        }
        updateStatus("Performing AI Analysis...", true);

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
                                    showErrorDialog("AI Analysis Failed", "The AI model could not process the image.", false);
                                    statusTextView.setText("AI Analysis Failed. Ready to try again.");
                                } else {
                                    updateMainDisplay(finalResult.resultBitmap);
                                    statusTextView.setText("AI Analysis Complete. Adjust slider and Analyze again, or Save.");
                                }
                                progressBar.setVisibility(View.GONE);
                                setUiEnabled(true);
                                analysisControlsContainer.setVisibility(View.VISIBLE);
                                btnSave.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                };

                if (selectedMethod == 10) {
                    DeepScanProcessor.processMethod11(sourceBitmapForTuning, ksize, new DeepScanProcessor.ScanListenerWithKsize() {
                        @Override
                        public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
                        @Override
                        public void onScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                            listener.onAiScanComplete(finalResult);
                        }
                    });
                } else if (selectedMethod == 11) {
                    DeepScanProcessor.processMethod12(getContext(), sourceBitmapForTuning, ksize, listener);
                } else if (selectedMethod == 12) {
                    DeepScanProcessor.processMethod13(getContext(), sourceBitmapForTuning, ksize, listener);
                }
            }
        }).start();
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
            case 9:
            default: DeepScanProcessor.processMethod10(bitmap, listener); break;
        }

        latch.await();
    }

    private void performFineTuningAnalysis() {
        if (sourceBitmapForTuning == null) {
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
                        setUiEnabled(true);
                    }
                });
            }
        };
        DeepScanProcessor.processWithFineTuning(sourceBitmapForTuning, finalLogicalMethod, depth, sharpness, listener);
    }

    private void beginTunedScan(Bitmap bitmap, final int frameIndex, int depth, int sharpness) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
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
            public void onScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                if (finalResult != null && finalResult.resultBitmap != null) {
                    updateMainDisplay(finalResult.resultBitmap);
                    String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                    try {
                        ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save processed tuned frame.", e);
                    }
                }
                latch.countDown();
            }
        };
        DeepScanProcessor.processWithFineTuning(bitmap, finalLogicalMethod, depth, sharpness, listener);
        latch.await();
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
                        cleanupRawFiles();

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
                    setUiEnabled(true);
                }
            });
        }
    }

    private void extractFramesForVideo(int fps) {
        try {
            File dir = new File(rawFramesDir);
            if (dir.exists()) {
                deleteRecursive(dir);
            }
            dir.mkdirs();

            FrameExtractor.extractFrames(getContext(), inputFilePath, rawFramesDir, fps);
            rawFrames = new File(rawFramesDir).listFiles();

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (rawFrames != null && rawFrames.length > 0) {
                        sortFrames(rawFrames);
                        setupFilmStrip(Arrays.asList(rawFrames));
                        statusTextView.setText("Ready to analyze.");
                    } else {
                        setupFilmStrip(new ArrayList<File>());
                        statusTextView.setText("Extraction failed: No frames found.");
                    }
                    setUiEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    progressBar.setIndeterminate(false);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Frame extraction failed", e);
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    statusTextView.setText("Frame extraction failed.");
                    setUiEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    progressBar.setIndeterminate(false);
                    showErrorDialog("Extraction Error", "Could not extract frames from video. Check logs for details.", false);
                }
            });
        }
    }

    private void prepareDirectories() {
        boolean isVideo = isVideoFile(inputFilePath);

        if (isVideo) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Project_" + timestamp);
            if (!projectDir.exists()) {
                projectDir.mkdirs();
            }
            rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
            processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
            new File(rawFramesDir).mkdirs();
            new File(processedFramesDir).mkdirs();
        } else {
            File permanentOutputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Processed_Images");
            if (!permanentOutputDir.exists()) {
                permanentOutputDir.mkdirs();
            }
            processedFramesDir = permanentOutputDir.getAbsolutePath();

            File tempDir = new File(getContext().getCacheDir(), "temp_image_processing");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            rawFramesDir = tempDir.getAbsolutePath();
        }
    }

    private File[] extractOrCopyFrames(String path) throws Exception {
        File destFile = new File(rawFramesDir, "raw_frame.png");
        copyFile(new File(path), destFile);
        return new File[]{destFile};
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
                    if (filmStripAdapter == null) {
                        filmStripAdapter = new FilmStripAdapter(getContext(), frames);
                        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
                        filmStripRecyclerView.setLayoutManager(layoutManager);
                        filmStripRecyclerView.setAdapter(filmStripAdapter);
                    } else {
                        filmStripAdapter.updateData(frames);
                    }
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
                        .setPositiveButton("OK", null)
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

    private void cleanupRawFiles() {
        if (rawFramesDir != null && !rawFramesDir.isEmpty()) {
            File dir = new File(rawFramesDir);
            if (dir.exists()) {
                deleteRecursive(dir);
            }
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width =  ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    // --- START OF NEW/UPDATED METHODS FOR AI FEATURE ---

    private void setupAiControls() {
        btnSaveApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String apiKey = etGeminiApiKey.getText().toString().trim();
                if (!apiKey.isEmpty()) {
                    sharedPreferences.edit().putString("GEMINI_API_KEY", apiKey).apply();
                    Toast.makeText(getContext(), "API Key Saved", Toast.LENGTH_SHORT).show();
                    updateAiUiState();
                } else {
                    Toast.makeText(getContext(), "API Key cannot be empty", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnUpdateApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etGeminiApiKey.setVisibility(View.VISIBLE);
                btnSaveApiKey.setVisibility(View.VISIBLE);
                btnUpdateApiKey.setVisibility(View.GONE);
            }
        });

        updateAiUiState();
    }

    private void updateAiUiState() {
        String savedApiKey = sharedPreferences.getString("GEMINI_API_KEY", null);
        if (savedApiKey != null && !savedApiKey.isEmpty()) {
            etGeminiApiKey.setVisibility(View.GONE);
            btnSaveApiKey.setVisibility(View.GONE);
            btnUpdateApiKey.setVisibility(View.VISIBLE);
            switchEnableAi.setEnabled(true);
        } else {
            etGeminiApiKey.setVisibility(View.VISIBLE);
            btnSaveApiKey.setVisibility(View.VISIBLE);
            btnUpdateApiKey.setVisibility(View.GONE);
            switchEnableAi.setEnabled(false);
            switchEnableAi.setChecked(false);
        }
    }

    private Bitmap getAiPencilScanAsBitmap(Bitmap bitmap, int ksize) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] resultHolder = { null };

        DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
            @Override
            public void onAiScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                if (finalResult != null) {
                    resultHolder[0] = finalResult.resultBitmap;
                }
                latch.countDown();
            }
        };

        if (selectedMethod == 11) {
            DeepScanProcessor.processMethod12(getContext(), bitmap, ksize, listener);
        } else if (selectedMethod == 12) {
            DeepScanProcessor.processMethod13(getContext(), bitmap, ksize, listener);
        } else {
            latch.countDown();
        }

        latch.await();
        return resultHolder[0];
    }

    private void saveProcessedFrame(Bitmap bitmap, int frameIndex) {
        if (bitmap != null) {
            updateMainDisplay(bitmap);
            String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
            try {
                ImageProcessor.saveBitmap(bitmap, outPath);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save AI processed frame.", e);
            }
            if (bitmap != goldStandardBitmap) {
                // Don't recycle the gold standard; it's needed for the whole loop.
                // Other bitmaps are temporary for the current frame and can be recycled.
                if(!bitmap.isRecycled()){
                    bitmap.recycle();
                }
            }
        }
    }

    private void beginBlockingAiGuidedScan(Bitmap bitmap, final int frameIndex, final int ksize) throws InterruptedException {
        try {
            updateStatus("Frame " + (frameIndex + 1) + ": AI analyzing subject...", false);
            String apiKey = sharedPreferences.getString("GEMINI_API_KEY", "");
            FrameAnalysisResult analysisResult = GeminiAiHelper.findObjectRegions(apiKey, bitmap);
            updateStatus("Processing frame " + (frameIndex + 1) + " of " + rawFrames.length, false);

            if (!analysisResult.hasObjects()) {
                Log.w(TAG, "AI found no objects for guided scan on frame " + frameIndex + ". Falling back to standard scan.");
                beginBlockingPencilOrNewAiScan(bitmap, frameIndex, ksize);
                return;
            }

            // Use the new, intelligent mask creation method.
            Bitmap aiMask = ImageProcessor.createUnifiedMaskFromRects(
                bitmap.getWidth(),
                bitmap.getHeight(),
                analysisResult.getObjectBounds()
            );

            // Perform the scan, which returns a clean bitmap.
            Bitmap finalProcessedBitmap = performAiGuidedScan(bitmap, ksize, aiMask);

            // Save and display the clean bitmap.
            saveProcessedFrame(finalProcessedBitmap, frameIndex);

            // Clean up the temporary unified mask.
            if (aiMask != null && !aiMask.isRecycled()) {
                aiMask.recycle();
            }

        } catch (Exception e) {
            Log.e(TAG, "AI-guided scan failed due to an exception.", e);
            String message = (e instanceof IOException) ? e.getMessage() : "An unexpected error occurred.";
            showErrorDialog("AI Assist Error", "The AI analysis failed for frame " + (frameIndex + 1) + ". " + message + "\n\nFalling back to standard processing for this frame.", false);
            beginBlockingPencilOrNewAiScan(bitmap, frameIndex, ksize);
        }
    }

    private Bitmap performAiGuidedScan(Bitmap originalBitmap, int ksize, Bitmap aiMask) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bitmap[] resultHolder = { null };

        DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
            @Override
            public void onAiScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                if (finalResult != null && finalResult.resultBitmap != null) {
                    resultHolder[0] = finalResult.resultBitmap;

                    final String toastMessage = String.format(Locale.US,
                        "AI Assist: Enhanced Frame. Problem: %s. Fix: %s.",
                        finalResult.problemDetected,
                        finalResult.fixApplied
                    );
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), toastMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    Log.e(TAG, "AI-guided scan returned a null result bitmap.");
                }
                latch.countDown();
            }
        };

        if (selectedMethod == 11) {
            DeepScanProcessor.processMethod12(getContext(), originalBitmap, ksize, aiMask, listener);
        } else if (selectedMethod == 12) {
            DeepScanProcessor.processMethod13(getContext(), originalBitmap, ksize, aiMask, listener);
        } else {
            latch.countDown();
        }

        latch.await();
        return resultHolder[0];
    }
    // --- END OF NEW/UPDATED METHODS ---

    // --- START: NEW HELPER METHODS FOR METHOD 14 ---

    /**
     * Loads a TFLite model file from the assets folder.
     * @param modelFileName The name of the model file in the assets folder.
     * @return A MappedByteBuffer containing the model data.
     * @throws IOException if the model file cannot be found or read.
     */
    private MappedByteBuffer loadModelFile(String modelFileName) throws IOException {
        AssetFileDescriptor fileDescriptor = getContext().getAssets().openFd(modelFileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Loads a Bitmap from the assets folder.
     * @param fileName The name of the image file in the assets folder.
     * @return A Bitmap object.
     * @throws IOException if the file cannot be found or read.
     */
    private Bitmap loadBitmapFromAssets(String fileName) throws IOException {
        AssetManager assetManager = getContext().getAssets();
        InputStream inputStream = assetManager.open(fileName);
        return BitmapFactory.decodeStream(inputStream);
    }
    // --- END: NEW HELPER METHODS FOR METHOD 14 ---
}
