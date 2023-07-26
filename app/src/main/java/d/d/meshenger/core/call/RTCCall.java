package d.d.meshenger.core.call;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.ColorInt;
import android.support.v7.app.AppCompatDelegate;
import android.util.TypedValue;

import org.json.JSONException;
import org.json.JSONObject;
import org.libsodium.jni.Sodium;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Loggable;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import d.d.meshenger.R;
import d.d.meshenger.core.contact.Contact;
import d.d.meshenger.main.MainService;
import d.d.meshenger.utils.Crypto;
import d.d.meshenger.utils.Log;
import d.d.meshenger.utils.PacketReader;
import d.d.meshenger.utils.PacketWriter;
//import org.webrtc.VideoCapturer;

// 音视频通话处理类
public class RTCCall implements DataChannel.Observer {
    public enum CallState {
        CONNECTING,
        RINGING,
        CONNECTED,
        DISMISSED,
        ENDED,
        ERROR
    }
    private final String StateChangeMessage = "StateChange";
    private final String CameraDisabledMessage = "CameraDisabled";
    private final String CameraEnabledMessage = "CameraEnabled";
    private PeerConnectionFactory factory;      // PeerConnectionFactory用于创建PeerConnection实例
    private PeerConnection connection;      // PeerConnection实例，用于建立音视频通话的实际连接
    private MediaConstraints constraints;       // 用于设置音视频通话的约束条件。
    private String offer;       // 用于保存通话中交换SDP（Session Description Protocol）的数据
    private SurfaceViewRenderer remoteRenderer;     // 用于显示远端的音视频画面
    private SurfaceViewRenderer localRenderer;      // 用于显示本地的音视频画面
    private EglBase.Context sharedContext;      // 用于渲染音视频画面
    private CameraVideoCapturer capturer;
    private MediaStream upStream;       // 本地音视频媒体流
    private DataChannel dataChannel;        // 用于进行数据通信
    private boolean videoEnabled;
    private Context context;
    private Contact contact;
    private byte[] ownPublicKey;
    private byte[] ownSecretKey;
    private List<PeerConnection.IceServer> iceServers;      // 表示ICE服务器的配置
    private OnStateChangeListener listener;     // 获取用户状态变化的监听器
    private MainService.MainBinder binder;
    public CallState state;     // 枚举类型，表示当前通话状态
    public Socket commSocket;       // 与服务器端建立Socket通信

    // 呼入通话-构造函数
    public RTCCall(Context context, MainService.MainBinder binder, Contact contact, Socket commSocket, String offer) {
        this.context = context;
        this.contact = contact;
        this.commSocket = commSocket;
        this.listener = null;
        this.binder = binder;
        this.ownPublicKey = binder.getSettings().getPublicKey();
        this.ownSecretKey = binder.getSettings().getSecretKey();
        this.offer = offer;
        this.iceServers = new ArrayList<>();
        for (String server : binder.getSettings().getIceServers()) {
            this.iceServers.add(PeerConnection.IceServer.builder(server).createIceServer());
        }
        initRTC(context);
    }

