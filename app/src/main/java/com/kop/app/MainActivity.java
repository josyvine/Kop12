package com.kop.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.exifinterface.media.ExifInterface;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_SELECT_CODE = 101;
    private static final int FPS = 12; // Frames per second to extract from video

    private WebView webView;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize the OpenCV library
        OpenCVLoader.initDebug();
        
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        setupWebView();
        
        // Load the local HTML file for the UI
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        
        // Make the WebView background transparent to see the native app theme behind it
        webView.setBackgroundColor(0x00000000);

        webView.setWebViewClient(new WebViewClient());
        // Add the JavascriptInterface so the HTML can call Java methods
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
    }

    // This class contains methods that can be called from JavaScript in the WebView
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void startFileProcessing() {
            // All UI interactions must be run on the main thread.
            // This Handler ensures the permission check and file picker are launched correctly.
            new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						if (checkPermissions()) {
							openFilePicker();
						} else {
							requestPermissions();
						}
					}
				});
        }

        @JavascriptInterface
        public void clearCache() {
            showToast("Cache clearing not yet implemented.");
        }

        @JavascriptInterface
        public void openContactForm() {
            showToast("Contact form not yet implemented.");
        }

        @JavascriptInterface
        public void setTheme(String themeName) {
            // In a real app, you would save this preference and restart the activity.
            showToast("Theme switching requires an app restart.");
        }
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
				}
			});
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int readPermission = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            int writePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permissions are granted at install time on older versions
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed to open file picker
                openFilePicker();
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Storage permission is required to select files.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Allow any file type
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Specify the mimetypes we are interested in (videos and images)
        String[] mimetypes = {"video/*", "image/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select a File to Process"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    // Copy the selected file to a temporary location in the app's cache
                    // to ensure we have direct file path access.
                    File tempFile = copyUriToCache(this, uri);
                    processFile(tempFile.getAbsolutePath());
                } catch (Exception e) {
                    showErrorDialog("File Error", "Could not copy the selected file. Please try a different file or file manager.");
                    Log.e(TAG, "Failed to copy file", e);
                }
            }
        }
    }

    private void processFile(final String inputFilePath) {
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("Processing File");
        progressDialog.setMessage("Preparing files...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Start a new background thread for the heavy processing to avoid freezing the UI.
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						// STEP 1: Create project directories for output files.
						String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
						File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Project_" + timestamp);
						if (!projectDir.exists()) projectDir.mkdirs();

						String rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
						String processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
						new File(rawFramesDir).mkdirs();
						new File(processedFramesDir).mkdirs();

						// STEP 2: Determine if the file is a video or image and extract frames.
						String fileExtension = inputFilePath.substring(inputFilePath.lastIndexOf(".")).toLowerCase();

						// Check for common video file extensions.
						if (fileExtension.matches(".(mp4|mov|3gp|mkv|webm)$")) {
							updateProgress("Extracting video frames...", 0, 1);
							FrameExtractor.extractFrames(inputFilePath, rawFramesDir, FPS);
						} else {
							// Assume it's an image.
							updateProgress("Preparing image for processing...", 0, 1);
							// FIX: Copy as a PNG to maintain consistency with video frames.
							copyFile(new File(inputFilePath), new File(rawFramesDir, "frame_00000.png"));
						}

						// STEP 3: Get the list of raw frames to process.
						File[] rawFrames = new File(rawFramesDir).listFiles();
						// Important check to ensure frames were actually extracted.
						if (rawFrames == null || rawFrames.length == 0) {
							throw new Exception("No frames were extracted or found. The file may be corrupt or an unsupported format.");
						}

						final int totalFrames = rawFrames.length;
						progressDialog.setMax(totalFrames);

						// STEP 4: Loop through each frame, process it, and save the result.
						for (int i = 0; i < rawFrames.length; i++) {
							final int frameNum = i + 1;
							updateProgress("Processing frame " + frameNum + " of " + totalFrames, frameNum, totalFrames);

							File frameFile = rawFrames[i];
							
							// Decode the bitmap and correct its orientation based on Exif data.
							Bitmap orientedBitmap = decodeAndRotateBitmap(frameFile.getAbsolutePath());
							if (orientedBitmap == null) continue; // Skip if bitmap could not be decoded.

							// This is where the new, high-quality outline extraction happens.
							Bitmap processedBitmap = ImageProcessor.extractOutline(orientedBitmap);

							// Save the final outlined image.
							String outPath = new File(processedFramesDir, String.format("processed_%05d.png", i)).getAbsolutePath();
							ImageProcessor.saveBitmap(processedBitmap, outPath);

							// IMPORTANT: Recycle bitmaps immediately to free up memory.
							orientedBitmap.recycle();
							if (processedBitmap != null) {
								processedBitmap.recycle();
							}
						}

						// STEP 5: Clean up and notify the user of success.
						dismissProgress();
						showSuccessDialog("Processing Complete", "Your rotoscoped frames have been saved to:\n\n" + processedFramesDir);

					} catch (final Exception e) {
						Log.e(TAG, "Processing failed", e);
						dismissProgress();
						String message = (e.getMessage() != null) ? e.getMessage() : "An unknown error occurred.";
						showErrorDialog("Processing Error", message);
					}
				}
			}).start();
    }
    
    // Decodes a bitmap from a file and rotates it according to its EXIF orientation tag.
    private Bitmap decodeAndRotateBitmap(String filePath) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) return null;

        ExifInterface exif = new ExifInterface(filePath);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                // No rotation needed, return the original bitmap.
                return bitmap;
        }

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle(); // Free the original bitmap's memory.
        return rotatedBitmap;
    }

    private void updateProgress(final String message, final int progress, final int max) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.setMessage(message);
                    if (max > 1) {
                         progressDialog.setIndeterminate(false);
                         progressDialog.setMax(max);
                         progressDialog.setProgress(progress);
                    } else {
                         progressDialog.setIndeterminate(true);
                    }
                }
            }
        });
    }

    private void dismissProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        });
    }

    private void showSuccessDialog(final String title, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
            }
        });
    }

    private void showErrorDialog(final String title, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            }
        });
    }
    
    // Utility function to copy a file from a source to a destination.
    private void copyFile(File source, File dest) throws Exception {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[4096]; // Use a larger buffer for faster copy
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }
    
    // Copies content from a URI (from the file picker) to a temporary file in the app's cache.
    private File copyUriToCache(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("Could not open input stream for URI");
        }
        // Use a more descriptive temporary file name
        File tempFile = new File(context.getCacheDir(), "processing_input_file.tmp");
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
    }
}
