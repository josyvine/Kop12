package com.kop.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class FilmStripAdapter extends RecyclerView.Adapter<FilmStripAdapter.FrameViewHolder> {

    private Context context;
    private List<File> frameFiles;
    private int currentPosition = -1;

    public FilmStripAdapter(Context context, List<File> frameFiles) {
        this.context = context;
        this.frameFiles = frameFiles;
    }

    @NonNull
    @Override
    public FrameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_film_strip, parent, false);
        return new FrameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FrameViewHolder holder, int position) {
        File frameFile = frameFiles.get(position);

        // Load a down-sampled bitmap to avoid using too much memory for thumbnails
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4; // Load a quarter-sized image
        Bitmap thumbnailBitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath(), options);
        holder.thumbnail.setImageBitmap(thumbnailBitmap);

        // Highlight the currently processing frame
        if (position == currentPosition) {
            holder.highlight.setVisibility(View.VISIBLE);
            holder.highlight.setBackgroundColor(Color.parseColor("#80FFFFFF")); // Semi-transparent white
        } else {
            holder.highlight.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return frameFiles.size();
    }

    public void setCurrentFrame(int position) {
        int previousPosition = this.currentPosition;
        this.currentPosition = position;

        // Notify the adapter to redraw the previous and new current items
        if (previousPosition != -1) {
            notifyItemChanged(previousPosition);
        }
        if (currentPosition != -1) {
            notifyItemChanged(currentPosition);
        }
    }

    public static class FrameViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        ImageView highlight;

        public FrameViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_thumbnail);
            highlight = itemView.findViewById(R.id.iv_highlight);
        }
    }
}
