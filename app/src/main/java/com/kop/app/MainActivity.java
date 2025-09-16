package com.kop.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_SELECT_CODE = 101;
    private static final int FPS = 12;

    private WebView webView;
    private ProgressDialog progressDialog;

    private Segmenter segmenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        OpenCVLoader.initDebug();
        
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        setupWebView();
        initializeSegmenter();

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        
        webView.setBackgroundColor(0x00000000);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
    }

    private void initializeSegmenter() {
        SelfieSegmenterOptions options =
			new SelfieSegmenterOptions.Builder()
			.setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE) // More accurate for single images
			.enableRawSizeMask() // Improves mask quality
			.build();

        segmenter = Segmentation.getClient(options);
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void startFileProcessing() {
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
        return true;
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
                openFilePicker();
            } else {
                Toast.makeText(this, "Storage permission is required to select files.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
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
        progressDialog.setMessage("Starting...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
						File projectDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "kop/Project_" + timestamp);
						if (!projectDir.exists()) projectDir.mkdirs();

						String rawFramesDir = new File(projectDir, "raw_frames").getAbsolutePath();
						String processedFramesDir = new File(projectDir, "processed_frames").getAbsolutePath();
						new File(rawFramesDir).mkdirs();
						new File(processedFramesDir).mkdirs();

						String fileExtension = inputFilePath.substring(inputFilePath.lastIndexOf(".")).toLowerCase();

						if (fileExtension.matches(".(mp4|mov|3gp|mkv|webm)$")) {
							updateProgress("Extracting frames...", 0, 1);
							FrameExtractor.extractFrames(inputFilePath, rawFramesDir, FPS);
						} else if (fileExtension.matches(".(jpg|jpeg|png|webp)$")) {
							updateProgress("Preparing image...", 0, 1);
							copyFile(new File(inputFilePath), new File(rawFramesDir, "frame_00000.jpg"));
						} else {
							copyFile(new File(inputFilePath), new File(rawFramesDir, "frame_00000.jpg"));
						}

						File[] rawFrames = new File(rawFramesDir).listFiles();
						if (rawFrames == null || rawFrames.length == 0) {
							throw new Exception("No frames were extracted or found.");
						}

						final int totalFrames = rawFrames.length;
						progressDialog.setMax(totalFrames);

						for (int i = 0; i < rawFrames.length; i++) {
							final int frameNum = i + 1;
							updateProgress("Processing frame " + frameNum + " of " + totalFrames, frameNum, totalFrames);

							File frameFile = rawFrames[i];
							Bitmap originalBitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath());
							if (originalBitmap == null) continue;

							InputImage image = InputImage.fromBitmap(originalBitmap, 0);
							SegmentationMask mask = Tasks.await(segmenter.process(image));
							Bitmap processedBitmap = ImageProcessor.extractOutlineFromMask(originalBitmap, mask);

							String outPath = new File(processedFramesDir, String.format("processed_%05d.png", i)).getAbsolutePath();
							ImageProcessor.saveBitmap(processedBitmap, outPath);

							originalBitmap..recycle();
							if (processedBitmap != null) {
								processedBitmap.recycle();
							}
						}

						dismissProgress();
						showSuccessDialog("Processing Complete", "Your rotoscoped frames have been saved to:\n\n" + processedFramesDir);

					} catch (final Exception e) {
						Log.e(TAG, "Processing failed", e);
						dismissProgress();
						String message = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
						showErrorDialog("Processing Error", message);
					}
				}
			}).start();
    }

    private void updateProgress(final String message, final int progress, final int max) {
        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (progressDialog != null && progressDialog.isShowing()) {
						progressDialog.setMessage(message);
						if (progressDialog.isIndeterminate() == false) {
							progressDialog.setMax(max);
							progressDialog.setProgress(progress);
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

    private void copyFile(File source, File dest) throws Exception {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
        }
    }
    
    private File copyUriToCache(Context context, Uri uri) throws Exception {
        // FIX: Added parentheses to call the getContentResolver() method.
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("Could not open input stream for URI");
        }
        File tempFile = new File(context.getCacheDir(), "temp_processing_file");
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
    }

}