    // 被动接听电话
    public void passiveAccept(OnStateChangeListener listener) {
        this.listener = listener;
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(this.iceServers);
        rtcConfiguration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        new Thread(() -> {
            connection = factory.createPeerConnection(rtcConfiguration, new DefaultObserver() {
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState);
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        log("ICE候选项收集完毕--------------------");
                        try {
                            // 将action、本地描述信息发送至对端
                            PacketWriter pw = new PacketWriter(commSocket);
                            JSONObject obj = new JSONObject();
                            obj.put("action", "connected");
                            obj.put("answer", connection.getLocalDescription().description);
                            byte[] encrypted = Crypto.encryptMessage(obj.toString(), contact.getPublicKey(), ownPublicKey, ownSecretKey);
                            if (encrypted != null) {
                                pw.writeMessage(encrypted);
                                reportStateChange(CallState.CONNECTED);
                            } else {
                                reportStateChange(CallState.ERROR);
                            }
                            // new Thread(new SpeakerRunnable(commSocket)).start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            reportStateChange(CallState.ERROR);
                        }
                    }
                }
                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    log("ICE连接状态发生改变--------------------" + iceConnectionState.name());
                    super.onIceConnectionChange(iceConnectionState);
                    if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED);
                    }
                }
                @Override
                public void onAddStream(MediaStream mediaStream) {
                    log("监听到媒体流添加事件--------------------");
                    super.onAddStream(mediaStream);
                    handleMediaStream(mediaStream);
                }
                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    super.onDataChannel(dataChannel);
                    RTCCall.this.dataChannel = dataChannel;
                    dataChannel.registerObserver(RTCCall.this);
                }
            });

            log("创建并配置PeerConnection对象，处理SDP描述信息--------------------");
            // connection.addStream(createStream());
            connection.addTrack(createVideoTrack());
            connection.addTrack(createAudioTrack());
            // this.dataChannel = connection.createDataChannel("data", new DataChannel.Init());
            log("设置远程描述信息--------------------");
            connection.setRemoteDescription(new DefaultSdpObserver() {
                // 设置远程描述信息
                @Override
                public void onSetSuccess() {
                    super.onSetSuccess();
                    log("创建Answer...--------------------");
                    // 创建Answer（应答），并将其作为回调参数传递给DefaultSdpObserver对象
                    // 成功创建Answer时，会调用SdpObserver对象的onCreateSuccess方法，将Answer作为参数传递给它
                    // Caller（发起呼叫的一方）需要将这个Answer发送给Callee（被呼叫的一方），从而完成连接的建立
                    connection.createAnswer(new DefaultSdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            log("Answer应答创建成功！！！--------------------");
                            super.onCreateSuccess(sessionDescription);
                            // 设置本地描述信息
                            connection.setLocalDescription(new DefaultSdpObserver(){
                                @Override
                                public void onSetFailure(String s) {
                                    super.onSetFailure(s);
                                    log("创建本地 Offer SDP失败！！！！！！--------------------" + s);
                                }
                            }, sessionDescription);
                        }
                        @Override
                        public void onCreateFailure(String s) {
                            super.onCreateFailure(s);
                            log("Answer应答创建失败！！！--------------------" + s);
                        }
                    }, constraints);
                }
            }, new SessionDescription(SessionDescription.Type.OFFER, offer));
        }).start();


        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
