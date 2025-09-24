package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    // FIX: Updated the model to the specific "gemini-2.0-flash" endpoint provided by the user.
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";
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

    // --- START OF NEW METHODS FOR TASK 2 (AI-Guided Scanning) ---

    /**
     * Scans a raw frame to find the bounding boxes of primary objects.
     *
     * @param apiKey The user's Gemini API key.
     * @param rawFrame The unprocessed frame to analyze.
     * @return A FrameAnalysisResult containing the bounding boxes of detected objects.
     * @throws IOException if the API key is missing, the network call fails, or the response cannot be parsed.
     */
    public static FrameAnalysisResult findObjectRegions(String apiKey, Bitmap rawFrame) throws IOException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IOException("API Key is missing.");
        }

        try {
            String rawFrameBase64 = bitmapToBase64(rawFrame);
            String jsonPayload = buildObjectDetectionPayload(rawFrameBase64);

            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("API Call Failed: " + response.code() + " " + response.body().string());
                }
                String responseBody = response.body().string();
                return parseObjectDetectionResponse(responseBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred during AI object detection", e);
            throw new IOException("Failed to process AI request. Check logs for details.", e);
        }
    }

    /**
     * Constructs the JSON payload for the object detection task.
     * @param rawFrameBase64 The Base64 encoded image.
     * @return The JSON payload as a string.
     */
    private static String buildObjectDetectionPayload(String rawFrameBase64) {
        String promptText = "You are an advanced object detection system. Your task is to identify the primary subjects in this image. " +
                "Respond ONLY with a JSON object. The object must have a single key 'objects' which is an array. " +
                "Each element in the array should be an object with a 'box' key. The 'box' value is an array of four integers: [x, y, width, height]. " +
                "For example: {\\\"objects\\\": [{\\\"box\\\": [100, 150, 320, 400]}]}";

        return "{\"contents\":[{\"parts\":[" +
                "{\"text\": \"" + promptText + "\"}," +
                "{\"inline_data\": {\"mime_type\":\"image/jpeg\", \"data\": \"" + rawFrameBase64 + "\"}}" +
                "]}]}";
    }

    /**
     * Parses the JSON response from the object detection API call.
     * @param responseBody The raw JSON string from the API.
     * @return A FrameAnalysisResult containing the parsed bounding boxes.
     */
    private static FrameAnalysisResult parseObjectDetectionResponse(String responseBody) {
        List<Rect> objectBounds = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String textResponse = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                     .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                     .get("text").getAsString();

            textResponse = textResponse.replace("```json", "").replace("```", "").trim();

            JsonObject result = JsonParser.parseString(textResponse).getAsJsonObject();
            if (result.has("objects")) {
                JsonArray objects = result.getAsJsonArray("objects");
                for (JsonElement objElement : objects) {
                    JsonObject obj = objElement.getAsJsonObject();
                    if (obj.has("box")) {
                        JsonArray box = obj.getAsJsonArray("box");
                        if (box.size() == 4) {
                            int x = box.get(0).getAsInt();
                            int y = box.get(1).getAsInt();
                            int width = box.get(2).getAsInt();
                            int height = box.get(3).getAsInt();
                            objectBounds.add(new Rect(x, y, x + width, y + height));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse AI object detection response: " + responseBody, e);
        }
        Log.d(TAG, "AI detected " + objectBounds.size() + " objects.");
        return new FrameAnalysisResult(objectBounds);
    }
    // --- END OF NEW METHODS ---

    // --- START OF NEW METHOD FOR AI CONTROL SYSTEM (Version 2) ---

    /**
     * Checks a new raw frame against a "gold standard" processed frame to maintain stylistic
     * consistency, suggesting adjustments to the ksize parameter if needed.
     *
     * @param apiKey         The user's Gemini API key.
     * @param goldStandard   The user-approved processed first frame (the target style).
     * @param currentRawFrame The current unprocessed frame to be analyzed.
     * @param currentKsize   The ksize value currently in use.
     * @return A CorrectedKsize object containing either the original ksize or an AI-suggested one.
     */
    public static CorrectedKsize checkFrameConsistency(
            String apiKey,
            Bitmap goldStandard,
            Bitmap currentRawFrame,
            int currentKsize) {

        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "API Key is missing. Skipping AI consistency check.");
            return new CorrectedKsize(false, currentKsize);
        }

        try {
            String goldStandardBase64 = bitmapToBase64(goldStandard);
            String rawFrameBase64 = bitmapToBase64(currentRawFrame);

            String jsonPayload = buildConsistencyCheckPayload(goldStandardBase64, rawFrameBase64);

            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "AI Consistency Check Failed: " + response.code() + " " + response.body().string());
                    return new CorrectedKsize(false, currentKsize);
                }

                String responseBody = response.body().string();
                return parseConsistencyResponse(responseBody, currentKsize);
            }

        } catch (Exception e) {
            Log.e(TAG, "An error occurred during AI consistency check", e);
            return new CorrectedKsize(false, currentKsize);
        }
    }

    /**
     * Constructs the JSON payload for the frame consistency check task.
     */
    private static String buildConsistencyCheckPayload(String goldStandardBase64, String rawFrameBase64) {
        String promptText = "You are an expert video filter analyst. Image 1 is a \\\"Gold Standard\\\" processed frame. " +
                "Image 2 is a new, \\\"Raw\\\" video frame. The primary parameter controlling the sketch is `ksize`. " +
                "Based on the lighting and detail in the Raw frame, would the current `ksize` produce a result stylistically consistent with the Gold Standard? " +
                "If the raw frame is much darker or brighter, `ksize` might need to be adjusted up or down by a small amount. " +
                "Respond ONLY with JSON: {\\\"adjustment_needed\\\": boolean, \\\"reason\\\": \\\"none|too_dark|too_bright|low_detail\\\", \\\"suggested_ksize_change\\\": 0|-2|+2}";

        return "{\"contents\":[{\"parts\":[" +
                "{\"text\": \"" + promptText + "\"}," +
                "{\"inline_data\": {\"mime_type\":\"image/jpeg\", \"data\": \"" + goldStandardBase64 + "\"}}," +
                "{\"inline_data\": {\"mime_type\":\"image/jpeg\", \"data\": \"" + rawFrameBase64 + "\"}}" +
                "]}]}";
    }

    /**
     * Parses the JSON response from the consistency check API call.
     */
    private static CorrectedKsize parseConsistencyResponse(String responseBody, int ksize) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String textResponse = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();

            textResponse = textResponse.replace("```json", "").replace("```", "").trim();

            JsonObject result = JsonParser.parseString(textResponse).getAsJsonObject();
            boolean adjustmentNeeded = result.get("adjustment_needed").getAsBoolean();

            if (!adjustmentNeeded) {
                return new CorrectedKsize(false, ksize);
            }

            int ksizeChange = result.get("suggested_ksize_change").getAsInt();
            int newKsize = ksize + ksizeChange;

            // Safety checks: Ensure ksize is always an odd number and at least 3.
            newKsize = Math.max(3, newKsize);
            if (newKsize % 2 == 0) {
                newKsize++; // If it's even, make it odd.
            }

            Log.d(TAG, "AI Consistency Correction. Reason: " + result.get("reason").getAsString() + ". Original ksize: " + ksize + ", New ksize: " + newKsize);
            return new CorrectedKsize(true, newKsize);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse AI consistency response: " + responseBody, e);
            return new CorrectedKsize(false, ksize);
        }
    }
    // --- END OF NEW METHOD ---
}
