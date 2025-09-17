package com.kop.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class MediaPickerAdapter extends RecyclerView.Adapter<MediaPickerAdapter.MediaViewHolder> {

    private final Context context;
    private final List<File> mediaFiles;
    private OnMediaSelectedListener listener;

    // Interface to communicate back to the Activity when a file is selected.
    public interface OnMediaSelectedListener {
        void onMediaSelected(File file);
    }

    public MediaPickerAdapter(Context context, List<File> mediaFiles, OnMediaSelectedListener listener) {
        this.context = context;
        this.mediaFiles = mediaFiles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        File file = mediaFiles.get(position);
        holder.bind(file);
    }

    @Override
    public int getItemCount() {
        return mediaFiles.size();
    }

    // The ViewHolder class holds the UI elements for a single grid item.
    class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        ImageView videoIcon;
        FrameLayout selectionOverlay;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_media_thumbnail);
            videoIcon = itemView.findViewById(R.id.iv_video_icon);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
        }

        void bind(final File file) {
            // Reset view state
            thumbnail.setImageBitmap(null);
            videoIcon.setVisibility(View.GONE);
            selectionOverlay.setVisibility(View.GONE);

            // Determine if it's an image or video and load the appropriate thumbnail
            String filePath = file.getAbsolutePath();
            String lowerCasePath = filePath.toLowerCase();

            if (isImageFile(lowerCasePath)) {
                // For images, load a down-sampled bitmap to save memory.
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 8; // Load 1/8th of the resolution
                Bitmap bmp = BitmapFactory.decodeFile(filePath, options);
                thumbnail.setImageBitmap(bmp);
                videoIcon.setVisibility(View.GONE);
            } else if (isVideoFile(lowerCasePath)) {
                // For videos, generate a thumbnail from the video file.
                Bitmap bmp = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND);
                thumbnail.setImageBitmap(bmp);
                videoIcon.setVisibility(View.VISIBLE);
            }

            // Set up the long-press listener as requested.
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    selectionOverlay.setVisibility(View.VISIBLE);
                    if (listener != null) {
                        // Use a small delay to make the selection feedback visible before switching screens.
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                listener.onMediaSelected(file);
                            }
                        }, 100);
                    }
                    return true;
                }
            });
        }

        private boolean isImageFile(String path) {
            return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp");
        }

        private boolean isVideoFile(String path) {
            return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".3gp") || path.endsWith(".mkv");
        }
    }
}
