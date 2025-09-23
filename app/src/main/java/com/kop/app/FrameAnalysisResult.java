package com.kop.app;

import android.graphics.Rect;
import java.util.List;

/**
 * A data class to hold the structured result from the Gemini API's object detection analysis.
 * It contains a list of bounding boxes for all primary subjects identified in a frame.
 */
public class FrameAnalysisResult {

    private final List<Rect> objectBounds;

    /**
     * Constructs a new FrameAnalysisResult.
     * @param objectBounds A list of Rect objects, where each Rect defines the bounding
     *                     box of a detected object.
     */
    public FrameAnalysisResult(List<Rect> objectBounds) {
        this.objectBounds = objectBounds;
    }

    /**
     * @return The list of detected object bounding boxes. Can be empty if no objects were found.
     */
    public List<Rect> getObjectBounds() {
        return objectBounds;
    }

    /**
     * @return True if at least one object was detected, false otherwise.
     */
    public boolean hasObjects() {
        return objectBounds != null && !objectBounds.isEmpty();
    }
}
