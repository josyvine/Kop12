package com.kop.app;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog; 
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private WebView webView;

    // The modern way to handle results from activities we launch.
    private ActivityResultLauncher<Intent> mediaPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        OpenCVLoader.initDebug();
        
        setContentView(R.layout.activity_main);

        // Register the launcher to handle the result from MediaPickerActivity.
        mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // We got a file path back from our picker.
                        String filePath = result.getData().getStringExtra(MediaPickerActivity.EXTRA_SELECTED_FILE_PATH);
                        if (filePath != null && !filePath.isEmpty()) {
                            // Launch the ProcessingActivity with the selected file path.
                            Intent processIntent = new Intent(MainActivity.this, ProcessingActivity.class);
                            processIntent.putExtra(ProcessingActivity.EXTRA_FILE_PATH, filePath);
                            startActivity(processIntent);
                        }
                    }
                }
            });

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
            // Run on the UI thread to show dialogs.
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
        public void clearCache() { showToast("Cache clearing not yet implemented."); }
        @JavascriptInterface
        public void openContactForm() { showToast("Contact form not yet implemented."); }
        @JavascriptInterface
        public void setTheme(String themeName) { showToast("Theme switching requires an app restart."); }
    }
    
    // This method shows the "Image or Video?" dialog you requested.
    private void showMediaTypeChooserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Media Type");

        // Set up the buttons
        builder.setPositiveButton("Image", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(MainActivity.this, MediaPickerActivity.class);
                intent.putExtra(MediaPickerActivity.EXTRA_MEDIA_TYPE, MediaPickerActivity.MEDIA_TYPE_IMAGE);
                mediaPickerLauncher.launch(intent);
            }
        });
        builder.setNegativeButton("Video", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(MainActivity.this, MediaPickerActivity.class);
                intent.putExtra(MediaPickerActivity.EXTRA_MEDIA_TYPE, MediaPickerActivity.MEDIA_TYPE_VIDEO);
                mediaPickerLauncher.launch(intent);
            }
        });
        builder.setNeutralButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
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
                showMediaTypeChooserDialog();
            } else {
                Toast.makeText(this, "Storage permission is required to select files.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
