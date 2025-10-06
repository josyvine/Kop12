package com.kop.app;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A utility class to handle the extraction of image files from a ZIP archive.
 */
public class ZipExtractor {

    private static final String TAG = "ZipExtractor";
    private static final int BUFFER_SIZE = 4096;

    public interface ExtractionListener {
        void onExtractionProgress(int extractedCount, int totalFiles);
        void onExtractionComplete(List<File> extractedFiles);
        void onExtractionError(String errorMessage);
    }

    /**
     * Extracts all supported image files from a given ZIP archive into a specified directory.
     * This operation is performed on the calling thread, so it should be run in the background.
     *
     * @param zipFile The ZIP file to extract from.
     * @param destinationDir The directory where the extracted images will be saved.
     * @return A list of File objects pointing to the successfully extracted images.
     * @throws IOException if an I/O error occurs during extraction.
     */
    public static List<File> extractImages(File zipFile, File destinationDir) throws IOException {
        List<File> extractedFiles = new ArrayList<>();

        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        FileInputStream fileInputStream = new FileInputStream(zipFile);
        ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(fileInputStream));

        try {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                // Prevent Zip Slip vulnerability
                String canonicalDestinationDirPath = destinationDir.getCanonicalPath();
                File destinationFile = new File(destinationDir, zipEntry.getName());
                String canonicalDestinationFilePath = destinationFile.getCanonicalPath();

                if (!canonicalDestinationFilePath.startsWith(canonicalDestinationDirPath + File.separator)) {
                    throw new IOException("Attempted Path Traversal in ZIP entry: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    continue; // Skip directories
                }

                if (isSupportedImageFile(zipEntry.getName())) {
                    // Ensure parent directory exists for nested images inside the zip
                    File parentDir = destinationFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    extractFile(zipInputStream, destinationFile);
                    extractedFiles.add(destinationFile);
                    Log.d(TAG, "Extracted image: " + destinationFile.getAbsolutePath());
                } else {
                    Log.d(TAG, "Skipping non-image file: " + zipEntry.getName());
                }
                zipInputStream.closeEntry();
            }
        } finally {
            zipInputStream.close();
        }

        return extractedFiles;
    }

    private static void extractFile(ZipInputStream zipInputStream, File outputFile) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile));
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = zipInputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        bos.flush();
        bos.close();
    }

    private static boolean isSupportedImageFile(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.US);
        return lowerCaseName.endsWith(".jpg") ||
               lowerCaseName.endsWith(".jpeg") ||
               lowerCaseName.endsWith(".png") ||
               lowerCaseName.endsWith(".webp");
    }
}