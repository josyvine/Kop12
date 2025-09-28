attribute vec4 vPosition;
attribute vec2 vTexCoord;
varying vec2 vTextureCoord; 

void main() {
    gl_Position = vPosition;
    vTextureCoord = vTexCoord;
}
