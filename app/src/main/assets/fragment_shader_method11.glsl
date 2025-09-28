#extension GL_OES_EGL_image_external : require
precision mediump float; 

varying vec2 vTextureCoord;
uniform samplerExternalOES uCameraTexture;
uniform sampler2D uMaskTexture; // The AI-generated mask
uniform float uKsize; // Range 0.0 to 1.0

// Function to convert a color to grayscale
float grayscale(vec4 color) {
    return dot(color.rgb, vec3(0.299, 0.587, 0.114));
}

// Color Dodge blend function
vec3 colorDodge(vec3 base, vec3 blend) {
    return min(base / (1.0 - blend), vec3(1.0));
}

void main() {
    vec4 mask = texture2D(uMaskTexture, vTextureCoord);
    vec4 originalColor = texture2D(uCameraTexture, vTextureCoord);

    // Check if the current pixel is part of the person (mask is white)
    if (mask.r > 0.5) {
        // --- Apply Pencil Sketch Logic (Identical to Method 9) ---
        vec2 texelSize = 1.0 / vec2(textureSize(uCameraTexture, 0));
        float originalGray = grayscale(originalColor);
        
        float blurRadius = uKsize * 5.0;
        vec4 blurredSum = vec4(0.0);
        int sampleCount = 0;
        
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                vec2 offset = vec2(float(x), float(y)) * texelSize * blurRadius;
                // We blur the inverted original color for the sketch effect
                float invertedSample = 1.0 - grayscale(texture2D(uCameraTexture, vTextureCoord + offset));
                blurredSum += vec4(invertedSample); // Summing up inverted values
                sampleCount++;
            }
        }
        
        // The average of the inverted samples is our blurred inverted gray
        float blurredInvertedGray = blurredSum.r / float(sampleCount);

        // We don't need to re-invert here because we blurred the inverted source
        vec3 sketchColor = colorDodge(vec3(originalGray), vec3(blurredInvertedGray));
        float finalGray = grayscale(vec4(sketchColor, 1.0));

        gl_FragColor = vec4(vec3(finalGray), 1.0);
    } else {
        // --- This is the background, so output the original color ---
        gl_FragColor = originalColor;
    }
}
