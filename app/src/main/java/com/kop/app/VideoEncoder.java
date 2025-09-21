package com.kop.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoder {
    private static final String TAG = "VideoEncoder";
    private static final String VIDEO_MIME_TYPE = "video/avc"; // H.264
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm"; // AAC
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 1; // 1 second between I-frames

    // Audio configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_BITRATE = 64000;

    private int width;
    private int height;
    private int bitRate;
    private File outputFile;

    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaMuxer muxer;
    private Surface inputSurface;
    private AudioRecord audioRecord;

    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private volatile boolean isRecording = false;
    private boolean muxerStarted = false;

    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec.BufferInfo audioBufferInfo;

    private HandlerThread encoderThread;
    private Handler encoderHandler;
    private Thread audioThread;
    
    private long presentationTimeNs = 0;
    
    private final Object lock = new Object();

    public VideoEncoder(int width, int height, int bitRate, File outputFile) {
        this.width = width;
        this.height = height;
        this.bitRate = bitRate;
        this.outputFile = outputFile;
        this.videoBufferInfo = new MediaCodec.BufferInfo();
        this.audioBufferInfo = new MediaCodec.BufferInfo();
    }

    public void start() throws IOException {
        encoderThread = new HandlerThread("VideoEncoderThread");
        encoderThread.start();
        encoderHandler = new Handler(encoderThread.getLooper());

        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    prepareVideoEncoder();
                    prepareAudioEncoder();

                    muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                    isRecording = true;

                    startAudioRecording();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to initialize encoder", e);
                    release();
                }
            }
        });
    }
    
    public void stop() {
        if (!isRecording) {
            return;
        }
        
        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                isRecording = false;
                if (audioThread != null) {
                    try {
                        audioThread.join();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Audio thread join interrupted", e);
                    }
                }
                
                // Signal end of stream to video encoder
                if (videoEncoder != null) {
                    videoEncoder.signalEndOfInputStream();
                }

                drainEncoder(true); // Drain any remaining frames
                release();
                
                if (encoderThread != null) {
                    encoderThread.quitSafely();
                }
            }
        });
    }

    public void encodeFrame(Bitmap bitmap) {
        if (!isRecording || inputSurface == null) {
            return;
        }

        encoderHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRecording) return;
                
                Canvas canvas = inputSurface.lockCanvas(null);
                if (canvas == null) return;
                
                try {
                    // Create a matrix to scale the bitmap to fit the video dimensions
                    Matrix matrix = new Matrix();
                    float scaleX = (float) width / bitmap.getWidth();
                    float scaleY = (float) height / bitmap.getHeight();
                    float scale = Math.max(scaleX, scaleY);
                    matrix.postScale(scale, scale);
                    
                    // Center the bitmap
                    float dx = (width - bitmap.getWidth() * scale) / 2;
                    float dy = (height - bitmap.getHeight() * scale) / 2;
                    matrix.postTranslate(dx, dy);

                    canvas.drawBitmap(bitmap, matrix, null);
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas);
                }

                drainEncoder(false);
            }
        });
    }

    private void prepareVideoEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = videoEncoder.createInputSurface();
        videoEncoder.start();
    }

    private void prepareAudioEncoder() throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        
        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
        
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
    }
    
    private void startAudioRecording() {
        audioRecord.startRecording();
        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ByteBuffer audioBuffer = ByteBuffer.allocateDirect(audioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT));
                while (isRecording) {
                    int readResult = audioRecord.read(audioBuffer, audioBuffer.capacity());
                    if (readResult > 0) {
                        encodeAudio(audioBuffer, readResult);
                    }
                }
                // Send end of stream to audio encoder
                if (audioEncoder != null) {
                    int inputBufferIndex = audioEncoder.dequeueInputBuffer(-1);
                    if (inputBufferIndex >= 0) {
                        audioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, getPresentationTimeNs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
            }
        });
        audioThread.start();
    }
    
    private void encodeAudio(ByteBuffer buffer, int length) {
        int inputBufferIndex = audioEncoder.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(buffer);
            audioEncoder.queueInputBuffer(inputBufferIndex, 0, length, getPresentationTimeNs(), 0);
        }
    }

    private void drainEncoder(boolean endOfStream) {
        synchronized(lock) {
            if (videoEncoder != null) {
                int outputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex);
                    if (muxerStarted && videoBufferInfo.size > 0) {
                        videoBufferInfo.presentationTimeUs = getPresentationTimeNs() / 1000;
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferInfo);
                    }
                    videoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                    outputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 0);
                }

                if (outputBufferIndex == MediaFormat.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (videoTrackIndex < 0) {
                         videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                         tryStartMuxer();
                    }
                }
            }
            
            if (audioEncoder != null) {
                int outputBufferIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(outputBufferIndex);
                    if (muxerStarted && audioBufferInfo.size > 0) {
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, audioBufferInfo);
                    }
                    audioEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                    outputBufferIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0);
                }

                if (outputBufferIndex == MediaFormat.INFO_OUTPUT_FORMAT_CHANGED) {
                     if (audioTrackIndex < 0) {
                        audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
                        tryStartMuxer();
                    }
                }
            }
        }
    }
    
    private void tryStartMuxer() {
        if (!muxerStarted && videoTrackIndex != -1 && audioTrackIndex != -1) {
            muxer.start();
            muxerStarted = true;
        }
    }
    
    private long getPresentationTimeNs() {
        long result = System.nanoTime();
        if (result <= presentationTimeNs) {
            result = presentationTimeNs + 1;
        }
        presentationTimeNs = result;
        return result;
    }
    
    private void release() {
        try {
            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
            }
            if (audioEncoder != null) {
                audioEncoder.stop();
                audioEncoder.release();
                audioEncoder = null;
            }
            if (muxer != null) {
                if(muxerStarted){
                    muxer.stop();
                }
                muxer.release();
                muxer = null;
            }
            if (inputSurface != null) {
                inputSurface.release();
                inputSurface = null;
            }
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during release", e);
        }
        muxerStarted = false;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
    }
}
