package com.kop.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import android.graphics.Matrix;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;

public class ProcessingActivity extends AppCompatActivity {

    private static final String TAG = "ProcessingActivity";
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    private static final int FPS = 12;

    private ImageView mainDisplay;
    private TextView statusTextView;
    private ProgressBar progressBar;
    private Handler uiHandler;

    private File[] rawFrames;
    private int currentFrameIndex = 0;
    private String processedFramesDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        mainDisplay = findViewById(R.id.iv_main_display);
        statusTextView = findViewById(R.id.tv_status);
        progressBar = findViewById(R.id.progress_bar);
        uiHandler = new Handler(Looper.getMainLooper());

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_FILE_PATH)) {
            String inputFilePath = intent.getStringExtra(EXTRA_FILE_PATH);
            prepareAndStartProcessing(inputFilePath);
        } else {
            showErrorDialog("Error", "No input file path provided.", true);
        }
    }

    private void prepareAndStartProcessing(final String inputFilePath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // STEP 1: Create project directories
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                    File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Project_" + timestamp);
                    if (!projectDir.exists()) projectDir.mkdirs();

                    String rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
                    processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
                    new File(rawFramesDir).mkdirs();
                    new File(processedFramesDir).mkdirs();

                    // STEP 2: Extract frames from video or prepare single image
                    updateStatus("Preparing files...", true);
                    String fileExtension = inputFilePath.substring(inputFilePath.lastIndexOf(".")).toLowerCase();
                    if (fileExtension.matches(".(mp4|mov|3gp|mkv|webm)$")) {
                        updateStatus("Extracting video frames...", true);
                        FrameExtractor.extractFrames(inputFilePath, rawFramesDir, FPS);
                    } else {
                        updateStatus("Preparing image...", true);
                        copyFile(new File(inputFilePath), new File(rawFramesDir, "frame_00000.png"));
                    }

                    rawFrames = new File(rawFramesDir).listFiles();
                    if (rawFrames == null || rawFrames.length == 0) {
                        throw new Exception("No frames were extracted or found.");
                    }
                    
                    Arrays.sort(rawFrames, new Comparator<File>() {
                        @Override
                        public int compare(File f1, File f2) {
                            return f1.getName().compareTo(f2.getName());
                        }
                    });

                    // STEP 3: Start the sequential, asynchronous processing chain.
                    currentFrameIndex = 0;
                    processNextFrame();

                } catch (final Exception e) {
                    Log.e(TAG, "Preparation failed", e);
                    String message = (e.getMessage() != null) ? e.getMessage() : "An unknown error occurred during preparation.";
                    showErrorDialog("Preparation Error", message, true);
                }
            }
        }).start();
    }
    
    private void processNextFrame() {
        if (currentFrameIndex >= rawFrames.length) {
            showSuccessDialog("Processing Complete", "Your rotoscoped frames have been saved to:\n\n" + processedFramesDir);
            return;
        }

        File frameFile = rawFrames[currentFrameIndex];
        final int frameNum = currentFrameIndex + 1;
        updateStatus("Processing frame " + frameNum + " of " + rawFrames.length, false);
        updateProgress(frameNum, rawFrames.length);

        try {
            final Bitmap orientedBitmap = decodeAndRotateBitmap(frameFile.getAbsolutePath());
            if (orientedBitmap == null) {
                currentFrameIndex++;
                processNextFrame();
                return;
            }

            // FIX: Call the new asynchronous ImageProcessor with a listener callback.
            ImageProcessor.extractOutline(orientedBitmap, new ImageProcessor.OutlineExtractionListener() {
                @Override
                public void onOutlineExtracted(Bitmap resultBitmap) {
                    updateMainDisplay(resultBitmap);

                    // Save the result.
                    final String outPath = new File(processedFramesDir, String.format("processed_%05d.png", currentFrameIndex)).getAbsolutePath();
                    try {
                        ImageProcessor.saveBitmap(resultBitmap, outPath);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save frame: " + outPath, e);
                    }

                    orientedBitmap.recycle();
                    
                    // Move to the next frame
                    currentFrameIndex++;
                    processNextFrame();
                }

                @Override
                public void onExtractionFailed(Exception e) {
                    Log.e(TAG, "Failed to process frame " + currentFrameIndex, e);
                    orientedBitmap.recycle();
                    currentFrameIndex++;
                    processNextFrame();
                }
            });

        } catch (IOException e) {
            Log.e(TAG, "Failed to read frame file", e);
            currentFrameIndex++;
            processNextFrame();
        }
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
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }
}
