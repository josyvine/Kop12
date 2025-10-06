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
    // NEW: Argument for multiple file paths
    private static final String ARG_FILE_PATHS = "file_paths";

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

    // UPDATED: Input can be single or multiple paths
    private String inputFilePath;
    private ArrayList<String> inputFilePaths;
    private List<File> rawFrames; // This will hold all frames/images to be processed
    private String rawFramesDir, processedFramesDir;
    private int selectedMethod = 0;

    // NEW: State flags
    private boolean isMultiImageMode = false;
    private boolean isZipMode = false;
    private boolean isReEditing = false;
    private int reEditFrameIndex = -1;

    private LinearLayout fineTuningControls;
    private SeekBar sliderDepth, sliderSharpness;
    private TextView tvDepthLabel, tvSharpnessLabel; // FIX: Member variables for labels
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

    private SharedPreferences sharedPreferences;
    private LinearLayout aiControlsContainer;
    private EditText etGeminiApiKey;
    private Button btnSaveApiKey;
    private ImageButton btnUpdateApiKey;
    private Switch switchEnableAi;
    private Bitmap goldStandardBitmap = null;

    private Spinner styleSpinner;
    private LinearLayout aiStyleControlsContainer;
    private final String[] styleAssetFiles = {
            "style_charcoal.jpg",
            "style_graphite_soft.jpg",
            "style_crosshatch.jpg",
            "style_technical_pen.jpg",
            "style_pencil_sketch.jpg"
    };

    // NEW: Controls for Method 9 adjustments
    private LinearLayout adjustmentControlsContainer;
    private SeekBar sliderAdjustment;
    private TextView tvAdjustmentLabel;
    private View adjBrightness, adjContrast, adjSaturation, adjTemperature;
    private int currentAdjustmentMode = 0; // 0: Brightness, 1: Contrast, 2: Saturation, 3: Temperature
    private int brightnessValue = 25, contrastValue = 25, saturationValue = 25, temperatureValue = 25;


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

    // NEW: Overloaded constructor for multiple image files
    public static ProcessingDialogFragment newInstance(ArrayList<String> filePaths) {
        ProcessingDialogFragment fragment = new ProcessingDialogFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_FILE_PATHS, filePaths);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            inputFilePaths = getArguments().getStringArrayList(ARG_FILE_PATHS);
            inputFilePath = getArguments().getString(ARG_FILE_PATH, "");

            if (inputFilePaths != null && !inputFilePaths.isEmpty()) {
                isMultiImageMode = true;
            } else if (inputFilePath.toLowerCase(Locale.US).endsWith(".zip")) {
                isZipMode = true;
            }
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

        sharedPreferences = getContext().getSharedPreferences("KopAppSettings", Context.MODE_PRIVATE);
        setupAiControls();
        setupAdjustmentControls();

        if ((inputFilePath != null && !inputFilePath.isEmpty()) || isMultiImageMode) {
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
        cleanupTempFiles();
        if (goldStandardBitmap != null && !goldStandardBitmap.isRecycled()) {
            goldStandardBitmap.recycle();
            goldStandardBitmap = null;
        }
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
        tvDepthLabel = view.findViewById(R.id.tv_depth_label); // FIX: Initialize label
        tvSharpnessLabel = view.findViewById(R.id.tv_sharpness_label); // FIX: Initialize label
        btnSave = view.findViewById(R.id.btn_save);
        ksizeControlsContainer = view.findViewById(R.id.ksize_controls_container);
        sliderKsize = view.findViewById(R.id.slider_ksize);
        switchAutomaticScan = view.findViewById(R.id.switch_automatic_scan);
        imageControlsContainer = view.findViewById(R.id.image_controls_container);
        sliderAnalysisMode = view.findViewById(R.id.slider_analysis_mode);
        videoControlsContainer = view.findViewById(R.id.video_controls_container);
        aiControlsContainer = view.findViewById(R.id.ai_controls_container);
        etGeminiApiKey = view.findViewById(R.id.et_gemini_api_key);
        btnSaveApiKey = view.findViewById(R.id.btn_save_api_key);
        btnUpdateApiKey = view.findViewById(R.id.btn_update_api_key);
        switchEnableAi = view.findViewById(R.id.switch_enable_ai);
        styleSpinner = view.findViewById(R.id.spinner_ai_style);
        aiStyleControlsContainer = view.findViewById(R.id.ai_style_controls_container);

        adjustmentControlsContainer = view.findViewById(R.id.adjustment_controls_container);
        sliderAdjustment = view.findViewById(R.id.slider_adjustment);
        tvAdjustmentLabel = view.findViewById(R.id.tv_adjustment_label);
        adjBrightness = view.findViewById(R.id.adj_brightness);
        adjContrast = view.findViewById(R.id.adj_contrast);
        adjSaturation = view.findViewById(R.id.adj_saturation);
        adjTemperature = view.findViewById(R.id.adj_temperature);

        ((ImageView)adjBrightness.findViewById(R.id.iv_adjustment_icon)).setImageResource(R.drawable.ic_brightness);
        ((TextView)adjBrightness.findViewById(R.id.tv_adjustment_label)).setText("Brightness");
        ((ImageView)adjContrast.findViewById(R.id.iv_adjustment_icon)).setImageResource(R.drawable.ic_contrast);
        ((TextView)adjContrast.findViewById(R.id.tv_adjustment_label)).setText("Contrast");
        ((ImageView)adjSaturation.findViewById(R.id.iv_adjustment_icon)).setImageResource(R.drawable.ic_saturation);
        ((TextView)adjSaturation.findViewById(R.id.tv_adjustment_label)).setText("Saturation");
        ((ImageView)adjTemperature.findViewById(R.id.iv_adjustment_icon)).setImageResource(R.drawable.ic_temperature);
        ((TextView)adjTemperature.findViewById(R.id.tv_adjustment_label)).setText("Color Temp");

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
                    final boolean isVideo = isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode;

                    if (isVideo) {
                        extractFramesForVideo(12);
                    } else if (isZipMode) {
                        extractFramesFromZip();
                    } else if (isMultiImageMode) {
                        copyMultipleImagesToRawDir();
                    } else { // Single Image
                        rawFrames = new ArrayList<>(Arrays.asList(copySingleImageToRawDir(inputFilePath)));
                    }

                    if (rawFrames != null && !rawFrames.isEmpty()) {
                        sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames.get(0).getAbsolutePath());
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateMainDisplay(sourceBitmapForTuning);
                                setupAnalysisControls(isVideo);
                                setupFilmStrip(rawFrames);
                                statusTextView.setText("Ready. Select a method and press Analyze.");
                                progressBar.setIndeterminate(false);
                                progressBar.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        throw new Exception("No images found to process.");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Initial setup failed", e);
                    showErrorDialog("Setup Error", "Failed to prepare files: " + e.getMessage(), true);
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
            aiControlsContainer.setVisibility(View.GONE);
        }

        fineTuningControls.setVisibility(View.GONE);

        ArrayAdapter<CharSequence> methodAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.method_options, android.R.layout.simple_spinner_item);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        methodSpinner.setAdapter(methodAdapter);
        methodSpinner.setSelection(selectedMethod, false);

        ArrayAdapter<CharSequence> styleAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.ai_sketch_style_options, android.R.layout.simple_spinner_item);
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        styleSpinner.setAdapter(styleAdapter);


        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMethod = position;
                boolean isVideoLoaded = isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode;

                if (isVideoLoaded && (selectedMethod == 11 || selectedMethod == 12)) {
                    aiControlsContainer.setVisibility(View.VISIBLE);
                } else {
                    aiControlsContainer.setVisibility(View.GONE);
                    if (switchEnableAi != null) {
                        switchEnableAi.setChecked(false);
                    }
                }

                if (selectedMethod == 13) {
                    aiStyleControlsContainer.setVisibility(View.VISIBLE);
                } else {
                    aiStyleControlsContainer.setVisibility(View.GONE);
                }

                if (isVideoLoaded) {
                    updateUiForVideoMode(sliderAnalysisMode.getProgress());
                } else {
                    updateUiForImageMode(switchAutomaticScan.isChecked());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMethod = 0;
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
                boolean isVideoLivePreview = (isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode) && sliderAnalysisMode.getProgress() == 1;
                boolean isImageManualTune = (!isVideoFile(inputFilePath) || isZipMode || isMultiImageMode) && !switchAutomaticScan.isChecked();

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
        boolean needsDepthSharpness = selectedMethod >= 3 && selectedMethod <= 7;
        boolean needsKsize = selectedMethod >= 10 && selectedMethod <= 12;
        boolean needsMethod9Adjustments = selectedMethod == 8;

        if (isAutomatic) {
            fineTuningControls.setVisibility(View.GONE);
        } else {
            fineTuningControls.setVisibility(View.VISIBLE);
            
            ksizeControlsContainer.setVisibility(needsKsize ? View.VISIBLE : View.GONE);
            adjustmentControlsContainer.setVisibility(needsMethod9Adjustments ? View.VISIBLE : View.GONE);

            boolean otherControlsVisible = needsKsize || needsMethod9Adjustments;
            sliderDepth.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
            sliderSharpness.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
            tvDepthLabel.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
            tvSharpnessLabel.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
        }
    }

    private void updateUiForVideoMode(int mode) {
        boolean needsDepthSharpness = selectedMethod >= 3 && selectedMethod <= 7;
        boolean needsKsize = selectedMethod >= 10 && selectedMethod <= 12;
        boolean needsMethod9Adjustments = selectedMethod == 8;

        switch(mode) {
            case 0:
                fineTuningControls.setVisibility(View.GONE);
                analyzeButton.setText("Analyze Video");
                break;
            case 1:
            case 2:
                if (needsDepthSharpness || needsKsize || needsMethod9Adjustments) {
                    fineTuningControls.setVisibility(View.VISIBLE);
                    ksizeControlsContainer.setVisibility(needsKsize ? View.VISIBLE : View.GONE);
                    adjustmentControlsContainer.setVisibility(needsMethod9Adjustments ? View.VISIBLE : View.GONE);

                    boolean otherControlsVisible = needsKsize || needsMethod9Adjustments;
                    sliderDepth.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
                    sliderSharpness.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
                    tvDepthLabel.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
                    tvSharpnessLabel.setVisibility(otherControlsVisible ? View.GONE : View.VISIBLE);
                } else {
                     fineTuningControls.setVisibility(View.GONE);
                }

                if (mode == 1) {
                    analyzeButton.setText("Analyze Preview");
                    performLivePreviewAnalysis();
                } else {
                    analyzeButton.setText("Apply to Video");
                }
                break;
        }
    }
    
    private void setupAdjustmentControls() {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSelectedAdjustment(v);
            }
        };

        adjBrightness.setOnClickListener(listener);
        adjContrast.setOnClickListener(listener);
        adjSaturation.setOnClickListener(listener);
        adjTemperature.setOnClickListener(listener);

        sliderAdjustment.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateAdjustmentValue(progress);
                    livePreviewHandler.removeCallbacks(livePreviewRunnable);
                    livePreviewRunnable = new Runnable() {
                        @Override
                        public void run() {
                            performLivePreviewAnalysisForAdjustments();
                        }
                    };
                    livePreviewHandler.postDelayed(livePreviewRunnable, 100);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updateSelectedAdjustment(adjBrightness);
    }
    
    private void updateSelectedAdjustment(View selectedView) {
        adjBrightness.setSelected(selectedView.getId() == adjBrightness.getId());
        adjContrast.setSelected(selectedView.getId() == adjContrast.getId());
        adjSaturation.setSelected(selectedView.getId() == adjSaturation.getId());
        adjTemperature.setSelected(selectedView.getId() == adjTemperature.getId());
        
        int progress = 0;
        String label = "Adjust: ";
        if (adjBrightness.isSelected()) {
            currentAdjustmentMode = 0;
            progress = brightnessValue;
            label += "Brightness";
        } else if (adjContrast.isSelected()) {
            currentAdjustmentMode = 1;
            progress = contrastValue;
            label += "Contrast";
        } else if (adjSaturation.isSelected()) {
            currentAdjustmentMode = 2;
            progress = saturationValue;
            label += "Saturation";
        } else if (adjTemperature.isSelected()) {
            currentAdjustmentMode = 3;
            progress = temperatureValue;
            label += "Color Temp";
        }
        tvAdjustmentLabel.setText(label);
        sliderAdjustment.setProgress(progress);
    }

    private void updateAdjustmentValue(int progress) {
        switch (currentAdjustmentMode) {
            case 0: brightnessValue = progress; break;
            case 1: contrastValue = progress; break;
            case 2: saturationValue = progress; break;
            case 3: temperatureValue = progress; break;
        }
    }
    
    private void performLivePreviewAnalysisForAdjustments() {
        if (sourceBitmapForTuning == null) return;
        
        updateStatus("Applying adjustments...", true);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap adjustedBitmap = DeepScanProcessor.applyMethod9Adjustments(
                    sourceBitmapForTuning.copy(Bitmap.Config.ARGB_8888, true),
                    brightnessValue, contrastValue, saturationValue, temperatureValue
                );

                DeepScanProcessor.ScanListener listener = new DeepScanProcessor.ScanListener() {
                    @Override
                    public void onScanProgress(int pass, int totalPasses, String status, Bitmap intermediateResult) {}
                    @Override
                    public void onScanComplete(final DeepScanProcessor.ProcessingResult finalResult) {
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                updateMainDisplay(finalResult.resultBitmap);
                                statusTextView.setText("Adjust sliders or Save.");
                                btnSave.setVisibility(View.VISIBLE);
                                progressBar.setVisibility(View.GONE);
                                setUiEnabled(true);
                                if (analysisControlsContainer != null) {
                                    analysisControlsContainer.setVisibility(View.VISIBLE);
                                }
                            }
                        });
                    }
                };
                DeepScanProcessor.processMethod9(adjustedBitmap, listener);
            }
        }).start();
    }


    private void performLivePreviewAnalysis() {
        if (sourceBitmapForTuning == null) {
            return;
        }

        if (selectedMethod == 8) {
            performLivePreviewAnalysisForAdjustments();
            return;
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
                showErrorDialog("API Key Missing", "AI Assist is enabled, but the Gemini API Key has not been set. Please add your key in the settings panel.", false);
                setUiEnabled(true);
                return;
            }
        }

        livePreviewHandler.removeCallbacksAndMessages(null);
        setUiEnabled(false);
        analysisControlsContainer.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        isReEditing = false;

        if (rawFrames == null || rawFrames.isEmpty()) {
            showErrorDialog("Processing Error", "No frames available to process.", true);
            setUiEnabled(true);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean isVideo = isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode;
                    if (isVideo) {
                        int mode = sliderAnalysisMode.getProgress();
                        if (mode == 1) {
                            processSingleFrameManually();
                        } else {
                            processAllFrames();
                        }
                    } else {
                        if (switchAutomaticScan.isChecked()) {
                            processAllFrames();
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
                int frameIndex = isReEditing ? reEditFrameIndex : 0;
                if (rawFrames != null && rawFrames.size() > frameIndex) {
                    sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames.get(frameIndex).getAbsolutePath());
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

    private void processAllFrames() throws Exception {
        final int totalFrames = rawFrames.size();
        final int ksize = sliderKsize.getProgress();
        final int depth = sliderDepth.getProgress();
        final int sharpness = sliderSharpness.getProgress();
        int currentKsize = ksize;

        if (goldStandardBitmap != null && !goldStandardBitmap.isRecycled()) {
            goldStandardBitmap.recycle();
        }
        goldStandardBitmap = null;

        uiHandler.post(new Runnable() { @Override public void run() { progressBar.setVisibility(View.VISIBLE); }});

        if (selectedMethod == 13) {
            updateStatus("Preparing AI Style Transfer...", true);
            Interpreter predictionInterpreter = null;
            Interpreter transferInterpreter = null;
            try {
                Interpreter.Options options = new Interpreter.Options();
                MappedByteBuffer predictionModel = loadModelFile("magenta_prediction.tflite");
                predictionInterpreter = new Interpreter(predictionModel, options);
                MappedByteBuffer transferModel = loadModelFile("magenta_transfer.tflite");
                transferInterpreter = new Interpreter(transferModel, options);
                int selectedStyleIndex = styleSpinner.getSelectedItemPosition();
                String styleAssetName = styleAssetFiles[selectedStyleIndex];
                Bitmap styleBitmap = loadBitmapFromAssets(styleAssetName);
                updateStatus("Analyzing Art Style...", true);
                float[] styleVector = DeepScanProcessor.runStylePrediction(styleBitmap, predictionInterpreter);
                styleBitmap.recycle();
                for (int i = 0; i < totalFrames; i++) {
                    final int frameNum = i + 1;
                    final int frameIndex = i;
                    updateStatus("Applying style to frame " + frameNum + " of " + totalFrames, false);
                    updateProgress(frameNum, totalFrames);
                    updateCurrentFrameHighlight(frameIndex);
                    Bitmap contentBitmap = decodeAndRotateBitmap(rawFrames.get(frameIndex).getAbsolutePath());
                    if (contentBitmap == null) continue;
                    Bitmap stylizedBitmap = DeepScanProcessor.runStyleTransfer(contentBitmap, styleVector, transferInterpreter);
                    saveProcessedFrame(stylizedBitmap, frameIndex);
                    contentBitmap.recycle();
                }
            } finally {
                if (predictionInterpreter != null) predictionInterpreter.close();
                if (transferInterpreter != null) transferInterpreter.close();
            }
        } else {
            for (int i = 0; i < totalFrames; i++) {
                final int frameNum = i + 1;
                final int frameIndex = i;

                updateStatus("Processing frame " + frameNum + " of " + totalFrames, false);
                updateProgress(frameNum, totalFrames);
                updateCurrentFrameHighlight(frameIndex);

                Bitmap orientedBitmap = decodeAndRotateBitmap(rawFrames.get(frameIndex).getAbsolutePath());
                if (orientedBitmap == null) continue;
                
                if (selectedMethod == 8) {
                    orientedBitmap = DeepScanProcessor.applyMethod9Adjustments(orientedBitmap, brightnessValue, contrastValue, saturationValue, temperatureValue);
                }

                boolean isVideo = isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode;
                if (isVideo && switchEnableAi.isChecked() && (selectedMethod == 11 || selectedMethod == 12)) {
                    if (frameIndex == 0) {
                        updateStatus("Processing frame 1 to set Gold Standard...", false);
                        Bitmap processedFirstFrame = getAiPencilScanAsBitmap(orientedBitmap, currentKsize);
                        if (processedFirstFrame != null) {
                            goldStandardBitmap = processedFirstFrame;
                            saveProcessedFrame(goldStandardBitmap, frameIndex);
                        } else {
                            throw new Exception("Failed to process the first frame to create a Gold Standard.");
                        }
                    } else {
                        CorrectedKsize correction = GeminiAiHelper.checkFrameConsistency(
                                sharedPreferences.getString("GEMINI_API_KEY", ""),
                                goldStandardBitmap,
                                orientedBitmap,
                                currentKsize
                        );
                        currentKsize = correction.ksize;
                        if (correction.wasCorrected) {
                            final int correctedKsize = currentKsize;
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "AI adjusted ksize to " + correctedKsize, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        beginBlockingAiGuidedScan(orientedBitmap, frameIndex, currentKsize);
                    }
                } else {
                    if (selectedMethod <= 1) {
                        beginBlockingAiScan(orientedBitmap, frameIndex);
                    } else if (selectedMethod >= 10 && selectedMethod <= 12) {
                        beginBlockingPencilOrNewAiScan(orientedBitmap, frameIndex, currentKsize);
                    } else if (selectedMethod == 2) {
                        beginMethod1LiveScan(orientedBitmap, frameIndex);
                    } else {
                        boolean isVideoStandardAuto = isVideo && sliderAnalysisMode.getProgress() == 0;
                        boolean isImageAutoScan = !isVideo && switchAutomaticScan.isChecked();
                        if (isVideoStandardAuto || isImageAutoScan) {
                            beginStandardScan(orientedBitmap, frameIndex);
                        } else {
                            beginTunedScan(orientedBitmap, frameIndex, depth, sharpness);
                        }
                    }
                }
                if (orientedBitmap != null && !orientedBitmap.isRecycled()) {
                    orientedBitmap.recycle();
                }
            }
        }
        
        if (!isReEditing) {
            cleanupTempFiles();
        }

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean isVideo = isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode;
                if (isVideo || isMultiImageMode || isZipMode) {
                    showSuccessDialog("Processing Complete", "Your files have been saved to:\n\n" + processedFramesDir);
                    statusTextView.setText("Processing Complete. Click a thumbnail to re-edit.");
                    btnSave.setVisibility(View.GONE);
                    btnSave.setText("Save");
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
                    saveProcessedFrame(finalResult.resultBitmap, frameIndex);
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
                saveProcessedFrame(finalResult.resultBitmap, frameIndex);
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
            case 8: logicalMethod = 7;
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
                    saveProcessedFrame(finalResult.resultBitmap, frameIndex);
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
                        File outFile;
                        if (isReEditing && reEditFrameIndex != -1) {
                            String fileName = new File(rawFrames.get(reEditFrameIndex).getName()).getName();
                            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                            String processedFileName = baseName.replace("raw_", "processed_") + ".png";
                            outFile = new File(processedFramesDir, processedFileName);
                        } else {
                            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                            String fileName = "Kop_Saved_" + timestamp + ".png";
                            outFile = new File(processedFramesDir, fileName);
                        }

                        ImageProcessor.saveBitmap(bitmapToSave, outFile.getAbsolutePath());
                        if (!isReEditing) {
                             cleanupTempFiles();
                        }

                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), "Image saved to " + outFile.getParent(), Toast.LENGTH_LONG).show();
                                if (isReEditing) {
                                    if(filmStripAdapter != null) {
                                        filmStripAdapter.notifyItemChanged(reEditFrameIndex);
                                    }
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save image", e);
                        uiHandler.post(new Runnable() {
                             @Override public void run() {
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
                saveProcessedFrame(finalResult.resultBitmap, frameIndex);
                updateScanStatus("Scan Complete. Found " + finalResult.objectsFound + " objects.", -1, -1);
                latch.countDown();
            }
        };
        DeepScanProcessor.processMethod1(bitmap, listener);
        latch.await();

        boolean isVideo = isVideoFile(inputFilePath) && !isZipMode && !isMultiImageMode;
        if (!isVideo) {
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
            File[] frameFiles = new File(rawFramesDir).listFiles();
            
            if (frameFiles != null) {
                sortFrames(frameFiles);
                rawFrames = new ArrayList<>(Arrays.asList(frameFiles));
            } else {
                rawFrames = new ArrayList<>();
            }

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (rawFrames != null && !rawFrames.isEmpty()) {
                        setupFilmStrip(rawFrames);
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
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        
        if (isZipMode) {
            File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/zip_images/Project_" + timestamp);
            projectDir.mkdirs();
            rawFramesDir = new File(projectDir, "raw").getAbsolutePath();
            processedFramesDir = new File(projectDir, "processed").getAbsolutePath();
        } else if (isMultiImageMode) {
            File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/MultiImage_Project_" + timestamp);
            projectDir.mkdirs();
            rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
            processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
        } else if (isVideoFile(inputFilePath)) {
            File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Video_Project_" + timestamp);
            projectDir.mkdirs();
            rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
            processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
        } else { // Single Image
            File permanentOutputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Processed_Images");
            permanentOutputDir.mkdirs();
            processedFramesDir = permanentOutputDir.getAbsolutePath();
            rawFramesDir = new File(getContext().getCacheDir(), "temp_image_processing").getAbsolutePath();
        }
        
        new File(rawFramesDir).mkdirs();
        new File(processedFramesDir).mkdirs();
    }

    private File[] copySingleImageToRawDir(String path) throws IOException {
        File source = new File(path);
        File destFile = new File(rawFramesDir, "raw_frame_00000.png");
        copyFile(source, destFile);
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
        if (path == null || path.isEmpty()) return false;
        String lowerCasePath = path.toLowerCase(Locale.US);
        return lowerCasePath.endsWith(".mp4") || lowerCasePath.endsWith(".mov") || lowerCasePath.endsWith(".3gp") || lowerCasePath.endsWith(".mkv");
    }

    private void setupFilmStrip(final List<File> frames) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (frames != null && !frames.isEmpty() && frames.size() > 1) { // Only show for more than 1 image
                    if (filmStripAdapter == null) {
                        filmStripAdapter = new FilmStripAdapter(getContext(), frames, new FilmStripAdapter.OnFrameClickListener() {
                            @Override
                            public void onFrameClicked(int position) {
                                // NEW: This is the re-edit trigger
                                File procDir = new File(processedFramesDir);
                                if (procDir.exists() && procDir.listFiles() != null && procDir.listFiles().length > 0) {
                                    setupReEditMode(position);
                                }
                            }
                        });
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

    private void setupReEditMode(int position) {
        isReEditing = true;
        reEditFrameIndex = position;
        isFirstFineTuneAnalysis = true; 

        String rawFileName = rawFrames.get(position).getName();
        String processedFileName = rawFileName.replace("raw_", "processed_");
        File processedFile = new File(processedFramesDir, processedFileName);
        
        if (processedFile.exists()) {
            try {
                sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames.get(position).getAbsolutePath());
                Bitmap currentProcessedBitmap = BitmapFactory.decodeFile(processedFile.getAbsolutePath());
                updateMainDisplay(currentProcessedBitmap);

                statusTextView.setText("Re-editing image " + (position + 1) + ". Adjust settings and press Analyze.");
                btnSave.setText("Save Edit");
                btnSave.setVisibility(View.VISIBLE);
                analyzeButton.setVisibility(View.VISIBLE);
                setUiEnabled(true);
                analysisControlsContainer.setVisibility(View.VISIBLE);
                
                updateUiForImageMode(false); // Force manual tuning mode for re-edits

            } catch (IOException e) {
                showErrorDialog("Error", "Could not load image for re-editing.", false);
            }
        } else {
             Toast.makeText(getContext(), "Processed image not found. Please analyze first.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void cleanupTempFiles() {
        if (rawFramesDir != null && !rawFramesDir.isEmpty()) {
            boolean isPermanentProject = isMultiImageMode || isZipMode;
            if (!isPermanentProject) {
                File dir = new File(rawFramesDir);
                if (dir.exists()) {
                    deleteRecursive(dir);
                }
            }
        }
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

    private void copyFile(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
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
            updateStatus("Processing frame " + (frameIndex + 1) + " of " + rawFrames.size(), false);

            if (!analysisResult.hasObjects()) {
                Log.w(TAG, "AI found no objects for guided scan on frame " + frameIndex + ". Falling back to standard scan.");
                beginBlockingPencilOrNewAiScan(bitmap, frameIndex, ksize);
                return;
            }

            Bitmap aiMask = ImageProcessor.createUnifiedMaskFromRects(
                bitmap.getWidth(),
                bitmap.getHeight(),
                analysisResult.getObjectBounds()
            );

            Bitmap finalProcessedBitmap = performAiGuidedScan(bitmap, ksize, aiMask);

            saveProcessedFrame(finalProcessedBitmap, frameIndex);

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

    private MappedByteBuffer loadModelFile(String modelFileName) throws IOException {
        AssetFileDescriptor fileDescriptor = getContext().getAssets().openFd(modelFileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private Bitmap loadBitmapFromAssets(String fileName) throws IOException {
        AssetManager assetManager = getContext().getAssets();
        InputStream inputStream = assetManager.open(fileName);
        return BitmapFactory.decodeStream(inputStream);
    }
    
    private void extractFramesFromZip() throws IOException {
        uiHandler.post(new Runnable() { @Override public void run() {
            statusTextView.setText("Extracting images from ZIP...");
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.VISIBLE);
        }});
        
        File zipFile = new File(inputFilePath);
        File tempExtractionDir = new File(getContext().getCacheDir(), "zip_temp");
        if(tempExtractionDir.exists()) deleteRecursive(tempExtractionDir);
        tempExtractionDir.mkdirs();

        List<File> extractedImages = ZipExtractor.extractImages(zipFile, tempExtractionDir);
        
        rawFrames = new ArrayList<>();
        for(int i = 0; i < extractedImages.size(); i++) {
            File sourceFile = extractedImages.get(i);
            String destFileName = String.format("raw_%05d.png", i);
            File destFile = new File(rawFramesDir, destFileName);
            copyFile(sourceFile, destFile);
            rawFrames.add(destFile);
        }
        deleteRecursive(tempExtractionDir);
    }
    
    private void copyMultipleImagesToRawDir() throws IOException {
        rawFrames = new ArrayList<>();
        for (int i = 0; i < inputFilePaths.size(); i++) {
            File sourceFile = new File(inputFilePaths.get(i));
            String destFileName = String.format("raw_%05d.png", i);
            File destFile = new File(rawFramesDir, destFileName);
            copyFile(sourceFile, destFile);
            rawFrames.add(destFile);
        }
    }
}