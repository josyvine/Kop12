package com.kop.app; 

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    // NEW: Listener for re-editing clicks
    private OnFrameClickListener clickListener;

    // NEW: Interface for click events
    public interface OnFrameClickListener {
        void onFrameClicked(int position);
    }

    // UPDATED: Constructor to accept the click listener
    public FilmStripAdapter(Context context, List<File> frameFiles, OnFrameClickListener clickListener) {
        this.context = context;
        this.frameFiles = frameFiles;
        this.clickListener = clickListener;
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

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        Bitmap thumbnailBitmap = BitmapFactory.decodeFile(frameFile.getAbsolutePath(), options);
        holder.thumbnail.setImageBitmap(thumbnailBitmap);

        if (position == currentPosition) {
            holder.highlight.setVisibility(View.VISIBLE);
            holder.highlight.setBackgroundResource(android.R.drawable.dialog_frame);
        } else {
            holder.highlight.setVisibility(View.GONE);
        }

        // NEW: Set the click listener on the item view
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) {
                    // Get the adapter position which is reliable inside a click listener
                    int adapterPosition = holder.getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        clickListener.onFrameClicked(adapterPosition);
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return frameFiles.size();
    }

    public void setCurrentFrame(int position) {
        int previousPosition = this.currentPosition;
        this.currentPosition = position;

        if (previousPosition != -1) {
            notifyItemChanged(previousPosition);
        }
        if (currentPosition != -1) {
            notifyItemChanged(currentPosition);
        }
    }

    public void updateData(List<File> newFrameFiles) {
        this.frameFiles = newFrameFiles;
        this.currentPosition = -1;
        notifyDataSetChanged();
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