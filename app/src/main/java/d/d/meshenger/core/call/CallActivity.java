package d.d.meshenger.core.call;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.webrtc.EglBase;

import java.io.IOException;

import d.d.meshenger.R;
import d.d.meshenger.core.contact.Contact;
import d.d.meshenger.main.MainService;
import d.d.meshenger.main.MeshengerActivity;
import d.d.meshenger.utils.Log;
import d.d.meshenger.utils.Utils;

public class CallActivity extends MeshengerActivity implements ServiceConnection, SensorEventListener {
    private TextView statusTextView;
    private TextView nameTextView;
    private MainService.MainBinder binder = null;
    private ServiceConnection connection;
    private RTCCall currentCall;        // 当前通话实例
    private boolean calledWhileScreenOff = false;       // 是否在屏幕关闭期间，呼入的来电
    private PowerManager powerManager;      // 设备电源管理类
    private PowerManager.WakeLock wakeLock;     // 控制设备屏幕的唤醒状态
    private PowerManager.WakeLock passiveWakeLock = null;      // 控制设备处于被动唤醒状态？
    private final long buttonAnimationDuration = 400;
    private final int CAMERA_PERMISSION_REQUEST_CODE =  2;
    private boolean permissionRequested = false;
    private Contact contact = null;
    private CallEvent.Type callEventType = null;        // 通话事件类型
    private Vibrator vibrator = null;       // 控制手机振动功能
    private Ringtone ringtone = null;       // 控制手机铃声功能
    private EglBase eglBase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);
        // 通话时保持屏幕打开（防止暂停应用程序和取消通话）
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        nameTextView = findViewById(R.id.callName);
        statusTextView = findViewById(R.id.callStatus);
        String action = getIntent().getAction();
        contact = (Contact) getIntent().getExtras().get("EXTRA_CONTACT");
        eglBase = EglBase.create();

        log("创建呼叫，呼叫类型为--------------------" + action);
        if ("ACTION_OUTGOING_CALL".equals(action)) {
            activeActionInit();
        } else if ("ACTION_INCOMING_CALL".equals(action)) {
            passiveActionInit();
        }
        // 设置切换视频流的开启和关闭？？？
        findViewById(R.id.videoStreamSwitch).setOnClickListener((button) -> {
           switchVideoEnabled((ImageButton)button);
        });
        // 切换前置摄像头和后置摄像头
        findViewById(R.id.frontFacingSwitch).setOnClickListener((button) -> {
            currentCall.switchFrontFacing();
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(declineBroadcastReceiver, new IntentFilter("call_declined"));
    }

    private void activeActionInit(){
        // ----------------------------------------呼出电话----------------------------------------
        callEventType = CallEvent.Type.OUTGOING_UNKNOWN;
        // 创建ServiceConnection对象，绑定到MainService
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                binder = (MainService.MainBinder) iBinder;
                // RTCCall启动通话
                currentCall = RTCCall.startCall(
                        CallActivity.this,
                        binder,
                        contact,
                        activeCallback
                        //findViewById(R.id.localRenderer)
                );
                currentCall.setEglBase(eglBase);
                // 为当前通话设置远程渲染器
                currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {}
        };
        if (contact.getName().isEmpty()) {
            nameTextView.setText(getResources().getString(R.string.unknown_caller));
        } else {
            nameTextView.setText(contact.getName());
        }
        // 将ServiceConnection绑定到MainService？？？
        bindService(new Intent(this, MainService.class), connection, 0);
        // 停止呼叫点击事件监听
        View.OnClickListener declineListener = view -> {
            currentCall.hangUp();
            callEventType = CallEvent.Type.OUTGOING_DECLINED;
            finish();
        };
        findViewById(R.id.callDecline).setOnClickListener(declineListener);
        startSensor();
    }

    private void passiveActionInit(){
        // ----------------------------------------呼入电话----------------------------------------
        callEventType = CallEvent.Type.INCOMING_UNKNOWN;
        // 判断来电是否在屏幕关闭的情况下接收到。这可能对后续的逻辑和处理产生影响，比如当屏幕关闭时是否需要唤醒屏幕等。
        calledWhileScreenOff = !((PowerManager) getSystemService(POWER_SERVICE)).isScreenOn();
        // 在来电接听过程中获取一个唤醒锁，以保持屏幕的点亮状态。这样可以避免在通话过程中屏幕自动关闭，提供更好的用户体验。
        passiveWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK, "meshenger:wakeup"
        );
        passiveWakeLock.acquire(10000);
        // 此处用于服务连接的ServiceConnection的是this，在this回调函数中对binder、currentCall进行了赋值
        connection = this;
        bindService(new Intent(this, MainService.class), this, 0);
        if (contact.getName().isEmpty()) {
            nameTextView.setText(getResources().getString(R.string.unknown_caller));
        } else {
            nameTextView.setText(contact.getName());
        }
        findViewById(R.id.callAccept).setVisibility(View.VISIBLE);
        startRinging();
        // 拒绝呼叫
        View.OnClickListener declineListener = view -> {
            stopRinging();
            log("拒绝呼叫...--------------------");
            currentCall.decline();
            if (passiveWakeLock != null && passiveWakeLock.isHeld()) {
                passiveWakeLock.release();
            }
            callEventType = CallEvent.Type.INCOMING_DECLINED;
            finish();
        };
        // 通话中-挂断电话
        View.OnClickListener hangupListener = view -> {
            // 此处应该不需要？
            stopRinging();
            log("挂断电话...--------------------");
            currentCall.decline();
            if (passiveWakeLock != null && passiveWakeLock.isHeld()) {
                passiveWakeLock.release();
            }
            callEventType = CallEvent.Type.INCOMING_ACCEPTED;
            finish();
        };
        // 接听电话
        View.OnClickListener acceptListener = view -> {
            stopRinging();
            log("接听电话...--------------------");
            findViewById(R.id.callAccept).setVisibility(View.GONE);
            try {
                // 为当前通话设置远程渲染器？？？
                currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
                //currentCall.setLocalRenderer(findViewById(R.id.localRenderer));
                currentCall.setEglBase(eglBase);
                currentCall.passiveAccept(passiveCallback);
                if (passiveWakeLock != null && passiveWakeLock.isHeld()) {
                    passiveWakeLock.release();
                }
                // 接通情况下，设置挂断按钮的点击监听器为hangupListener
                findViewById(R.id.callDecline).setOnClickListener(hangupListener);
                startSensor();
            } catch (Exception e) {
                e.printStackTrace();
                stopDelayed("尝试接听电话时出错--------------------");
                // 源代码放在这里似乎不太合适
                // findViewById(R.id.callAccept).setVisibility(View.GONE);
                callEventType = CallEvent.Type.INCOMING_ERROR;
            }
        };
        findViewById(R.id.callAccept).setOnClickListener(acceptListener);
        findViewById(R.id.callDecline).setOnClickListener(declineListener);
    }

    // 呼出电话-状态变化监听器
    private RTCCall.OnStateChangeListener activeCallback = callState -> {
        switch (callState) {
            case CONNECTING: {
                log("主动呼出-响应：连接中--------------------");
                setStatusText(getString(R.string.call_connecting));
                break;
            }
            case CONNECTED: {
                log("主动呼出-响应：已连接--------------------");
                new Handler(getMainLooper()).post( () -> findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE));
                setStatusText(getString(R.string.call_connected));
                break;
            }
            case DISMISSED: {
                log("主动呼出-响应：被拒绝--------------------");
                stopDelayed(getString(R.string.call_denied));
                break;
            }
            case RINGING: {
                log("主动呼出-响应：振铃中--------------------");
                setStatusText(getString(R.string.call_ringing));
                break;
            }
            case ENDED: {
                log("主动呼出-响应：通话已结束--------------------");
                stopDelayed(getString(R.string.call_ended));
                break;
            }
            case ERROR: {
                log("主动呼出-响应：呼叫出错--------------------");
                stopDelayed(getString(R.string.call_error));
                break;
            }
        }
    };

    // 呼入电话-状态变化监听器
    private RTCCall.OnStateChangeListener passiveCallback = callState -> {
        switch (callState) {
            case CONNECTED: {
                log("被动呼入-响应：已连接--------------------");
                setStatusText(getString(R.string.call_connected));
                runOnUiThread(() -> findViewById(R.id.callAccept).setVisibility(View.GONE));
                new Handler(getMainLooper()).post(() -> findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE));
                break;
            }
            case RINGING: {
                log("被动呼入-响应：振铃中--------------------");
                setStatusText(getString(R.string.call_ringing));
                break;
            }
            case ENDED: {
                log("被动呼入-响应：振铃已结束--------------------");
                stopDelayed(getString(R.string.call_ended));
                break;
            }
            case ERROR: {
                log("被动呼入-响应：呼叫出错--------------------");
                stopDelayed(getString(R.string.call_error));
                break;
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;
        this.currentCall = this.binder.getCurrentCall();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要相机权限才能启动视频！！！", Toast.LENGTH_SHORT).show();
                return;
            }
            switchVideoEnabled(findViewById(R.id.videoStreamSwitch));
        }
    }

    // 切换视频功能的开关
    private void switchVideoEnabled(ImageButton button) {
        if (!Utils.hasCameraPermission(this)) {
            Utils.requestCameraPermission(this, CAMERA_PERMISSION_REQUEST_CODE);
            permissionRequested = true;
            return;
        }
        // 切换视频开关
        currentCall.setVideoEnabled(!currentCall.isVideoEnabled());
        // 图标缩放动画
        ScaleAnimation animation = new ScaleAnimation(1.0f, 0.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(buttonAnimationDuration / 2);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                button.setImageResource(currentCall.isVideoEnabled() ? R.drawable.baseline_camera_alt_black_off_48 : R.drawable.baseline_camera_alt_black_48);
                Animation a = new ScaleAnimation(0.0f, 1.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                a.setDuration(buttonAnimationDuration / 2);
                button.startAnimation(a);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        // 设置图标是否可见
        View frontSwitch = findViewById(R.id.frontFacingSwitch);
        if (currentCall.isVideoEnabled()){
            frontSwitch.setVisibility(View.VISIBLE);
            Animation scale = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(buttonAnimationDuration);
            frontSwitch.startAnimation(scale);
        } else {
            Animation scale = new ScaleAnimation(1f, 0f, 1f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(buttonAnimationDuration);
            scale.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    findViewById(R.id.frontFacingSwitch).setVisibility(View.GONE);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            frontSwitch.startAnimation(scale);
        }
        button.startAnimation(animation);
    }

    private void startRinging(){
        log("启动振铃--------------------");
        int ringerMode = ((AudioManager) getSystemService(AUDIO_SERVICE)).getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return;
        }
        vibrator = ((Vibrator) getSystemService(VIBRATOR_SERVICE));
        long[] pattern = {1500, 800, 800, 800};
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            VibrationEffect vibe = VibrationEffect.createWaveform(pattern, 0);
            vibrator.vibrate(vibe);
        } else {
            vibrator.vibrate(pattern, 0);
        }
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return;
        }
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_RINGTONE));
        ringtone.play();
    }

    private void stopRinging(){
        log("停止振铃--------------------");
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (ringtone != null){
            ringtone.stop();
            ringtone = null;
        }
    }

    private void setStatusText(String text) {
        new Handler(getMainLooper()).post(() ->
                statusTextView.setText(text)
        );
    }

    private void stopDelayed(String message) {
        new Handler(getMainLooper()).post(() -> {
            statusTextView.setText(message);
            new Handler().postDelayed(this::finish, 2000);
        });
    }

    // 开启屏幕接触感应器,当接近传感器时关闭屏幕
    private void startSensor() {
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        // 创建PowerManager的WakeLock实例，通过调用acquire()方法来获取WakeLock锁。以便在屏幕接近传感器检测到物体时保持屏幕关闭状态。
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "meshenger:proximity");
        wakeLock.acquire();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        log("传感器感应数据--------------------" + sensorEvent.values[0]);
        if (sensorEvent.values[0] == 0.0f) {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "meshenger:tag");
            wakeLock.acquire();
        } else {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "meshenger:tag");
            wakeLock.acquire();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    BroadcastReceiver declineBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("接收到广播，通话已取消！！！--------------------");
            finish();
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (calledWhileScreenOff) {
            calledWhileScreenOff = false;
            return;
        }
        if (permissionRequested){
            permissionRequested = false;
            return;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log("onDestroy--------------------");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(declineBroadcastReceiver);
        stopRinging();
        if (currentCall.state == RTCCall.CallState.CONNECTED) {
            currentCall.decline();
        }
        currentCall.cleanup();
        // 将此次通话加入历史事件列表
        this.binder.addCallEvent(this.contact, this.callEventType);
        // if (binder != null) {
        // unbindService(connection);
        // }
        // 判断connection是否为null？
        unbindService(connection);
        if (wakeLock != null) {
            wakeLock.release();
        }
        if (currentCall != null && currentCall.commSocket != null && currentCall.commSocket.isConnected() && !currentCall.commSocket.isClosed()) {
            try {
                log("关闭Socket连接--------------------");
                currentCall.commSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        currentCall.releaseCamera();
        eglBase.release();
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
