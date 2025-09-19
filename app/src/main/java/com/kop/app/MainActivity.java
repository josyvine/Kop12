package com.kop.app;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
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
    
    private MediaPickerDialogFragment mediaPickerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                        showMediaTypeChooserDialog();
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

    private void showMediaTypeChooserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Media Type");

        builder.setPositiveButton("Image", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                launchMediaPicker(MediaPickerActivity.MEDIA_TYPE_IMAGE);
            }
        });
        builder.setNegativeButton("Video", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                launchMediaPicker(MediaPickerActivity.MEDIA_TYPE_VIDEO);
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
