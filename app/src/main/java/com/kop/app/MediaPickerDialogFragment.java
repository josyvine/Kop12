package com.kop.app;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MediaPickerDialogFragment extends DialogFragment {

    public static final String TAG = "MediaPickerDialog";
    private static final String ARG_MEDIA_TYPE = "media_type";

    private RecyclerView mediaGrid;
    private ProgressBar loadingIndicator;
    private TextView titleTextView, currentPathTextView, emptyFolderTextView;
    private MediaPickerAdapter adapter;
    private Button phoneStorageButton, sdCardButton;
    private EditText searchEditText;

    private File currentDirectory;
    private String mediaType;
    private OnMediaSelectedListener mediaSelectedListener;
    private List<MediaItem> allItemsInCurrentDir = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public interface OnMediaSelectedListener {
        void onFileSelected(String filePath);
    }

    public static MediaPickerDialogFragment newInstance(String mediaType) {
        MediaPickerDialogFragment fragment = new MediaPickerDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MEDIA_TYPE, mediaType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        if (getArguments() != null) {
            mediaType = getArguments().getString(ARG_MEDIA_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_media_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mediaGrid = view.findViewById(R.id.rv_media_grid);
        loadingIndicator = view.findViewById(R.id.pb_loading);
        titleTextView = view.findViewById(R.id.tv_picker_title);
        currentPathTextView = view.findViewById(R.id.tv_current_path);
        emptyFolderTextView = view.findViewById(R.id.tv_empty_folder);
        phoneStorageButton = view.findViewById(R.id.btn_phone_storage);
        sdCardButton = view.findViewById(R.id.btn_sd_card);
        searchEditText = view.findViewById(R.id.et_search);
        ImageButton closeButton = view.findViewById(R.id.btn_close_picker);

        titleTextView.setText("Select " + (MediaPickerActivity.MEDIA_TYPE_VIDEO.equals(mediaType) ? "Video" : "Image"));

        setupRecyclerView();

        phoneStorageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateTo(Environment.getExternalStorageDirectory());
            }
        });

        sdCardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File sdCard = getSdCardPath();
                if (sdCard != null) {
                    navigateTo(sdCard);
                } else {
                    Toast.makeText(getContext(), "SD Card not found.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
            }
            @Override
            public void afterTextChanged(final Editable s) {
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        filterMedia(s.toString());
                    }
                };
                searchHandler.postDelayed(searchRunnable, 300); // Debounce search
            }
        });

        navigateTo(Environment.getExternalStorageDirectory()); // Start at internal storage root
    }

    public void setOnMediaSelectedListener(OnMediaSelectedListener listener) {
        this.mediaSelectedListener = listener;
    }

    private void setupRecyclerView() {
        adapter = new MediaPickerAdapter(getContext(), new ArrayList<MediaItem>(),
            new MediaPickerAdapter.OnMediaSelectedListener() {
                @Override
                public void onMediaSelected(File file) {
                    if (mediaSelectedListener != null) {
                        mediaSelectedListener.onFileSelected(file.getAbsolutePath());
                    }
                }
            },
            new MediaPickerAdapter.OnFolderClickedListener() {
                @Override
                public void onFolderClicked(File file) {
                    navigateTo(file);
                }
            });
        mediaGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        mediaGrid.setAdapter(adapter);
    }

    private void navigateTo(File directory) {
        if (directory == null) {
            // This case handles the ".." when at the root
            return;
        }
        this.currentDirectory = directory;
        currentPathTextView.setText(directory.getAbsolutePath());
        searchEditText.setText(""); // Clear search when navigating
        loadDirectoryContents(directory);
    }

    private void loadDirectoryContents(final File directory) {
        loadingIndicator.setVisibility(View.VISIBLE);
        mediaGrid.setVisibility(View.GONE);
        emptyFolderTextView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                File[] files = directory.listFiles();
                final List<MediaItem> items = new ArrayList<>();

                // Add parent directory link ("..") if not at a root
                if (!isRoot(directory)) {
                    items.add(new MediaItem(directory.getParentFile(), MediaItem.ItemType.PARENT));
                }

                if (files != null) {
                    List<File> fileList = Arrays.asList(files);
                    Collections.sort(fileList, new Comparator<File>() {
                        @Override
                        public int compare(File f1, File f2) {
                            if (f1.isDirectory() && !f2.isDirectory()) {
                                return -1;
                            } else if (!f1.isDirectory() && f2.isDirectory()) {
                                return 1;
                            } else {
                                return f1.getName().compareToIgnoreCase(f2.getName());
                            }
                        }
                    });

                    for (File file : fileList) {
                        if (file.isDirectory()) {
                            items.add(new MediaItem(file, MediaItem.ItemType.FOLDER));
                        } else {
                            if (isTargetMediaFile(file)) {
                                items.add(new MediaItem(file, MediaItem.ItemType.FILE));
                            }
                        }
                    }
                }
                
                allItemsInCurrentDir.clear();
                allItemsInCurrentDir.addAll(items);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.updateData(items);
                        loadingIndicator.setVisibility(View.GONE);
                        if (items.isEmpty()) {
                            emptyFolderTextView.setVisibility(View.VISIBLE);
                            mediaGrid.setVisibility(View.GONE);
                        } else {
                            emptyFolderTextView.setVisibility(View.GONE);
                            mediaGrid.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }

    private void filterMedia(String query) {
        final String lowerCaseQuery = query.toLowerCase(Locale.US);
        List<MediaItem> filteredList = new ArrayList<>();

        if (query.isEmpty()) {
            filteredList.addAll(allItemsInCurrentDir);
        } else {
            for (MediaItem item : allItemsInCurrentDir) {
                if (item.getName().toLowerCase(Locale.US).contains(lowerCaseQuery)) {
                    filteredList.add(item);
                }
            }
        }
        
        adapter.updateData(filteredList);
        
        if (filteredList.isEmpty()) {
            emptyFolderTextView.setVisibility(View.VISIBLE);
            mediaGrid.setVisibility(View.GONE);
        } else {
            emptyFolderTextView.setVisibility(View.GONE);
            mediaGrid.setVisibility(View.VISIBLE);
        }
    }

    private boolean isTargetMediaFile(File file) {
        String lowerCasePath = file.getName().toLowerCase(Locale.US);
        if (MediaPickerActivity.MEDIA_TYPE_IMAGE.equals(mediaType)) {
            return lowerCasePath.endsWith(".jpg") || lowerCasePath.endsWith(".jpeg") || lowerCasePath.endsWith(".png") || lowerCasePath.endsWith(".webp");
        } else if (MediaPickerActivity.MEDIA_TYPE_VIDEO.equals(mediaType)) {
            return lowerCasePath.endsWith(".mp4") || lowerCasePath.endsWith(".mov") || lowerCasePath.endsWith(".3gp") || lowerCasePath.endsWith(".mkv");
        }
        return false;
    }

    private File getSdCardPath() {
        File[] externalDirs = ContextCompat.getExternalFilesDirs(requireContext(), null);
        for (File dir : externalDirs) {
            if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                // Path is like /storage/XXXX-XXXX/Android/data/com.kop.app/files
                // We need to go up to get /storage/XXXX-XXXX
                String path = dir.getAbsolutePath();
                int androidDataIndex = path.indexOf("/Android/data");
                if (androidDataIndex != -1) {
                    return new File(path.substring(0, androidDataIndex));
                }
            }
        }
        return null;
    }

    private boolean isRoot(File dir) {
        if (dir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            return true;
        }
        File sdCard = getSdCardPath();
        if (sdCard != null && dir.getAbsolutePath().equals(sdCard.getAbsolutePath())) {
            return true;
        }
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }
}
