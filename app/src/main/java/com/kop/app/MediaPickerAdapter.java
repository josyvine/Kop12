package com.kop.app;  

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils; 
import android.os.Handler;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.MediaViewHolder> {

    private final Context context;
    private List<MediaItem> mediaItems;
    private OnMediaSelectedListener listener;
    private OnFolderClickedListener folderListener;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(4);

    public interface OnMediaSelectedListener {
        void onMediaSelected(File file);
    }

    public interface OnFolderClickedListener {
        void onFolderClicked(File file);
    }

    public MediaPickerAdapter(Context context, List<MediaItem> mediaItems, OnMediaSelectedListener listener, OnFolderClickedListener folderListener) {
        this.context = context;
        this.mediaItems = mediaItems;
        this.listener = listener;
        this.folderListener = folderListener;
    }

    public void updateData(List<MediaItem> newMediaItems) {
        this.mediaItems = newMediaItems;
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

    class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        ImageView videoIcon;
        FrameLayout selectionOverlay;
        LinearLayout folderLayout;
        TextView folderName;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_media_thumbnail);
            videoIcon = itemView.findViewById(R.id.iv_video_icon);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
            folderLayout = itemView.findViewById(R.id.folder_layout);
            folderName = itemView.findViewById(R.id.tv_folder_name);
        }

        void bind(final MediaItem item) {
            // Reset visibility for all views
            thumbnail.setVisibility(View.GONE);
            videoIcon.setVisibility(View.GONE);
            folderLayout.setVisibility(View.GONE);
            selectionOverlay.setVisibility(View.GONE);
            thumbnail.setImageBitmap(null); // Clear previous image
            thumbnail.setTag(item.getPath()); // Tag the view with the unique path

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
                    // Set a placeholder while the thumbnail loads
                    thumbnail.setImageResource(android.R.color.darker_gray);

                    thumbnailExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            final String filePath = item.getPath();
                            final String lowerCasePath = filePath.toLowerCase();
                            final Bitmap bmp;

                            if (isImageFile(lowerCasePath)) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inSampleSize = 8;
                                bmp = BitmapFactory.decodeFile(filePath, options);
                            } else if (isVideoFile(lowerCasePath)) {
                                bmp = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
                            } else {
                                bmp = null;
                            }

                            // Check if the view hasn't been recycled for another item
                            if (thumbnail.getTag().equals(filePath)) {
                                thumbnail.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (bmp != null) {
                                            thumbnail.setImageBitmap(bmp);
                                            if (isVideoFile(lowerCasePath)) {
                                                videoIcon.setVisibility(View.VISIBLE);
                                            } else {
                                                videoIcon.setVisibility(View.GONE);
                                            }
                                        } else {
                                            // Set a default icon if thumbnail creation fails
                                            thumbnail.setImageResource(android.R.drawable.ic_menu_report_image);
                                            videoIcon.setVisibility(View.GONE);
                                        }
                                    }
                                });
                            }
                        }
                    });

                    // A long click selects the file for processing.
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
                    break;
            }
        }

        private boolean isImageFile(String path) {
            return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp");
        }

        private boolean isVideoFile(String path) {
            return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".3gp") || path.endsWith(".mkv");
        }
    }
}
