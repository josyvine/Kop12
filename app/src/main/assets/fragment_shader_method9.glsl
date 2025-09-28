#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 vTextureCoord; 
uniform samplerExternalOES uCameraTexture;
uniform float uKsize; // Range 0.0 to 1.0 (from SeekBar 0-100)

const float PI = 3.14159265359;

// Function to convert a color to grayscale
float grayscale(vec4 color) {
    return dot(color.rgb, vec3(0.299, 0.587, 0.114));
}

// Color Dodge blend function
vec3 colorDodge(vec3 base, vec3 blend) {
    return min(base / (1.0 - blend), vec3(1.0));
}

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(uCameraTexture, 0));
    float originalGray = grayscale(texture2D(uCameraTexture, vTextureCoord));
    float invertedGray = 1.0 - originalGray;

    // --- Gaussian Blur Simulation ---
    // uKsize is 0.0-1.0. We map it to a blur radius.
    // A larger ksize means a wider blur, which results in a softer, more abstract sketch.
    float blurRadius = uKsize * 5.0; // Controls the "thickness" of the pencil line
    vec4 blurredSum = vec4(0.0);
    int sampleCount = 0;

    // A simple box blur is efficient on the GPU. We sample in a grid.
    // The number of samples is fixed for performance, but the distance between them (spread) changes.
    for (int x = -4; x <= 4; x++) {
        for (int y = -4; y <= 4; y++) {
            vec2 offset = vec2(float(x), float(y)) * texelSize * blurRadius;
            blurredSum += texture2D(uCameraTexture, vTextureCoord + offset);
            sampleCount++;
        }
    }
    
    float blurredGray = 1.0 - grayscale(blurredSum / float(sampleCount));

    // --- Final Composition ---
    vec3 sketchColor = colorDodge(vec3(originalGray), vec3(blurredGray));
    
    // Convert the final sketch to a solid black-and-white image
    float finalGray = grayscale(vec4(sketchColor, 1.0));

    gl_FragColor = vec4(vec3(finalGray), 1.0);
}
