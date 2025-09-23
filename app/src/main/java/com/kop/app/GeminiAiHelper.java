package com.kop.app;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log; 

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

// --- START OF ADDED IMPORTS TO FIX "CANNOT FIND SYMBOL" ERRORS ---
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
// --- END OF ADDED IMPORTS ---

/**
 * A dedicated helper class to handle all communication with the Google Gemini API.
 * This class encapsulates networking, JSON building, and response parsing to keep the main
 * fragment logic clean and focused on UI and processing.
 */
public class GeminiAiHelper {

    private static final String TAG = "GeminiAiHelper";
    // The Gemini endpoint is hardcoded as requested. The API key will be appended.
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent?key=";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * The main public method to get AI-corrected parameters.
     * It takes the necessary images and current settings, communicates with the Gemini API,
     * and returns a corrected set of parameters if needed.
     *
     * @param apiKey The user's Gemini API key.
     * @param goldStandard The user-approved processed first frame (the target style).
     * @param rawFrame The current unprocessed frame.
     * @param processedFrame The current frame processed with the user's default settings.
     * @param currentKsize The ksize value used to generate the processedFrame.
     * @return A CorrectedKsize object containing either the original ksize or the AI-suggested one.
     */
    public static CorrectedKsize getCorrectedKsize(
            String apiKey,
            Bitmap goldStandard,
            Bitmap rawFrame,
            Bitmap processedFrame,
            int currentKsize) {

        // If the API key is missing, immediately return without doing anything.
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "API Key is missing. Skipping AI correction.");
            return new CorrectedKsize(false, currentKsize);
        }

        try {
            // Convert all bitmap images to Base64 strings for the JSON payload.
            String goldStandardBase64 = bitmapToBase64(goldStandard);
            String rawFrameBase64 = bitmapToBase64(rawFrame);
            String processedFrameBase64 = bitmapToBase64(processedFrame);

            // Build the JSON request body with the prompt and image data.
            String jsonPayload = buildJsonPayload(goldStandardBase64, rawFrameBase64, processedFrameBase64);

            // Create and send the network request.
            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL + apiKey)
                    .post(body)
                    .build();

            // Execute the call and handle the response.
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    // Log the error if the API call fails (e.g., bad API key, server error).
                    Log.e(TAG, "API Call Failed: " + response.code() + " " + response.body().string());
                    return new CorrectedKsize(false, currentKsize);
                }

                String responseBody = response.body().string();
                // Parse the successful response to get the AI's suggestion.
                return parseResponseAndSuggestKsize(responseBody, currentKsize);
            }

        } catch (Exception e) {
            // Catch any other exceptions (e.g., network issues, out of memory).
            Log.e(TAG, "An error occurred during AI processing", e);
            return new CorrectedKsize(false, currentKsize);
        }
    }

    /**
     * Converts a Bitmap object to a Base64 encoded string.
     * @param bitmap The bitmap to convert.
     * @return A Base64 string representation of the image.
     */
    private static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // Compress the bitmap to JPEG format. PNG can be too large for API requests.
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    /**
     * Constructs the JSON payload required by the Gemini Vision API.
     * This includes a highly specific prompt and the three Base64-encoded images.
     */
    private static String buildJsonPayload(String goldStandardBase64, String rawFrameBase64, String processedFrameBase64) {
        // This detailed prompt is the "brain" of the operation, guiding the AI.
        String promptText = "You are an expert in AI-generated sketch art. Your task is to ensure stylistic consistency for a video filter. " +
                "Image 1 is the 'Target Style' sketch. Image 2 is a 'Raw Video Frame'. Image 3 is the 'Current Sketch' generated from Image 2. " +
                "The primary control parameter is 'ksize', which affects line clarity and detail. A low ksize gives sharp detail. A high ksize is more abstract and blurry. " +
                "Does Image 3's sketch style match Image 1? " +
                "Respond ONLY with a JSON object in this format: {\\\"is_consistent\\\": boolean, \\\"problem\\\": \\\"none|blurry_or_underprocessed|smudged_or_overprocessed\\\"}";

        // Note: The backslashes are necessary to escape quotes within the Java string.
        return "{\"contents\":[{\"parts\":[" +
                "{\"text\": \"" + promptText + "\"}," +
                "{\"inline_data\": {\"mime_type\":\"image/jpeg\", \"data\": \"" + goldStandardBase64 + "\"}}," +
                "{\"inline_data\": {\"mime_type\":\"image/jpeg\", \"data\": \"" + rawFrameBase64 + "\"}}," +
                "{\"inline_data\": {\"mime_type\":\"image/jpeg\", \"data\": \"" + processedFrameBase64 + "\"}}" +
                "]}]}";
    }

    /**
     * Parses the JSON response from the Gemini API and translates its feedback into a concrete
     * change to the ksize parameter.
     * @param responseBody The raw JSON string from the API.
     * @param ksize The current ksize value.
     * @return A CorrectedKsize object with the AI's suggestion.
     */
    private static CorrectedKsize parseResponseAndSuggestKsize(String responseBody, int ksize) {
        try {
            // The AI's JSON response is nested inside the API's own JSON structure.
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String textResponse = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                     .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                     .get("text").getAsString();

            // The AI might wrap its JSON in markdown, so we clean it.
            textResponse = textResponse.replace("```json", "").replace("```", "").trim();

            JsonObject result = JsonParser.parseString(textResponse).getAsJsonObject();
            boolean isConsistent = result.get("is_consistent").getAsBoolean();

            if (isConsistent) {
                // If the AI says the frame is good, no correction is needed.
                return new CorrectedKsize(false, ksize);
            }

            // If not consistent, read the problem and suggest a correction.
            String problem = result.get("problem").getAsString();
            int newKsize = ksize;

            switch (problem) {
                case "blurry_or_underprocessed":
                    // The effect is too weak/blurry, so we decrease ksize to add more detail.
                    newKsize = ksize - 2;
                    break;
                case "smudged_or_overprocessed":
                    // The effect is too strong/smudged, so we increase ksize to make it more abstract/softer.
                    newKsize = ksize + 2;
                    break;
            }

            // Safety checks: Ensure ksize is always an odd number and at least 3.
            newKsize = Math.max(3, newKsize);
            if (newKsize % 2 == 0) {
                newKsize++; // If it's even, make it odd.
            }

            Log.d(TAG, "AI Correction Applied. Problem: " + problem + ". Original ksize: " + ksize + ", New ksize: " + newKsize);
            return new CorrectedKsize(true, newKsize);

        } catch (Exception e) {
            // If the response from the AI is not valid JSON or is structured unexpectedly.
            Log.e(TAG, "Failed to parse AI response: " + responseBody, e);
            return new CorrectedKsize(false, ksize);
        }
    }
}
