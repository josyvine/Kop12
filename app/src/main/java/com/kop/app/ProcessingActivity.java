package com.kop.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Matrix;

import java.io.File;
import java.io.FileInputStream;  // FIX: Added missing import
import java.io.FileOutputStream; // FIX: Added missing import
import java.io.IOException;
import java.io.InputStream;      // FIX: Added missing import
import java.io.OutputStream;     // FIX: Added missing import
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private TextView statusTextView, scanStatusTextView;
    private ProgressBar progressBar, scanProgressBar;
    private LinearLayout scanStatusContainer, videoControlsContainer;
    private RecyclerView filmStripRecyclerView;
    private FilmStripAdapter filmStripAdapter;
    private Button analyzeButton, closeButton;
    private Spinner fpsSpinner;
    private Handler uiHandler;
    private String inputFilePath;
    private File[] rawFrames;
    private String rawFramesDir, processedFramesDir;

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
        statusTextView = findViewById(R.id.tv_status);
        scanStatusTextView = findViewById(R.id.tv_scan_status);
        progressBar = findViewById(R.id.progress_bar);
        scanProgressBar = findViewById(R.id.scan_progress_bar);
        scanStatusContainer = findViewById(R.id.scan_status_container);
        videoControlsContainer = findViewById(R.id.video_controls);
        filmStripRecyclerView = findViewById(R.id.rv_film_strip);
        analyzeButton = findViewById(R.id.btn_analyze);
        fpsSpinner = findViewById(R.id.spinner_fps);
        closeButton = findViewById(R.id.btn_close);
        uiHandler = new Handler(Looper.getMainLooper());

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
                    boolean isVideo = isVideoFile(inputFilePath);

                    if (isVideo) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setupVideoControls();
                            }
                        });
                        extractFramesForVideo(12); // Default FPS
                    } else {
                        rawFrames = extractOrCopyFrames(inputFilePath);
                        beginDeepScanLoop();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Initial setup failed", e);
                    showErrorDialog("Setup Error", "Failed to prepare files.", true);
                }
            }
        }).start();
    }
    
    private void setupVideoControls() {
        videoControlsContainer.setVisibility(View.VISIBLE);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.fps_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fpsSpinner.setAdapter(adapter);
        fpsSpinner.setSelection(2); // Default to 12 FPS

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

        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                videoControlsContainer.setVisibility(View.GONE);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        beginDeepScanLoop();
                    }
                }).start();
            }
        });
    }

    private void extractFramesForVideo(int fps) {
        try {
            updateStatus("Extracting " + fps + " FPS...", true);
            File dir = new File(rawFramesDir);
            if(dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            FrameExtractor.extractFrames(inputFilePath, rawFramesDir, fps);
            rawFrames = new File(rawFramesDir).listFiles();
            if (rawFrames != null) {
                sortFrames(rawFrames);
                setupFilmStrip(Arrays.asList(rawFrames));
                updateStatus("Ready to analyze " + rawFrames.length + " frames.", false);
                updateProgress(0, rawFrames.length);
            } else {
                updateStatus("No frames extracted.", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Frame extraction failed", e);
            showErrorDialog("Extraction Error", "Could not extract frames from video.", false);
        }
    }

    private void beginDeepScanLoop() {
        try {
            if (rawFrames == null || rawFrames.length == 0) {
                throw new Exception("No frames available to process.");
            }
            final int totalFrames = rawFrames.length;
            for (int i = 0; i < totalFrames; i++) {
                final int frameNum = i + 1;
                final int frameIndex = i;

                updateStatus("Processing frame " + frameNum + " of " + totalFrames, false);
                updateProgress(frameNum, totalFrames);
                updateCurrentFrameHighlight(frameIndex);

                File frameFile = rawFrames[frameIndex];
                Bitmap orientedBitmap = decodeAndRotateBitmap(frameFile.getAbsolutePath());
                if (orientedBitmap == null) continue;

                final CountDownLatch latch = new CountDownLatch(1);

                DeepScanProcessor.performDeepScan(orientedBitmap, new DeepScanProcessor.ScanListener() {
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
                });
                latch.await();

                try { Thread.sleep(2000); } catch (InterruptedException e) {}
                hideScanStatus();
                orientedBitmap.recycle();
            }
            showSuccessDialog("Processing Complete", "Your rotoscoped frames have been saved to:\n\n" + processedFramesDir);
        } catch (final Exception e) {
            Log.e(TAG, "Processing failed", e);
            String message = (e.getMessage() != null) ? e.getMessage() : "An unknown error occurred.";
            showErrorDialog("Processing Error", message, true);
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
        updateStatus("Preparing image...", true);
        copyFile(new File(path), new File(rawFramesDir, "frame_00000.png"));
        File[] frames = new File(rawFramesDir).listFiles();
        if (frames != null) {
            sortFrames(frames);
        }
        return frames;
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
        // This is the full, uncompressed try-with-resources block.
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
