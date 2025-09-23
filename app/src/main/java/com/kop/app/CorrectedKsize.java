package com.kop.app;

/**
 * A simple data class to hold the AI's suggestion for the ksize parameter.
 * This is used to pass data cleanly from the GeminiAiHelper back to the ProcessingDialogFragment.
 */
public class CorrectedKsize {
    public boolean wasCorrected;
    public int ksize;

    public CorrectedKsize(boolean wasCorrected, int ksize) {
        this.wasCorrected = wasCorrected;
        this.ksize = ksize;
    }
}
