package com.kop.app;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_SELECT_CODE = 101;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize the OpenCV library
        OpenCVLoader.initDebug();
        
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
                    // Copy the selected file to a temporary location
                    File tempFile = copyUriToCache(this, uri);
                    // Launch the new ProcessingActivity and pass the file path to it
                    Intent processIntent = new Intent(this, ProcessingActivity.class);
                    processIntent.putExtra(ProcessingActivity.EXTRA_FILE_PATH, tempFile.getAbsolutePath());
                    startActivity(processIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Error preparing file for processing.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to copy file to cache", e);
                }
            }
        }
    }
    
    private File copyUriToCache(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new Exception("Could not open input stream for URI");
        }
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
