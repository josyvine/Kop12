package com.kop.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.MediaViewHolder> {

    private final Context context;
    private List<MediaItem> mediaItems;
    private OnMediaSelectedListener listener;
    private OnFolderClickedListener folderListener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    // --- NEW: Variables for multi-selection ---
    private final boolean isMultiSelectMode;
    private final Set<MediaItem> selectedItems = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnMediaSelectedListener {
        void onMediaSelected(File file);
    }

    public interface OnFolderClickedListener {
        void onFolderClicked(File file);
    }

    // --- UPDATED CONSTRUCTOR ---
    public MediaPickerAdapter(Context context, List<MediaItem> mediaItems, OnMediaSelectedListener listener, OnFolderClickedListener folderListener, boolean isMultiSelectMode) {
        this.context = context;
        this.mediaItems = mediaItems;
        this.listener = listener;
        this.folderListener = folderListener;
        this.isMultiSelectMode = isMultiSelectMode;
    }

    public void updateData(List<MediaItem> newMediaItems) {
        this.mediaItems = newMediaItems;
        // When data is updated, clear selections that may no longer be relevant
        selectedItems.retainAll(new HashSet<>(newMediaItems));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media_browser, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaItem item = mediaItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return mediaItems != null ? mediaItems.size() : 0;
    }

    // --- NEW: Methods to manage selection state ---
    public int getSelectedCount() {
        return selectedItems.size();
    }

    public List<String> getSelectedFilePaths() {
        List<String> paths = new ArrayList<>();
        for (MediaItem item : selectedItems) {
            paths.add(item.getPath());
        }
        return paths;
    }

    public void clearSelection() {
        selectedItems.clear();
        notifyDataSetChanged();
    }


    class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        ImageView videoIcon;
        ImageView zipIcon; // NEW
        FrameLayout selectionOverlay;
        LinearLayout folderLayout;
        TextView folderName;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_media_thumbnail);
            videoIcon = itemView.findViewById(R.id.iv_video_icon);
            zipIcon = itemView.findViewById(R.id.iv_zip_icon); // NEW
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
            folderLayout = itemView.findViewById(R.id.folder_layout);
            folderName = itemView.findViewById(R.id.tv_folder_name);
        }

        void bind(final MediaItem item) {
            thumbnail.setVisibility(View.GONE);
            videoIcon.setVisibility(View.GONE);
            zipIcon.setVisibility(View.GONE); // NEW
            folderLayout.setVisibility(View.GONE);
            thumbnail.setImageBitmap(null);
            thumbnail.setTag(item.getPath());

            // --- UPDATED: Show selection state ---
            if (selectedItems.contains(item)) {
                selectionOverlay.setVisibility(View.VISIBLE);
            } else {
                selectionOverlay.setVisibility(View.GONE);
            }


            switch (item.getType()) {
                case FOLDER:
                case PARENT:
                    folderLayout.setVisibility(View.VISIBLE);
                    folderName.setText(item.getName());
                    itemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (folderListener != null) {
                                folderListener.onFolderClicked(item.getFile());
                            }
                        }
                    });
                    itemView.setOnLongClickListener(null);
                    break;

                case FILE:
                    thumbnail.setVisibility(View.VISIBLE);
                    thumbnail.setImageResource(android.R.color.darker_gray);

                    thumbnailExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            final String filePath = item.getPath();
                            final String lowerCasePath = filePath.toLowerCase();
                            final Bitmap bmp;
                            final boolean isVideo = isVideoFile(lowerCasePath);
                            final boolean isImage = isImageFile(lowerCasePath);
                            final boolean isZip = isZipFile(lowerCasePath);

                            if (isImage) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inSampleSize = 8;
                                bmp = BitmapFactory.decodeFile(filePath, options);
                            } else if (isVideo) {
                                bmp = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
                            } else {
                                bmp = null; // ZIP files won't have a thumbnail
                            }

                            if (thumbnail.getTag().equals(filePath)) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (bmp != null) {
                                            thumbnail.setImageBitmap(bmp);
                                        } else if (isZip) {
                                            thumbnail.setImageResource(R.drawable.ic_zip_file); // Use a specific icon for zip
                                        } else {
                                            thumbnail.setImageResource(android.R.drawable.ic_menu_report_image);
                                        }

                                        videoIcon.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                                        zipIcon.setVisibility(isZip ? View.VISIBLE : View.GONE);
                                    }
                                });
                            }
                        }
                    });

                    // --- UPDATED CLICK LOGIC ---
                    if (isMultiSelectMode) {
                        // In multi-select mode, a short click toggles selection
                        itemView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                toggleSelection(item);
                                if (listener != null) {
                                    // Notify listener so it can update the count in the title
                                    listener.onMediaSelected(null);
                                }
                            }
                        });
                        itemView.setOnLongClickListener(null); // No long click in this mode
                    } else {
                        // In single-select mode, a long click confirms selection
                        itemView.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                selectionOverlay.setVisibility(View.VISIBLE);
                                if (listener != null) {
                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            listener.onMediaSelected(item.getFile());
                                        }
                                    }, 100);
                                }
                                return true;
                            }
                        });
                        itemView.setOnClickListener(null);
                    }
                    break;
            }
        }

        private void toggleSelection(MediaItem item) {
            if (selectedItems.contains(item)) {
                selectedItems.remove(item);
                selectionOverlay.setVisibility(View.GONE);
            } else {
                selectedItems.add(item);
                selectionOverlay.setVisibility(View.VISIBLE);
            }
        }

        private boolean isImageFile(String path) {
            return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp");
        }

        private boolean isVideoFile(String path) {
            return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".3gp") || path.endsWith(".mkv");
        }

        private boolean isZipFile(String path) {
            return path.endsWith(".zip");
        }
    }
}