//                    log("------------------------------------------------------------------------------------------------------------------------");
//                    if (null != connection.getTransceivers()) {
//                        log("1111111111------------------------------------------------------------" + connection.getTransceivers().size());
//                    }
//                    if (null != connection.getSenders()) {
//                        log("2222222222------------------------------------------------------------" + connection.getSenders().size());
//                    }
//                    if (null != connection.getReceivers()) {
//                        log("3333333333------------------------------------------------------------" + connection.getReceivers().size());
//                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 呼出通话-startCall
    public static RTCCall startCall(Context context, MainService.MainBinder binder, Contact contact, OnStateChangeListener listener) {
        return new RTCCall(context, binder, contact, listener);
    }

    // 呼出通话-构造函数
    public RTCCall(Context context, MainService.MainBinder binder, Contact contact, OnStateChangeListener listener) {
        this.context = context;
        this.contact = contact;
        this.commSocket = null;
        this.listener = listener;
        this.binder = binder;
        this.ownPublicKey = binder.getSettings().getPublicKey();
        this.ownSecretKey = binder.getSettings().getSecretKey();
        log("RTCCall呼叫对象已创建--------------------");
        this.iceServers = new ArrayList<>();
        for (String server : binder.getSettings().getIceServers()) {
            this.iceServers.add(PeerConnection.IceServer.builder(server).createIceServer());
        }

        // 手动添加ice服务器
        String coturnServer = "192.168.1.6";
        String stunUrl = "stun:" + coturnServer + ":3478";
        String turnUrl = "turn:" + coturnServer + ":3478";         // 5349
        String username = "wej";
        String password = "123456";
//        this.iceServers.add(
//                PeerConnection.IceServer.builder(stunUrl)
//                        .setUsername(username)
//                        .setPassword(password)
//                        .createIceServer()
//        );
//        this.iceServers.add(
//                PeerConnection.IceServer.builder(turnUrl)
//                        .setUsername(username)
//                        .setPassword(password)
//                        .createIceServer()
//        );

//        coturnServer = "stun1.l.google.com";
//        stunUrl = "stun:" + coturnServer + ":19302";
//        this.iceServers.add(
//                PeerConnection.IceServer.builder(stunUrl)
//                        .setUsername(username)
//                        .setPassword(password)
//                        .createIceServer()
//        );

        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            context.setTheme(R.style.AppTheme_Dark);
        } else {
            context.setTheme(R.style.AppTheme_Light);
        }
        initRTC(context);
        activeCall();
    }

    // 主动呼出电话
    public void activeCall(){
        new Thread(() -> {
            PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(this.iceServers);
            // 创建PeerConnection对象
            connection = factory.createPeerConnection(rtcConfiguration, new DefaultObserver() {
                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    // 在这里可以将iceCandidate发送给对端
                    if (!iceCandidate.serverUrl.equals("")) {
                        log("======================================================================================================");
                        log("stun/turn服务器Url--------------------" + iceCandidate.serverUrl + " --- " + iceCandidate.sdpMid + " --- " + iceCandidate.sdpMLineIndex);
                        // log("SDP候选项描述信息--------------------" + iceCandidate.sdp);
                        // log("ICE候选项信息字符串--------------------" + iceCandidate.toString());
                        for (int i = 0; i <3; i++){
                            String pattern = "";
                            if (i == 0) pattern = "candidate:\\d+ \\d+ udp \\d+ (\\d+\\.\\d+\\.\\d+\\.\\d+) (\\d+) typ srflx raddr (\\d+\\.\\d+\\.\\d+\\.\\d+) rport (\\d+).*";
                            if (i == 1) pattern = "candidate:\\d+ \\d+ udp \\d+ (\\d+\\.\\d+\\.\\d+\\.\\d+) (\\d+) typ relay raddr (\\d+\\.\\d+\\.\\d+\\.\\d+) rport (\\d+).*";
                            if (i == 2) pattern = "candidate:\\d+ \\d+ udp \\d+ ([^\\s]+) (\\d+) typ srflx raddr ([^\\s]+) rport (\\d+).*";
                            Pattern regex = Pattern.compile(pattern);
                            Matcher matcher = regex.matcher(iceCandidate.sdp);
                            if (matcher.matches()) {
                                String localIp = matcher.group(1);
                                String localPort = matcher.group(2);
                                String remoteIp = matcher.group(3);
                                String remotePort = matcher.group(4);
                                log("建立通信连接的地址:端口--------------------" + localIp + ":" + localPort);
                                // 用于ICE服务器指定发送目标设备数据？？？
                                log("接收ICE服务器的地址:端口--------------------" + remoteIp + ":" + remotePort);
                                break;
                            }
                            if (i == 2) log("没有找到匹配项！！！--------------------");
                        }
                    }
                    // 当候选项信息收集、交换后
                    // 要与信令服务器请求，试图获得对端设备的ICE候选项信息
                    // 而后再建立音视频通话连接请求
                }
                // 表示ICE候选项已收集完毕时调用
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState);
                    // 当ICE候选项收集完成时，可以进行相应的操作
                    byte[] otherPublicKey = new byte[Sodium.crypto_sign_publickeybytes()];
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        log("转发Offer（SDP）描述信息...--------------------");
                        try {
                            commSocket = contact.createSocket();
                            if (commSocket == null) {
                                log("无法建立Socket连接--------------------");
                                reportStateChange(CallState.ERROR);
                                //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                return;
                            }
                            InetSocketAddress remote_address = (InetSocketAddress) commSocket.getRemoteSocketAddress();
                            log("呼出通话获得的远程网络地址--------------------" + remote_address);
                            contact.setLastWorkingAddress(new InetSocketAddress(remote_address.getAddress(), MainService.serverPort));
                            log("连接中...--------------------");
                            // 创建PacketReader对象
                            PacketReader pr = new PacketReader(commSocket);
                            PacketWriter pw = new PacketWriter(commSocket);
                            reportStateChange(CallState.CONNECTING);
                            // 匿名代码块，区分局部变量作用域
                            // 加密action、offer消息，发送给对方。
                            {
                                JSONObject obj = new JSONObject();
                                obj.put("action", "call");
                                obj.put("offer", connection.getLocalDescription().description);
                                byte[] encrypted = Crypto.encryptMessage(obj.toString(), contact.getPublicKey(), ownPublicKey, ownSecretKey);
                                if (encrypted == null) {
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    // RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return;
                                }
                                pw.writeMessage(encrypted);
                            }
                            // 阻塞-读取socket返回的第一个消息
                            {
                                byte[] response = pr.readMessage();
                                String decrypted = Crypto.decryptMessage(response, otherPublicKey, ownPublicKey, ownSecretKey);
                                if (decrypted == null || !Arrays.equals(contact.getPublicKey(), otherPublicKey)) {
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return;
                                }
                                JSONObject obj = new JSONObject(decrypted);
                                if (!obj.optString("action", "").equals("ringing")) {
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    // RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return;
                                }
                                log("振铃中...--------------------");
                                reportStateChange(CallState.RINGING);
                            }
                            // 阻塞-读取socket返回的第二个消息
                            {
                                byte[] response = pr.readMessage();
                                String decrypted = Crypto.decryptMessage(response, otherPublicKey, ownPublicKey, ownSecretKey);
                                if (decrypted == null || !Arrays.equals(contact.getPublicKey(), otherPublicKey)) {
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    return;
                                }
                                JSONObject obj = new JSONObject(decrypted);
                                String action = obj.getString("action");
                                if (action.equals("connected")) {
                                    reportStateChange(CallState.CONNECTED);
                                    // 调用handleAnswer方法处理"answer"的值？？？
                                    handleAnswer(obj.getString("answer"));
                                    // 联系人已接受接听电话
                                    // RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ACCEPTED);
                                } else if (action.equals("dismissed")) {
                                    closeCommSocket();
                                    reportStateChange(CallState.DISMISSED);
                                    // contact declined receiving call
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_DECLINED);
                                } else {
                                    log("意料之外的action回复--------------------" + action);
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                }
                            }
                        } catch (Exception e) {
                            closeCommSocket();
                            e.printStackTrace();
                            reportStateChange(CallState.ERROR);
                            // RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                        }
                    }
                }
                // 当ICE连接状态发生改变时调用
                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    log("ICE连接状态发生改变--------------------" + iceConnectionState.name());
                    super.onIceConnectionChange(iceConnectionState);
                    if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED);
                    }
                }
                // 通话中新增媒体流时调用
                @Override
                public void onAddStream(MediaStream mediaStream) {
                    log("发现有新添加的媒体流--------------------" + mediaStream.toString());
                    super.onAddStream(mediaStream);
                    // 处理添加的媒体流？
                    handleMediaStream(mediaStream);
                }
                // DataChannel可用时调用
                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    super.onDataChannel(dataChannel);
                    RTCCall.this.dataChannel = dataChannel;
                    // 注册DataChannel的观察者，RTCCall类自身
                    dataChannel.registerObserver(RTCCall.this);
                }
            });
            log("创建并配置PeerConnection对象，启动音视频流传输--------------------");
            // 创建流媒体，添加到PeerConnection对象中
            // connection.addStream(createStream());
            connection.addTrack(createVideoTrack());
            connection.addTrack(createAudioTrack());
