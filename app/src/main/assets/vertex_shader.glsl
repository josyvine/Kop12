attribute vec4 vPosition;
attribute vec2 vTexCoord;
varying vec2 vTextureCoord;
uniform mat4 uTransformMatrix;

void main() {
    gl_Position = vPosition;
    vTextureCoord = (uTransformMatrix * vec4(vTexCoord, 0.0, 1.0)).xy;
}
