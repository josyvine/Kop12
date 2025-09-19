package com.kop.app;

import java.io.File; 

public class MediaItem {

    public enum ItemType {
        PARENT, // Represents the ".." up directory
        FOLDER,
        FILE
    }

    private final File file;
    private final ItemType type;

    public MediaItem(File file, ItemType type) {
        this.file = file;
        this.type = type;
    }

    public File getFile() {
        return file;
    }

    public ItemType getType() {
        return type;
    }

    public String getName() {
        if (type == ItemType.PARENT) {
            return "..";
        }
        return file != null ? file.getName() : "";
    }

    public String getPath() {
        return file != null ? file.getAbsolutePath() : "";
    }
}
