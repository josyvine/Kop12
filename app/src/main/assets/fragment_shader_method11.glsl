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
    // --- FIX START: New strategy to hide mask imperfections ---
    
    // 1. First, create a pencil sketch of the ENTIRE image, regardless of the mask.
    vec2 texelSize = 1.0 / uTextureSize;
    vec4 originalColor = texture2D(uCameraTexture, vTextureCoord);
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
    vec4 fullScreenSketch = vec4(vec3(finalGray), 1.0);

    // 2. Get the sharpened mask, which tells us where the person is.
    vec4 mask = texture2D(uMaskTexture, vTextureCoord);
    float sharpMask = smoothstep(0.4, 0.6, mask.r);

    // 3. Blend from the full-screen sketch to the original color based on the mask.
    // Where the mask is 0 (background), we see the sketch.
    // Where the mask is 1 (person), we see the original color.
    gl_FragColor = mix(fullScreenSketch, originalColor, sharpMask);

    // --- FIX END ---
}
