package com.kop.app;  

import androidx.appcompat.app.AlertDialog; 
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 101; // New code for camera
    private WebView webView;

    private MediaPickerDialogFragment mediaPickerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OpenCVLoader.initDebug();

        // This new code block will run in the background to copy the ffmpeg binary
        // from your project's assets folder to a place where the app can execute it.
        // This only runs once when the app is first installed or updated.
        new Thread(new Runnable() {
            @Override
            public void run() {
                setupFFmpeg();
            }
        }).start();

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        setupWebView();

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

    /**
     * Copies the ffmpeg binary from the assets folder to the app's private
     * files directory and makes it executable.
     */
    private void setupFFmpeg() {
        File ffmpegFile = new File(getFilesDir(), "ffmpeg");
        if (ffmpegFile.exists()) {
            Log.d(TAG, "ffmpeg executable already exists.");
            // If it exists, ensure it is executable, as permissions can be lost.
            if (!ffmpegFile.canExecute()) {
                ffmpegFile.setExecutable(true);
            }
            return;
        }

        try {
            // The path here must match the path in your assets folder exactly.
            String ffmpegAssetPath = "arm64-v8a/ffmpeg";
            
            try (InputStream in = getAssets().open(ffmpegAssetPath);
                 FileOutputStream out = new FileOutputStream(ffmpegFile)) {

                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            if (!ffmpegFile.setExecutable(true)) {
                Log.e(TAG, "Failed to set ffmpeg as executable.");
            } else {
                Log.d(TAG, "ffmpeg executable has been copied and set as executable.");
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy ffmpeg executable. Make sure the file exists at: app/src/main/assets/" + "arm64-v8a/ffmpeg", e);
        }
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
                        showMediaTypeChooserDialog();
                    } else {
                        requestPermissions();
                    }
                }
            });
        }

        @JavascriptInterface
        public void startCamera() {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (checkCameraPermission()) {
                        // *** THIS IS THE MODIFIED LOGIC ***
                        // Launch the new GPU-accelerated CameraFilterActivity instead of the old dialog.
                        Intent intent = new Intent(MainActivity.this, CameraFilterActivity.class);
                        startActivity(intent);
                    } else {
                        requestCameraPermission();
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

    private void showMediaTypeChooserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Media Type");

        builder.setPositiveButton("Image", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // FIX: Reference the constant from its new home in MediaPickerDialogFragment
                launchMediaPicker(MediaPickerDialogFragment.MEDIA_TYPE_IMAGE);
            }
        });
        builder.setNegativeButton("Video", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // FIX: Reference the constant from its new home in MediaPickerDialogFragment
                launchMediaPicker(MediaPickerDialogFragment.MEDIA_TYPE_VIDEO);
            }
        });
        builder.setNeutralButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void launchMediaPicker(String mediaType) {
        mediaPickerDialog = MediaPickerDialogFragment.newInstance(mediaType);
        mediaPickerDialog.setOnMediaSelectedListener(new MediaPickerDialogFragment.OnMediaSelectedListener() {
            @Override
            public void onFileSelected(String filePath) {
                // Hide the media picker
                if (mediaPickerDialog != null && mediaPickerDialog.isAdded()) {
                    getSupportFragmentManager().beginTransaction().hide(mediaPickerDialog).commit();
                }
                // Launch the processing dialog
                launchProcessingDialog(filePath);
            }
        });
        mediaPickerDialog.show(getSupportFragmentManager(), MediaPickerDialogFragment.TAG);
    }

    private void launchProcessingDialog(String filePath) {
        ProcessingDialogFragment processingDialog = ProcessingDialogFragment.newInstance(filePath);
        processingDialog.setOnDialogClosedListener(new ProcessingDialogFragment.OnDialogClosedListener() {
            @Override
            public void onDialogClosed() {
                // When the processing dialog is closed, show the media picker again
                if (mediaPickerDialog != null) {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    Fragment prev = getSupportFragmentManager().findFragmentByTag(ProcessingDialogFragment.TAG);
                    if (prev != null) {
                        ft.remove(prev);
                    }
                    ft.show(mediaPickerDialog);
                    ft.commit();
                }
            }
        });
        processingDialog.show(getSupportFragmentManager(), ProcessingDialogFragment.TAG);
    }

    // *** THIS METHOD IS NOW OBSOLETE AND HAS BEEN REMOVED ***
    // private void launchCameraDialog() {
    //     CameraDialogFragment cameraDialog = CameraDialogFragment.newInstance();
    //     cameraDialog.show(getSupportFragmentManager(), "CameraDialogFragment");
    // }

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

    private boolean checkCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showMediaTypeChooserDialog();
            } else {
                Toast.makeText(this, "Storage permission is required to select files.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // *** THIS IS THE MODIFIED LOGIC ***
                // After permission is granted, launch the new activity
                Intent intent = new Intent(MainActivity.this, CameraFilterActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
