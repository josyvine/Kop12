package com.kop.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MediaPickerActivity extends AppCompatActivity implements MediaPickerAdapter.OnMediaSelectedListener {

    public static final String EXTRA_MEDIA_TYPE = "extra_media_type";
    public static final String EXTRA_SELECTED_FILE_PATH = "extra_selected_file_path";
    public static final String MEDIA_TYPE_IMAGE = "image";
    public static final String MEDIA_TYPE_VIDEO = "video";

    private RecyclerView mediaGrid;
    private ProgressBar loadingIndicator;
    private TextView titleTextView;
    private MediaPickerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_picker);

        mediaGrid = findViewById(R.id.rv_media_grid);
        loadingIndicator = findViewById(R.id.pb_loading);
        titleTextView = findViewById(R.id.tv_picker_title);

        // Determine whether to load images or videos from the intent.
        String mediaType = getIntent().getStringExtra(EXTRA_MEDIA_TYPE);
        if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
            titleTextView.setText("Select an Image");
            loadMediaAsync(MEDIA_TYPE_IMAGE);
        } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
            titleTextView.setText("Select a Video");
            loadMediaAsync(MEDIA_TYPE_VIDEO);
        }
    }

    private void loadMediaAsync(final String mediaType) {
        loadingIndicator.setVisibility(View.VISIBLE);
        mediaGrid.setVisibility(View.GONE);

        // File scanning must be done on a background thread to avoid freezing the app.
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<File> foundFiles = new ArrayList<>();
                // Start scanning from the root of the external storage.
                File root = Environment.getExternalStorageDirectory();
                scanDirectoryForMedia(root, foundFiles, mediaType);

                // Sort files by date, newest first, for a better user experience.
                Collections.sort(foundFiles, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    }
                });

                // Switch back to the UI thread to update the RecyclerView.
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        setupRecyclerView(foundFiles);
                        loadingIndicator.setVisibility(View.GONE);
                        mediaGrid.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    private void scanDirectoryForMedia(File directory, List<File> fileList, String mediaType) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories.
                scanDirectoryForMedia(file, fileList, mediaType);
            } else {
                // Check if the file matches the desired media type.
                String lowerCasePath = file.getAbsolutePath().toLowerCase(Locale.US);
                if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
                    if (lowerCasePath.endsWith(".jpg") || lowerCasePath.endsWith(".jpeg") || lowerCasePath.endsWith(".png") || lowerCasePath.endsWith(".webp")) {
                        fileList.add(file);
                    }
                } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
                    if (lowerCasePath.endsWith(".mp4") || lowerCasePath.endsWith(".mov") || lowerCasePath.endsWith(".3gp") || lowerCasePath.endsWith(".mkv")) {
                        fileList.add(file);
                    }
                }
            }
        }
    }

    private void setupRecyclerView(List<File> files) {
        adapter = new MediaPickerAdapter(this, files, this);
        // Using a GridLayoutManager to display items in a grid. '3' is the number of columns.
        mediaGrid.setLayoutManager(new GridLayoutManager(this, 3));
        mediaGrid.setAdapter(adapter);
    }

    // This method is called by the adapter when the user long-presses a file.
    @Override
    public void onMediaSelected(File file) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SELECTED_FILE_PATH, file.getAbsolutePath());
        setResult(RESULT_OK, resultIntent);
        finish(); // Close this activity and return to MainActivity.
    }
}
