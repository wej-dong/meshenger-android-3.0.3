package org.webrtc.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 功能：
 * 作者：wej
 * 日期：2023年07月13日
 */
public class AudioTrackInterceptor extends AudioTrack {
    // 即原[WebRtcAudioTrack.audioTrack]
    private AudioTrack originalTrack;

    // 音频数据输出回调AudioTrackStateCallback
    private JavaAudioDeviceModule.SamplesReadyCallback samplesReadyCallback;

    public AudioTrackInterceptor(AudioTrack originalTrack, JavaAudioDeviceModule.SamplesReadyCallback samplesReadyCallback) {
        //不用关心这里传的参数，只是一个壳
        super(AudioManager.STREAM_VOICE_CALL, 44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 8192, MODE_STREAM);
        this.originalTrack = originalTrack;
        this.samplesReadyCallback = samplesReadyCallback;
    }

    @Override
    public int getState() {
        return originalTrack.getState();
    }
    @Override
    public void play() {
        originalTrack.play();
    }
    @Override
    public int getPlayState() {
        return originalTrack.getPlayState();
    }
    @Override
    public void stop() {
        originalTrack.stop();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int write(@NonNull ByteBuffer audioData, int sizeInBytes, int writeMode) {
        int position = audioData.position();
        int from = audioData.isDirect() ? position : audioData.arrayOffset() + position;
        int write = originalTrack.write(audioData, sizeInBytes, writeMode);
        if (write == sizeInBytes) {
            byte[] bytes = Arrays.copyOfRange(audioData.array(), from, from + sizeInBytes);
            samplesReadyCallback.onWebRtcAudioRecordSamplesReady(
                    new JavaAudioDeviceModule.AudioSamples(
                            originalTrack.getAudioFormat(),
                            originalTrack.getChannelCount(),
                            originalTrack.getSampleRate(),
                            bytes
                    )
            );
        }
        return write;
    }

    @Override
    public int write(@NonNull byte[] audioData, int offsetInBytes, int sizeInBytes) {
        int write = originalTrack.write(audioData, offsetInBytes, sizeInBytes);
        if (write == sizeInBytes) {
            byte[] bytes = Arrays.copyOfRange(audioData, offsetInBytes, offsetInBytes + sizeInBytes);
            samplesReadyCallback.onWebRtcAudioRecordSamplesReady(
                    new JavaAudioDeviceModule.AudioSamples(
                            originalTrack.getAudioFormat(),
                            originalTrack.getChannelCount(),
                            originalTrack.getSampleRate(),
                            bytes
                    )
            );
        }
        return write;
    }
}
