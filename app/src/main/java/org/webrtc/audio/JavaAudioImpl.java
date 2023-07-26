package org.webrtc.audio;

import android.content.Context;
import android.media.AudioTrack;

import java.lang.reflect.Field;

import d.d.meshenger.utils.Log;

/**
 * 功能：
 * 作者：wej
 * 日期：2023年07月13日
 */
public class JavaAudioImpl {
    private JavaAudioDeviceModule audioDeviceModule;

    public JavaAudioImpl(Context applicationContext){
        init(applicationContext);
    }

    public void init(Context applicationContext) {
        audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
                .setSamplesReadyCallback(new JavaAudioDeviceModule.SamplesReadyCallback() {
                    @Override
                    public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples audioSamples) {
                        // 音频输入数据，麦克风数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
                        int audioFormat = audioSamples.getAudioFormat();
                        int channelCount = audioSamples.getChannelCount();
                        int sampleRate = audioSamples.getSampleRate();
                        // pcm格式数据
                        byte[] data = audioSamples.getData();
                        // log("local本地音频数据--------------------" + Arrays.toString(data));
                    }
                })
                .setAudioTrackStateCallback(new JavaAudioDeviceModule.AudioTrackStateCallback() {
                    @Override
                    public void onWebRtcAudioTrackStart() {
                        // 回调AudioTrack状态，启动
                        try {
                            setAudioTrackSamplesReadyCallback(audioDeviceModule, new JavaAudioDeviceModule.SamplesReadyCallback() {
                                @Override
                                public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples audioSamples) {
                                    //音频输出数据，通话时对方数据，原始pcm数据，可以直接录制成pcm文件，再转成mp3
                                    int audioFormat = audioSamples.getAudioFormat();
                                    int channelCount = audioSamples.getChannelCount();
                                    int sampleRate = audioSamples.getSampleRate();
                                    //pcm格式数据
                                    byte[] data = audioSamples.getData();
                                    // log("remote远端音频数据--------------------" + Arrays.toString(data));
                                }
                            });
                        } catch (NoSuchFieldException e){
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onWebRtcAudioTrackStop() { }
                })
                .createAudioDeviceModule();
    }

    public JavaAudioDeviceModule getJavaAudioDeviceModule(){
        return this.audioDeviceModule;
    }

    public static void setAudioTrackSamplesReadyCallback(JavaAudioDeviceModule module, JavaAudioDeviceModule.SamplesReadyCallback samplesReadyCallback) throws NoSuchFieldException, IllegalAccessException {
        Class<?> moduleClass = module.getClass();
        Field audioOutputField = moduleClass.getDeclaredField("audioOutput");
        audioOutputField.setAccessible(true);
        WebRtcAudioTrack webRtcAudioTrack = (WebRtcAudioTrack) audioOutputField.get(module);
        Class<?> audioTrackClass = webRtcAudioTrack.getClass();
        Field audioTrackField = audioTrackClass.getDeclaredField("audioTrack");
        audioTrackField.setAccessible(true);
        AudioTrack audioTrack = (AudioTrack) audioTrackField.get(webRtcAudioTrack);
        if (audioTrack == null) {
            return;
        }
        AudioTrackInterceptor interceptor = new AudioTrackInterceptor(audioTrack, samplesReadyCallback);
        audioTrackField.set(webRtcAudioTrack, interceptor);
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
