package com.kop.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Matrix;

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

public class ProcessingActivity extends AppCompatActivity {

    private static final String TAG = "ProcessingActivity";
    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private ImageView mainDisplay;
    private ImageView overlayDisplay; // For Method 1
    private TextView statusTextView;
    private ProgressBar progressBar;
    private LinearLayout analysisControlsContainer, fpsControlsContainer;
    private RecyclerView filmStripRecyclerView;
    private FilmStripAdapter filmStripAdapter;
    private Button analyzeButton, closeButton;
    private Spinner fpsSpinner, methodSpinner;
    private Handler uiHandler;
    private String inputFilePath;
    private File[] rawFrames;
    private String rawFramesDir, processedFramesDir;
    private int selectedMethod = 0; // Default to Method 0

    // --- UI ELEMENTS AND STATE FOR MANUAL/AUTOMATIC MODES ---
    private LinearLayout fineTuningControls;
    private SeekBar sliderDepth, sliderSharpness;
    private Button btnSave;
    private Switch switchAutomaticScan;
    private Bitmap sourceBitmapForTuning; // Holds the original image for re-analysis
    private boolean isFirstFineTuneAnalysis = true;
    
    // --- UI ELEMENTS FOR PROGRESS DISPLAY ---
    private LinearLayout scanStatusContainer;
    private TextView scanStatusTextView;
    private ProgressBar scanProgressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);
        initializeViews();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_FILE_PATH)) {
            inputFilePath = intent.getStringExtra(EXTRA_FILE_PATH);
            startInitialSetup();
        } else {
            showErrorDialog("Error", "No input file path provided.", true);
        }
    }

    private void initializeViews() {
        mainDisplay = findViewById(R.id.iv_main_display);
        overlayDisplay = findViewById(R.id.iv_overlay_display);
        statusTextView = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress_bar);
        analysisControlsContainer = findViewById(R.id.analysis_controls);
        fpsControlsContainer = findViewById(R.id.fps_controls);
        filmStripRecyclerView = findViewById(R.id.rv_film_strip);
        analyzeButton = findViewById(R.id.btn_analyze);
        fpsSpinner = findViewById(R.id.spinner_fps);
        methodSpinner = findViewById(R.id.spinner_method);
        closeButton = findViewById(R.id.btn_close);
        uiHandler = new Handler(Looper.getMainLooper());
        
        scanStatusTextView = findViewById(R.id.tv_scan_status);
        scanProgressBar = findViewById(R.id.scan_progress_bar);
        scanStatusContainer = findViewById(R.id.scan_status_container);

        fineTuningControls = findViewById(R.id.fine_tuning_controls);
        sliderDepth = findViewById(R.id.slider_depth);
        sliderSharpness = findViewById(R.id.slider_sharpness);
        btnSave = findViewById(R.id.btn_save);
        switchAutomaticScan = findViewById(R.id.switch_automatic_scan);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
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
                        extractFramesForVideo(12); // Default FPS
                    } else {
                        rawFrames = extractOrCopyFrames(inputFilePath);
                    }
                    
                    if (rawFrames != null && rawFrames.length > 0) {
                        sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames[0].getAbsolutePath());
                        updateMainDisplay(sourceBitmapForTuning);
                        
                        // NEW WORKFLOW: Immediately start the AI analysis
                        beginAutomaticAiScan();
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Initial setup failed", e);
                    showErrorDialog("Setup Error", "Failed to prepare files.", true);
                }
            }
        }).start();
    }
    
    // --- NEW AUTOMATIC AI SCAN WORKFLOW ---
    private void beginAutomaticAiScan() {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "Source image not found for AI scan.", true);
            return;
        }

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                statusTextView.setText("Performing AI Analysis...");
                progressBar.setIndeterminate(true);
                analysisControlsContainer.setVisibility(View.GONE);
                btnSave.setVisibility(View.GONE);
            }
        });
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                DeepScanProcessor.AiScanListener listener = new DeepScanProcessor.AiScanListener() {
                    @Override
                    public void onAiScanComplete(DeepScanProcessor.ProcessingResult finalResult) {
                        updateMainDisplay(finalResult.resultBitmap);
                        
                        // Now that the automatic scan is done, show the controls
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                setupAnalysisControls(isVideoFile(inputFilePath));
                                statusTextView.setText("AI Analysis Complete. Save or choose another method.");
                                progressBar.setIndeterminate(false);
                                btnSave.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                };
                
                // Pass the application context, which is needed by MediaPipe
                DeepScanProcessor.processMethod0(getApplicationContext(), sourceBitmapForTuning, listener);
            }
        }).start();
    }
    
    private void setupAnalysisControls(boolean isVideo) {
        analysisControlsContainer.setVisibility(View.VISIBLE);
        fineTuningControls.setVisibility(View.GONE);

        ArrayAdapter<CharSequence> methodAdapter = ArrayAdapter.createFromResource(this,
                R.array.method_options, android.R.layout.simple_spinner_item);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        methodSpinner.setAdapter(methodAdapter);
        methodSpinner.setSelection(0, false); // Select Method 0 by default, don't trigger listener
        methodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // The position in the array directly corresponds to the method number
                selectedMethod = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMethod = 0;
            }
        });

        if (isVideo) {
            fpsControlsContainer.setVisibility(View.VISIBLE);
            ArrayAdapter<CharSequence> fpsAdapter = ArrayAdapter.createFromResource(this,
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
                if (isChecked) {
                    fineTuningControls.setVisibility(View.GONE);
                } else {
                    if (!isFirstFineTuneAnalysis) {
                        fineTuningControls.setVisibility(View.VISIBLE);
                    }
                }
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

    private void beginAnalysis() {
        analyzeButton.setEnabled(false);
        analysisControlsContainer.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (rawFrames == null || rawFrames.length == 0) {
                        throw new Exception("No frames available to process.");
                    }
                    
                    if (selectedMethod == 0) {
                        beginAutomaticAiScan();
                        return;
                    }

                    if (switchAutomaticScan.isChecked() || selectedMethod == 1) {
                        processAllFramesAutomatically();
                    } else {
                        if (isFirstFineTuneAnalysis) {
                            sourceBitmapForTuning = decodeAndRotateBitmap(rawFrames[0].getAbsolutePath());
                        }
                        performFineTuningAnalysis();
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Analysis failed", e);
                    String message = (e.getMessage() != null) ? e.getMessage() : "An unknown error occurred.";
                    showErrorDialog("Processing Error", message, true);
                }
            }
        }).start();
    }
    
    private void processAllFramesAutomatically() throws Exception {
        final int totalFrames = rawFrames.length;
        for (int i = 0; i < totalFrames; i++) {
            final int frameNum = i + 1;
            final int frameIndex = i;

            updateStatus("Processing frame " + frameNum + " of " + totalFrames, false);
            updateProgress(frameNum, totalFrames);
            updateCurrentFrameHighlight(frameIndex);

            Bitmap orientedBitmap = decodeAndRotateBitmap(rawFrames[frameIndex].getAbsolutePath());
            if (orientedBitmap == null) continue;

            if (selectedMethod == 1) {
                beginMethod1LiveScan(orientedBitmap, frameIndex);
            } else {
                beginStandardScan(orientedBitmap, frameIndex);
            }

            orientedBitmap.recycle();
        }
        showSuccessDialog("Processing Complete", "Your rotoscoped frames have been saved to:\n\n" + processedFramesDir);
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
                
                // NO LONGER SAVING AUTOMATICALLY FOR SINGLE IMAGES
                boolean isVideo = isVideoFile(inputFilePath);
                if (isVideo) {
                    String outPath = new File(processedFramesDir, String.format("processed_%05d.png", frameIndex)).getAbsolutePath();
                    try {
                        ImageProcessor.saveBitmap(finalResult.resultBitmap, outPath);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save processed frame.", e);
                    }
                }
                
                updateScanStatus("Scan Complete. Found " + finalResult.objectsFound + " objects.", -1, -1);
                latch.countDown();
            }
        };

        switch (selectedMethod) {
            case 2: DeepScanProcessor.processMethod2(bitmap, listener); break;
            case 3: DeepScanProcessor.processMethod3(bitmap, listener); break;
            case 4: DeepScanProcessor.processMethod4(bitmap, listener); break;
            case 5: DeepScanProcessor.processMethod5(bitmap, listener); break;
            case 6: DeepScanProcessor.processMethod6(bitmap, listener); break;
            case 7: DeepScanProcessor.processMethod7(bitmap, listener); break;
            case 8: DeepScanProcessor.processMethod8(bitmap, listener); break;
            case 9: DeepScanProcessor.processMethod9(bitmap, listener); break;
            case 10: default: DeepScanProcessor.processMethod10(bitmap, listener); break;
        }

        latch.await();
        boolean isVideo = isVideoFile(inputFilePath);
        if (!isVideo) {
             uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    btnSave.setVisibility(View.VISIBLE);
                    analysisControlsContainer.setVisibility(View.VISIBLE);
                    analyzeButton.setEnabled(true);
                }
            });
        }
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        hideScanStatus();
    }
    
    private void performFineTuningAnalysis() {
        if (sourceBitmapForTuning == null) {
            showErrorDialog("Error", "No source image is loaded for tuning.", true);
            return;
        }
        updateStatus("Analyzing...", true);
        
        final int depth = sliderDepth.getProgress();
        final int sharpness = sliderSharpness.getProgress();

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
                            if (!switchAutomaticScan.isChecked()) {
                                fineTuningControls.setVisibility(View.VISIBLE);
                            }
                            isFirstFineTuneAnalysis = false;
                        }
                        analysisControlsContainer.setVisibility(View.VISIBLE);
                        btnSave.setVisibility(View.VISIBLE);
                        updateStatus("Ready for fine-tuning. Adjust sliders and Analyze.", false);
                        updateProgress(0, 1);
                        analyzeButton.setEnabled(true);
                    }
                });
            }
        };
        DeepScanProcessor.processWithFineTuning(sourceBitmapForTuning, selectedMethod, depth, sharpness, listener);
    }
    
    private void saveCurrentImage() {
        if (mainDisplay.getDrawable() == null) {
            Toast.makeText(this, "No image to save.", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(ProcessingActivity.this, "Image saved!", Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save image", e);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ProcessingActivity.this, "Error saving image.", Toast.LENGTH_SHORT).show();
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
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
        hideScanStatus();
    }
    
    private void extractFramesForVideo(int fps) {
        try {
            updateStatus("Extracting " + fps + " FPS...", true);
            File dir = new File(rawFramesDir);
            if(dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) { for (File file : files) file.delete(); }
            }
            FrameExtractor.extractFrames(inputFilePath, rawFramesDir, fps);
            rawFrames = new File(rawFramesDir).listFiles();
            if (rawFrames != null) {
                sortFrames(rawFrames);
                setupFilmStrip(Arrays.asList(rawFrames));
            }
        } catch (Exception e) {
            Log.e(TAG, "Frame extraction failed", e);
            showErrorDialog("Extraction Error", "Could not extract frames from video.", false);
        }
    }
    
    // --- HELPER METHODS (UNCHANGED) ---

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
                    filmStripAdapter = new FilmStripAdapter(ProcessingActivity.this, frames);
                    LinearLayoutManager layoutManager = new LinearLayoutManager(ProcessingActivity.this, LinearLayoutManager.HORIZONTAL, false);
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
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ProcessingActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .show();
            }
        });
    }

    private void showErrorDialog(final String title, final String message, final boolean finishActivity) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ProcessingActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (finishActivity) {
                                    finish();
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
}
