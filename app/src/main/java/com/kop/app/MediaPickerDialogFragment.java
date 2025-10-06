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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MediaPickerDialogFragment extends DialogFragment {

    public static final String MEDIA_TYPE_IMAGE = "image";
    public static final String MEDIA_TYPE_VIDEO = "video";
    public static final String MEDIA_TYPE_ZIP = "zip"; // NEW CONSTANT
    public static final String TAG = "MediaPickerDialog";
    private static final String ARG_MEDIA_TYPE = "media_type";

    private RecyclerView mediaGrid;
    private ProgressBar loadingIndicator;
    private TextView titleTextView, currentPathTextView, emptyFolderTextView;
    private MediaPickerAdapter adapter;
    private Button phoneStorageButton, sdCardButton, doneButton; // Added doneButton
    private EditText searchEditText;
    private Spinner filterSpinner;

    private String mediaType;
    private OnMediaSelectedListener mediaSelectedListener;

    private List<MediaItem> allItemsInCurrentDir = new ArrayList<>();
    private List<MediaItem> masterMediaList = new ArrayList<>();
    private boolean isBrowsingFolders = false;
    private boolean isMultiSelectMode = false; // NEW flag for multi-image selection

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // UPDATED INTERFACE to support single and multiple file selections
    public interface OnMediaSelectedListener {
        void onFileSelected(String filePath);
        void onFilesSelected(List<String> filePaths);
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
            isMultiSelectMode = MEDIA_TYPE_IMAGE.equals(mediaType); // Enable multi-select only for images
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

        initializeViews(view);

        if (isMultiSelectMode) {
            titleTextView.setText("Select Images (0)");
            doneButton.setVisibility(View.VISIBLE);
        } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
            titleTextView.setText("Select Video");
        } else if (MEDIA_TYPE_ZIP.equals(mediaType)) {
            titleTextView.setText("Select ZIP Archive");
        }


        setupRecyclerView();
        setupControls();

        loadAllMediaFiles();
    }

    private void initializeViews(View view) {
        mediaGrid = view.findViewById(R.id.rv_media_grid);
        loadingIndicator = view.findViewById(R.id.pb_loading);
        titleTextView = view.findViewById(R.id.tv_picker_title);
        currentPathTextView = view.findViewById(R.id.tv_current_path);
        emptyFolderTextView = view.findViewById(R.id.tv_empty_folder);
        phoneStorageButton = view.findViewById(R.id.btn_phone_storage);
        sdCardButton = view.findViewById(R.id.btn_sd_card);
        searchEditText = view.findViewById(R.id.et_search);
        filterSpinner = view.findViewById(R.id.spinner_filter);
        doneButton = view.findViewById(R.id.btn_done_picker);
    }

    public void setOnMediaSelectedListener(OnMediaSelectedListener listener) {
        this.mediaSelectedListener = listener;
    }

    private void setupRecyclerView() {
        adapter = new MediaPickerAdapter(getContext(), new ArrayList<MediaItem>(),
            new MediaPickerAdapter.OnMediaSelectedListener() {
                @Override
                public void onMediaSelected(File file) {
                    if (isMultiSelectMode) {
                        // In multi-select, this is handled by the adapter's click listener
                        // Update the title with the selection count
                        int count = adapter.getSelectedCount();
                        titleTextView.setText("Select Images (" + count + ")");
                    } else {
                        // Single select mode (Video, ZIP)
                        if (mediaSelectedListener != null) {
                            mediaSelectedListener.onFileSelected(file.getAbsolutePath());
                            dismiss();
                        }
                    }
                }
            },
            new MediaPickerAdapter.OnFolderClickedListener() {
                @Override
                public void onFolderClicked(File file) {
                    navigateTo(file);
                }
            }, isMultiSelectMode); // Pass multi-select flag to adapter

        mediaGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        mediaGrid.setAdapter(adapter);
    }

    private void setupControls() {
        phoneStorageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isBrowsingFolders = true;
                navigateTo(Environment.getExternalStorageDirectory());
            }
        });

        sdCardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File sdCard = getSdCardPath();
                if (sdCard != null) {
                    isBrowsingFolders = true;
                    navigateTo(sdCard);
                } else {
                    Toast.makeText(getContext(), "SD Card not found.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        titleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBrowsingFolders) {
                    loadAllMediaFiles();
                }
            }
        });

        ImageButton closeButton = getView().findViewById(R.id.btn_close_picker);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaSelectedListener != null) {
                    List<String> selectedPaths = adapter.getSelectedFilePaths();
                    if (selectedPaths.isEmpty()) {
                        Toast.makeText(getContext(), "Please select at least one image.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mediaSelectedListener.onFilesSelected(selectedPaths);
                    dismiss();
                }
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
            }
            @Override
            public void afterTextChanged(final Editable s) {
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        filterMediaByName(s.toString());
                    }
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.media_filter_options, android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(filterAdapter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilterAndSort();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void navigateTo(File directory) {
        if (directory == null) {
            return;
        }
        updateUiForFolderBrowsing(directory);
        loadDirectoryContents(directory);
    }

    private void updateUiForAllMediaView() {
        isBrowsingFolders = false;
        filterSpinner.setVisibility(View.VISIBLE);
        currentPathTextView.setVisibility(View.GONE);
        String title = "All Media";
        if (isMultiSelectMode) {
            title = "Select Images (" + adapter.getSelectedCount() + ")";
        } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
            title = "All Videos";
        } else if (MEDIA_TYPE_ZIP.equals(mediaType)) {
            title = "All ZIP Archives";
        }
        titleTextView.setText(title);
    }

    private void updateUiForFolderBrowsing(File directory) {
        isBrowsingFolders = true;
        filterSpinner.setVisibility(View.GONE);
        currentPathTextView.setVisibility(View.VISIBLE);
        currentPathTextView.setText(directory.getAbsolutePath());
        String title = "Browse";
         if (isMultiSelectMode) {
            title = "Select Images (" + adapter.getSelectedCount() + ")";
        } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
            title = "Select Video";
        } else if (MEDIA_TYPE_ZIP.equals(mediaType)) {
            title = "Select ZIP Archive";
        }
        titleTextView.setText(title);
    }

    private void loadAllMediaFiles() {
        updateUiForAllMediaView();
        loadingIndicator.setVisibility(View.VISIBLE);
        mediaGrid.setVisibility(View.GONE);
        emptyFolderTextView.setVisibility(View.GONE);
        searchEditText.setText("");
        adapter.clearSelection(); // Clear selections when reloading

        new Thread(new Runnable() {
            @Override
            public void run() {
                masterMediaList.clear();
                List<File> storageRoots = new ArrayList<>();
                storageRoots.add(Environment.getExternalStorageDirectory());
                File sdCard = getSdCardPath();
                if (sdCard != null) {
                    storageRoots.add(sdCard);
                }

                for (File root : storageRoots) {
                    scanDirectoryForMedia(root, masterMediaList);
                }

                Collections.sort(masterMediaList, new Comparator<MediaItem>() {
                    @Override
                    public int compare(MediaItem o1, MediaItem o2) {
                        return Long.compare(o2.getFile().lastModified(), o1.getFile().lastModified());
                    }
                });

                allItemsInCurrentDir.clear();
                allItemsInCurrentDir.addAll(masterMediaList);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        filterSpinner.setSelection(0, false);
                        adapter.updateData(allItemsInCurrentDir);
                        loadingIndicator.setVisibility(View.GONE);
                        if (allItemsInCurrentDir.isEmpty()) {
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

    private void scanDirectoryForMedia(File directory, List<MediaItem> mediaList) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!file.getName().startsWith(".") && !file.getName().equalsIgnoreCase("Android")) {
                        scanDirectoryForMedia(file, mediaList);
                    }
                } else {
                    if (isTargetMediaFile(file)) {
                        mediaList.add(new MediaItem(file, MediaItem.ItemType.FILE));
                    }
                }
            }
        }
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

                if (!isRoot(directory)) {
                    items.add(new MediaItem(directory.getParentFile(), MediaItem.ItemType.PARENT));
                }

                if (files != null) {
                    List<File> fileList = new ArrayList<>();
                    for(File file : files) {
                        if (!file.getName().startsWith(".")) {
                           fileList.add(file);
                        }
                    }
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
                        if (items.isEmpty() || (items.size() == 1 && items.get(0).getType() == MediaItem.ItemType.PARENT)) {
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

    private void applyFilterAndSort() {
        if (isBrowsingFolders || masterMediaList.isEmpty()) {
            return;
        }

        int selectedPosition = filterSpinner.getSelectedItemPosition();
        List<MediaItem> filteredList = new ArrayList<>(masterMediaList);

        switch (selectedPosition) {
            case 0: // Newest First
                Collections.sort(filteredList, new Comparator<MediaItem>() {
                    @Override
                    public int compare(MediaItem o1, MediaItem o2) {
                        return Long.compare(o2.getFile().lastModified(), o1.getFile().lastModified());
                    }
                });
                break;
            case 1: // Oldest First
                Collections.sort(filteredList, new Comparator<MediaItem>() {
                    @Override
                    public int compare(MediaItem o1, MediaItem o2) {
                        return Long.compare(o1.getFile().lastModified(), o2.getFile().lastModified());
                    }
                });
                break;
            case 2: // Largest First
                Collections.sort(filteredList, new Comparator<MediaItem>() {
                    @Override
                    public int compare(MediaItem o1, MediaItem o2) {
                        return Long.compare(o2.getFile().length(), o1.getFile().length());
                    }
                });
                break;
            case 3: // Phone Storage Only
                String phonePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                List<MediaItem> phoneOnlyList = new ArrayList<>();
                for (MediaItem item : filteredList) {
                    if (item.getPath().startsWith(phonePath)) {
                        phoneOnlyList.add(item);
                    }
                }
                filteredList = phoneOnlyList;
                break;
            case 4: // SD Card Only
                File sdCard = getSdCardPath();
                if (sdCard != null) {
                    String sdPath = sdCard.getAbsolutePath();
                    List<MediaItem> sdOnlyList = new ArrayList<>();
                    for (MediaItem item : filteredList) {
                        if (item.getPath().startsWith(sdPath)) {
                            sdOnlyList.add(item);
                        }
                    }
                    filteredList = sdOnlyList;
                } else {
                    Toast.makeText(getContext(), "SD Card not found.", Toast.LENGTH_SHORT).show();
                    filteredList.clear();
                }
                break;
        }

        allItemsInCurrentDir.clear();
        allItemsInCurrentDir.addAll(filteredList);
        filterMediaByName(searchEditText.getText().toString());
    }

    private void filterMediaByName(String query) {
        final String lowerCaseQuery = query.toLowerCase(Locale.US);
        List<MediaItem> finalList = new ArrayList<>();

        if (query.isEmpty()) {
            finalList.addAll(allItemsInCurrentDir);
        } else {
            for (MediaItem item : allItemsInCurrentDir) {
                if (item.getType() == MediaItem.ItemType.PARENT) {
                    finalList.add(item);
                    continue;
                }
                if (item.getName().toLowerCase(Locale.US).contains(lowerCaseQuery)) {
                    finalList.add(item);
                }
            }
        }

        adapter.updateData(finalList);

        if (finalList.isEmpty()) {
            emptyFolderTextView.setVisibility(View.VISIBLE);
            mediaGrid.setVisibility(View.GONE);
        } else {
            emptyFolderTextView.setVisibility(View.GONE);
            mediaGrid.setVisibility(View.VISIBLE);
        }
    }

    private boolean isTargetMediaFile(File file) {
        String lowerCasePath = file.getName().toLowerCase(Locale.US);
        if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
            return lowerCasePath.endsWith(".jpg") || lowerCasePath.endsWith(".jpeg") || lowerCasePath.endsWith(".png") || lowerCasePath.endsWith(".webp");
        } else if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
            return lowerCasePath.endsWith(".mp4") || lowerCasePath.endsWith(".mov") || lowerCasePath.endsWith(".3gp") || lowerCasePath.endsWith(".mkv");
        } else if (MEDIA_TYPE_ZIP.equals(mediaType)) {
            return lowerCasePath.endsWith(".zip");
        }
        return false;
    }

    private File getSdCardPath() {
        File[] externalDirs = ContextCompat.getExternalFilesDirs(requireContext(), null);
        for (File dir : externalDirs) {
            if (dir != null && Environment.isExternalStorageRemovable(dir)) {
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