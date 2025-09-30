#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord; 
uniform samplerExternalOES uCameraTexture;
uniform sampler2D uMaskTexture; // The AI-generated mask
uniform float uKsize; // Range 0.0 to 1.0
uniform vec2 uTextureSize; // The dimensions of the texture

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
    vec4 finalColor;

    // Check if the current pixel is part of the PERSON (mask is white)
    if (mask.r > 0.5) {
        // --- Apply Pencil Sketch Logic to the person ---
        vec2 texelSize = 1.0 / uTextureSize;
        float originalGray = grayscale(originalColor);
        
        float blurRadius = uKsize * 5.0;
        vec4 blurredSum = vec4(0.0);
        int sampleCount = 0;
        
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                vec2 offset = vec2(float(x), float(y)) * texelSize * blurRadius;
                float invertedSample = 1.0 - grayscale(texture2D(uCameraTexture, vTextureCoord + offset));
                blurredSum += vec4(invertedSample);
                sampleCount++;
            }
        }
        
        float blurredInvertedGray = blurredSum.r / float(sampleCount);

        vec3 sketchColor = colorDodge(vec3(originalGray), vec3(blurredInvertedGray));
        float finalGray = grayscale(vec4(sketchColor, 1.0));

        finalColor = vec4(vec3(finalGray), 1.0);
    } else {
        // --- This is the BACKGROUND, so output a solid white color to hide the raw camera feed ---
        finalColor = vec4(1.0, 1.0, 1.0, 1.0);
    }

    // --- FIX START: The entire edge detection block has been removed to hide the black outline. ---
    // The final output is now simply the 'finalColor' calculated above, without any mixing.
    gl_FragColor = finalColor;
    // --- FIX END ---
}
