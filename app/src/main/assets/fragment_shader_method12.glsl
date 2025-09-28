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
    vec4 finalColor;

    // Check if the current pixel is part of the background (mask is black)
    if (mask.r < 0.5) {
        // --- Apply Pencil Sketch Logic (Identical to Method 9) ---
        vec2 texelSize = 1.0 / vec2(textureSize(uCameraTexture, 0));
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
        // --- This is the person, so output the original color ---
        finalColor = originalColor;
    }

    // --- Edge Detection on the Mask to create an outline ---
    vec2 texelSize = 1.0 / vec2(textureSize(uMaskTexture, 0));
    // Sobel operator for edge detection
    float topLeft = texture2D(uMaskTexture, vTextureCoord + vec2(-texelSize.x, -texelSize.y)).r;
    float top = texture2D(uMaskTexture, vTextureCoord + vec2(0.0, -texelSize.y)).r;
    float topRight = texture2D(uMaskTexture, vTextureCoord + vec2(texelSize.x, -texelSize.y)).r;
    float left = texture2D(uMaskTexture, vTextureCoord + vec2(-texelSize.x, 0.0)).r;
    float right = texture2D(uMaskTexture, vTextureCoord + vec2(texelSize.x, 0.0)).r;
    float bottomLeft = texture2D(uMaskTexture, vTextureCoord + vec2(-texelSize.x, texelSize.y)).r;
    float bottom = texture2D(uMaskTexture, vTextureCoord + vec2(0.0, texelSize.y)).r;
    float bottomRight = texture2D(uMaskTexture, vTextureCoord + vec2(texelSize.x, texelSize.y)).r;

    float dx = -topLeft - 2.0 * left - bottomLeft + topRight + 2.0 * right + bottomRight;
    float dy = -topLeft - 2.0 * top - topRight + bottomLeft + 2.0 * bottom + bottomRight;

    float edge = sqrt(dx * dx + dy * dy);
    
    // Mix the final color with black based on the edge strength to draw the outline
    gl_FragColor = mix(finalColor, vec4(0.0, 0.0, 0.0, 1.0), smoothstep(0.2, 0.5, edge));
}