//            connection.getStats(new RTCStatsCollectorCallback() {
//                @Override
//                public void onStatsDelivered(RTCStatsReport rtcStatsReport) {
//                    log("RTC状态报告！！！！！！--------------------");
//                    log("RTC状态报告getTimestampUs！！！！！！--------------------" + rtcStatsReport.getTimestampUs());
//                    log("RTC状态报告getStatsMap！！！！！！--------------------" + rtcStatsReport.getStatsMap().toString());
//                }
//            });

            // 创建DataChannel，并注册观察者
            this.dataChannel = connection.createDataChannel("data", new DataChannel.Init());
            this.dataChannel.registerObserver(this);
            // 创建一个offer，它是SDP的一种描述会话配置和流冒险的方式
            connection.createOffer(new DefaultSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    connection.setLocalDescription(new DefaultSdpObserver(){
                        @Override
                        public void onSetFailure(String s) {
                            super.onSetFailure(s);
                            //出错时的LOG:
                            //onSetFailure s=
                            //Failed to set local offer sdp:
                            //Failed to set local video description recv parameters for m-section with mid='video'.
                            log("创建本地 Offer SDP失败！！！--------------------" + s);
                        }
                    }, sessionDescription);
                }
            }, constraints);
        }).start();
    }

    // 提取NTP时间戳，将字节数组转换为long类型
    private static long extractNtpTimestamp(byte[] array, int offset) {
        long ntpTimestamp = 0;
        for (int i = 0; i < 8; i++) {
            ntpTimestamp = (ntpTimestamp << 8) | (array[offset + i] & 0xff);
        }
        return ntpTimestamp;
    }

    // handleMediaStream函数用于处理传入的MediaStream对象
    private void handleMediaStream(MediaStream stream) {
        log("传入媒体流，渲染音视频轨道数据--------------------");
        // 对是否有远程渲染器（remoteRenderer），是否存在视频轨道进行判断。
        if (this.remoteRenderer == null || stream.videoTracks.size() == 0) {
            return;
        }
        // 在主线程中执行相关操作
        // 初始化远程渲染器，并将视频轨道的内容显示在远程渲染器上。
        new Handler(Looper.getMainLooper()).post(() -> {
            // remoteRenderer.setBackgroundColor(Color.TRANSPARENT);
            remoteRenderer.init(this.sharedContext, null);
            stream.videoTracks.get(0).addSink(remoteRenderer);

            // 获取远程音频轨道
            // AudioTrack remoteAudioTrack = stream.audioTracks.get(0);
            // 添加自定义音频渲染器

            // 获取视频轨道数据
            VideoTrack videoTrack = stream.videoTracks.get(0);
            videoTrack.addSink(new VideoSink() {
                @Override
                public void onFrame(VideoFrame videoFrame) {
                    // 获取视频帧数据
                    VideoFrame.Buffer videoBuffer = videoFrame.getBuffer();
                    log("----------------------------------------------------------------------------------------------------------");
                    // 根据视频像素格式获取对应的像素数据
                    if (videoBuffer instanceof VideoFrame.TextureBuffer) {
                        VideoFrame.TextureBuffer textureBuffer = (VideoFrame.TextureBuffer) videoBuffer;
                        // 获取纹理ID和纹理变换矩阵等信息
                        int textureId = textureBuffer.getTextureId();
                        // float[] transformMatrix = textureBuffer.getTransformMatrix();
                        // log("textureId----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + textureId);
                        // log("TransformMatrix----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + textureBuffer.getTransformMatrix());
                        // 在OpenGL中使用纹理渲染
                        // ...
                    } else if (videoBuffer instanceof VideoFrame.I420Buffer) {
                        VideoFrame.I420Buffer i420Buffer = (VideoFrame.I420Buffer) videoBuffer;
                        // 获取视频的宽度、高度和像素数据
                        int width = i420Buffer.getWidth();
                        int height = i420Buffer.getHeight();
                        // byte[] yData = i420Buffer.getDataY();
                        // byte[] uData = i420Buffer.getDataU();
                        // byte[] vData = i420Buffer.getDataV();
                        // log("width----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + width);
                        // log("height----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + height);
                        // log("yData----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + i420Buffer.getStrideY());
                        // log("uData----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + i420Buffer.getStrideU());
                        // log("vData----AAAAAAAAAAAAAAAAAAAA-----------------------------------------------------" + i420Buffer.getStrideV());
                        // 处理像素数据
                        // ...
                    }
                }
            });
        });
    }

    // 创建MediaStream对象。
    private MediaStream createStream() {
        // 创建本地流（upStream）
        upStream = factory.createLocalMediaStream("stream1");
        // 使用factory.createVideoTrack方法创建视频轨道
        // 使用factory.createVideoSource方法创建视频源
        // 创建音频轨道
        AudioTrack audio = factory.createAudioTrack("audio1", factory.createAudioSource(new MediaConstraints()));
        upStream.addTrack(audio);

        // 创建视频轨道
        this.capturer = createCapturer();
        // ？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？？
        // VideoTrack video =  factory.createVideoTrack("video1", factory.createVideoSource(this.capturer));
        EglBase eglBase = EglBase.create();
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", eglBase.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(this.capturer.isScreencast());
        this.capturer.initialize(surfaceTextureHelper, this.context, videoSource.getCapturerObserver());
        VideoTrack video =  factory.createVideoTrack("video1", videoSource);
        upStream.addTrack(video);
        // this.capturer.startCapture(500, 500, 30);
        return upStream;
    }

    private MediaStreamTrack createVideoTrack() {
        this.capturer = createCapturer();
        // 创建视频轨道
        VideoTrack videoTrack = null;
        if (this.capturer != null){
            // 创建EglBase用于SurfaceTextureHelper
            EglBase eglBase = EglBase.create();
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", eglBase.getEglBaseContext());
            // 创建VideoSource
            VideoSource videoSource = factory.createVideoSource(this.capturer.isScreencast());
            // 初始化Capturer
            this.capturer.initialize(surfaceTextureHelper, this.context, videoSource.getCapturerObserver());
            // 获取VideoTrack
            videoTrack = factory.createVideoTrack("video1", videoSource);
            videoTrack.addSink(new VideoSink() {
                @Override
                public void onFrame(VideoFrame videoFrame) { }
            });
            videoTrack.setEnabled(true);
        }
        return videoTrack;
    }

    private MediaStreamTrack createAudioTrack() {
        // 创建音频轨道
        AudioTrack audioTrack = factory.createAudioTrack("audio1", factory.createAudioSource(new MediaConstraints()));
        return audioTrack;
    }

    private EglBase eglBase;
    private JavaAudioImpl javaAudio;
    public void setEglBase(EglBase eglBase) {
        this.eglBase = eglBase;
    }

    // 自定义的日志注入器类
    public class LogInjectableLogger implements Loggable {
        @Override
        public void onLogMessage(String tag, Logging.Severity severity, String message) {
            // 根据需要将日志信息输出到Android日志
            switch (severity) {
                case LS_INFO:
                    // log( "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + tag + message);
                    break;
                case LS_WARNING:
                    // log( "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + tag + message);
                    break;
                case LS_ERROR:
                    // log( "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" + tag + message);
                    break;
            }
        }
    }

    // 初始化WebRTC相关工厂和约束条件
    private void initRTC(Context c) {
        // 使用PeerConnectionFactory的initialize方法，传入一个Context对象，对PeerConnectionFactory进行初始化
        PeerConnectionFactory.initialize(
                PeerConnectionFactory
                        .InitializationOptions
                        .builder(c)
                        .setEnableInternalTracer(true)                                                // 启用内部跟踪器
                        .setInjectableLogger(new LogInjectableLogger(), Logging.Severity.LS_INFO)     // 自定义日志注入器
                        .createInitializationOptions()
        );
        // 使用PeerConnectionFactory的builder方法，创建PeerConnectionFactory对象。
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        // 此方式得重新参照4.0.4源码再做参考
        // EglBase.Context eglCtxRemote = EglBase.create().getEglBaseContext();
        // EglBase.Context eglCtxLocal = EglBase.create().getEglBaseContext();
        // DefaultVideoEncoderFactory enVdf = new DefaultVideoEncoderFactory(eglCtxRemote, true, true);
        // VideoDecoderFactory deVdf = new DefaultVideoDecoderFactory(eglCtxRemote);
        SoftwareVideoEncoderFactory encoderFactory = new SoftwareVideoEncoderFactory();
        SoftwareVideoDecoderFactory decoderFactory = new SoftwareVideoDecoderFactory();
        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(new JavaAudioImpl(this.context).getJavaAudioDeviceModule())
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        // 创建一个包含可选约束的MediaConstraints对象
        constraints = new MediaConstraints();
        // 定义是否接收音频数据
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        // 定义是否接收视频数据
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"));
        // 定义是否使用DTLS-SKIP协议来实现安全传输
        constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        // initVideoTrack();
    }

    // 处理应答的SDP描述？？？
    private void handleAnswer(String remoteDesc) {
        connection.setRemoteDescription(
                new DefaultSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        super.onSetSuccess();
                        log("onSetSuccess成功！！！--------------------");
                    }
                    @Override
                    public void onSetFailure(String s) {
                        super.onSetFailure(s);
                        log("onSetFailure失败！！！--------------------" + s);
                    }
                },
                new SessionDescription(SessionDescription.Type.ANSWER, remoteDesc)
        );
    }

    // 报告变更的通话状态，通知监听器响应更新
    private void reportStateChange(CallState state) {
        this.state = state;
        if (this.listener != null) {
            this.listener.OnStateChange(state);
        }
    }

    // 创建CameraVideoCapturer对象；选择“前置摄像头”作为视频采集设备
    private CameraVideoCapturer createCapturer() {
        // 通过Camera1Enumerator，获取可用的摄像头设备名称
        CameraEnumerator enumerator = new Camera1Enumerator();
        for (String name : enumerator.getDeviceNames()) {
            // 判断其是否为前置摄像头
            if (enumerator.isFrontFacing(name)) {
                // 使用该设备创建CameraVideoCapturer对象，否则继续循环查找下一个设备。
                return enumerator.createCapturer(name, null);
            }
        }
        return null;
    }

    @Override
    public void onBufferedAmountChange(long l) {}
    @Override
    public void onStateChange() {}
    @Override
    public void onMessage(DataChannel.Buffer buffer) {
        // 读取buffer中的数据
        byte[] data = new byte[buffer.data.remaining()];
        buffer.data.get(data);
        String s = new String(data);
        JSONObject object = null;
        try {
            log("接收Message，判断是否渲染远程视频--------------------" + s);
            object = new JSONObject(s);
            // 根据接收的消息，判断是否启用远程视频？
            if (object.has(StateChangeMessage)) {
                String state = object.getString(StateChangeMessage);
                switch (state) {
                    case CameraEnabledMessage:
                    case CameraDisabledMessage: {
                        setRemoteVideoEnabled(state.equals(CameraEnabledMessage));
                        break;
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setRemoteRenderer(SurfaceViewRenderer remoteRenderer) {
        this.remoteRenderer = remoteRenderer;
    }

    // 切换前置摄像头和后置摄像头？
    public void switchFrontFacing() {
        if (this.capturer != null) {
            this.capturer.switchCamera(null);
        }
    }

    private void setRemoteVideoEnabled(boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            // 如果enabled为true，则将远程渲染器（remoteRenderer）的背景颜色设置为透明。
            // 如果enabled为false，则获取当前主题的背景颜色，并将其设置为远程渲染器的背景颜色。
            if (enabled) {
                this.remoteRenderer.setBackgroundColor(Color.TRANSPARENT);
            } else {
                TypedValue typedValue = new TypedValue();
                Resources.Theme theme = this.context.getTheme();
                theme.resolveAttribute(R.attr.backgroundCardColor, typedValue, true);
                @ColorInt int color = typedValue.data;
                this.remoteRenderer.setBackgroundColor(color);
            }
        });
    }

    public boolean isVideoEnabled(){
        return this.videoEnabled;
    }

    public void setVideoEnabled(boolean enabled) {
        this.videoEnabled = enabled;
        try {
            // 调用capturer的startCapture方法启用本地视频，传入的参数分别为视频的宽度、高度和帧率。
            if (enabled) {
                this.capturer.startCapture(500, 500, 30);
            }
            // 调用capturer的stopCapture方法禁用本地视频。
            else {
                this.capturer.stopCapture();
            }
            // 向对端发送消息，通知远程端视频的开启或关闭状态。
            JSONObject object = new JSONObject();
            object.put(StateChangeMessage, enabled ? CameraEnabledMessage : CameraDisabledMessage);
            log("设置本地视频启用状态--------------------" + object);
            // dataChannel发送视频启用状态的字节数组
            dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(object.toString().getBytes()), false));
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 释放相机、远程/本地渲染器资源
    public void releaseCamera() {
        if (this.capturer != null) {
            try {
                this.capturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (this.remoteRenderer != null) {
            this.remoteRenderer.release();
        }
        if (this.localRenderer != null) {
            this.localRenderer.release();
        }
    }

    // 被动呼入 - 拒绝、中断通话？  （这里是否可以与hangUp()合并，即是否需要调用reportStateChange(CallState.ENDED);）
    public void decline() {
        // 新的线程执行中断操作，防止造成主线程阻塞。
        new Thread(() -> {
            try {
                log("被动呼入 - 正在拒绝、中断通话中...--------------------");
                // socket向对方发送中断通话的消息
                if (this.commSocket != null) {
                    PacketWriter pw = new PacketWriter(commSocket);
                    byte[] encrypted = Crypto.encryptMessage("{\"action\":\"dismissed\"}", this.contact.getPublicKey(), this.ownPublicKey, this.ownSecretKey);
                    pw.writeMessage(encrypted);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }).start();
    }

    // 清理RTCCall对象
    public void cleanup() {
        log("RTCCall - cleanup...--------------------");
        closeCommSocket();
        // 这里需要将connection关闭，防止在error情况下，if条件走不通，出现内存泄漏？
        closePeerConnection();
        if (this.upStream != null && state == CallState.CONNECTED) {
            /*
            for(AudioTrack track: this.upStream.audioTracks){
                track.setEnabled(false);
                track.dispose();
            }
            for(VideoTrack track : this.upStream.videoTracks) track.dispose();
            */
            closePeerConnection();
            // factory.dispose();
        }
    }

    // 主动呼出 - 中断通话
    public void hangUp() {
        log("主动呼出 - 正在中断通话中...--------------------");
        new Thread(() -> {
            try {
                if (this.commSocket != null) {
                    PacketWriter pw = new PacketWriter(this.commSocket);
                    byte[] encrypted = Crypto.encryptMessage("{\"action\":\"dismissed\"}", this.contact.getPublicKey(), this.ownPublicKey, this.ownSecretKey);
                    pw.writeMessage(encrypted);
                }
                closeCommSocket();
                closePeerConnection();
                reportStateChange(CallState.ENDED);
            } catch (IOException e) {
                e.printStackTrace();
                reportStateChange(CallState.ERROR);
            }
        }).start();
    }

    // 关闭与对方的socket通信
    private void closeCommSocket() {
        log("closeCommSocket--------------------");
        if (this.commSocket != null) {
            try {
                this.commSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.commSocket = null;
        }
    }

    // 关闭PeerConnection对象，
    private void closePeerConnection() {
        log("关闭PeerConnection对象--------------------");
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.connection = null;
        }
    }

    public interface OnStateChangeListener {
        void OnStateChange(CallState state);
    }

    /*
    private void initLocalRenderer() {
        if (this.localRenderer != null) {
            log("really initng " + (this.sharedContext == null));
            this.localRenderer.init(this.sharedContext, null);
            this.localCameraTrack.addSink(localRenderer);
            this.capturer.startCapture(500, 500, 30);
        }
    }
    private void initVideoTrack() {
        this.sharedContext = EglBase.create().getEglBaseContext();
        this.capturer = createCapturer(true);
        this.localCameraTrack = factory.createVideoTrack("video1", factory.createVideoSource(capturer));
    }
    */

    private void log(String s) {
        Log.d(this, s);
    }
}
