package d.d.meshenger.main;

import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDelegate;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

import java.util.ArrayList;

import d.d.meshenger.R;
import d.d.meshenger.core.data.MySettings;
import d.d.meshenger.set.AddressEntry;
import d.d.meshenger.utils.Log;
import d.d.meshenger.utils.Utils;

/**
 * 显示启动屏幕、名称设置对话框、数据库密码对话框
 * 并在启动MainActivity之前启动后台服务
 */
public class StartActivity extends MeshengerActivity implements ServiceConnection {
    private MainService.MainBinder binder;
    private int startState = 0;
    private static Sodium sodium;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        // 加载libsodium库以便JNI访问
        this.sodium = NaCl.sodium();
        Typeface type = Typeface.createFromAsset(getAssets(), "rounds_black.otf");
        ((TextView) findViewById(R.id.splashText)).setTypeface(type);
        // 启动MainService，并通过onServiceConnected回调
        startService(new Intent(this, MainService.class));
    }

    /**
     * 应用程序初始化。
     * 在进入主界面之前，完成所有必要的配置和数据准备。
     */
    private void continueInit()  {
        this.startState += 1;
        switch (this.startState) {
            case 1:
                log("init 1: 加载数据库--------------------");
                // 无密码打开
                this.binder.loadDatabase();
                continueInit();
                break;
            case 2:
                log("init 2: 检查数据库--------------------");
                if (this.binder.getDatabase() == null) {
                    // 数据库可能已加密
                    showDatabasePasswordDialog();
                } else {
                    continueInit();
                }
                break;
            case 3:
                log("init 3: 检查名称--------------------");
                if (this.binder.getSettings().getUsername().isEmpty()) {
                    // 设置名称
                    showMissingUsernameDialog();
                } else {
                    continueInit();
                }
                break;
            case 4:
                log("init 4: 检查密钥对--------------------");
                if (this.binder.getSettings().getPublicKey() == null) {
                    // 生成密钥对
                    initKeyPair();
                }
                continueInit();
                break;
            case 5:
                log("init 5: 检查网络地址--------------------");
                if (this.binder.isFirstStart()) {
                    showMissingAddressDialog();
                } else {
                    continueInit();
                }
                break;
            case 6:
               log("init 6: 启动联系人列表--------------------");
                // 设置夜间模式
                boolean nightMode = this.binder.getSettings().getNightMode();
                AppCompatDelegate.setDefaultNightMode(nightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                // 全部完成-显示联系人列表
                startActivity(new Intent(this, MainActivity.class));
                finish();
                break;
        }
    }

    /**
     * 与服务建立绑定初始化
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;
        log("服务绑定成功--------------------");
        if (this.startState == 0) {
            if (this.binder.isFirstStart()) {
                // 延迟1s加载启始页
                (new Handler()).postDelayed(() -> {
                    continueInit();
                },1000);
            } else {
                continueInit();
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 自动绑定该服务。
        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(this);
    }

    // 创建并保存私钥、公钥对
    private void initKeyPair() {
        final byte[] publicKey = new byte[Sodium.crypto_sign_publickeybytes()];
        final byte[] secretKey = new byte[Sodium.crypto_sign_secretkeybytes()];
        Sodium.crypto_sign_keypair(publicKey, secretKey);
        MySettings mySettings = this.binder.getSettings();
        mySettings.setPublicKey(publicKey);
        mySettings.setSecretKey(secretKey);
        try {
            this.binder.saveDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 获取当前设备WI-FI接口的MAC地址
    private ArrayList<String> getMacOfDevice() {
        // 遍历Utils.collectAddresses()方法返回的地址列表，查找并返回与指定设备名称匹配的MAC地址。
        ArrayList<String> ipAddress = new ArrayList<>();
        for (AddressEntry ae : Utils.collectAddresses()) {
            if (Utils.isIP(ae.address)) {
                ipAddress.add(ae.address);
            }
        }
        log("遍历所有的网络地址（MAC、IP）--------------------" + ipAddress.toString());
        return ipAddress;
    }

    private void showMissingAddressDialog() {
        ArrayList<String> mac = getMacOfDevice();
        if (mac.size() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("设置网络地址");
            builder.setMessage("找不到您的WiFi卡地址。立即启用WiFi（不需要Internet）或稍后跳过进行配置。");
            // OK，重新检查MAC地址
            builder.setPositiveButton(R.string.ok, (DialogInterface dialog, int id) -> {
                showMissingAddressDialog();
                dialog.cancel();
            });
            // SKIP，继续执行，跳过网络地址配置
            builder.setNegativeButton(R.string.skip, (DialogInterface dialog, int id) -> {
                dialog.cancel();
                continueInit();
            });
            builder.show();
        } else {
            // 保存设备的IP-MAC地址
            for (String addMac: mac) {
                this.binder.getSettings().addAddress(addMac);
            }
            try {
                this.binder.saveDatabase();
            } catch (Exception e) {
                e.printStackTrace();
            }
            continueInit();
        }
    }

    // 打开设置名称的对话框
    private void showMissingUsernameDialog() {
        TextView tw = new TextView(this);
        tw.setText(R.string.name_prompt);
        tw.setTextSize(20);
        tw.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(tw);
        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        et.setSingleLine(true);
        layout.addView(et);
        layout.setPadding(40, 80, 40, 40);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.hello);
        builder.setView(layout);
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            this.binder.shutdown();
            finish();
        });
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        builder.setPositiveButton(R.string.next, (dialogInterface, i) -> {
            // Do Nothing，将重写此处的程序
        });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener((newDialog) -> {
            Button okButton = ((AlertDialog) newDialog).getButton(AlertDialog.BUTTON_POSITIVE);
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
                @Override
                public void afterTextChanged(Editable editable) {
                    okButton.setClickable(editable.length() > 0);
                    okButton.setAlpha(editable.length() > 0 ? 1.0f : 0.5f);
                }
            });
            okButton.setClickable(false);
            okButton.setAlpha(0.5f);
            if (et.requestFocus()) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
        //  重写程序（以便用于手动关闭对话框）
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((View v) -> {
            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);
            String username = et.getText().toString();
            if (Utils.isValidName(username)) {
                this.binder.getSettings().setUsername(username);
                try {
                    this.binder.saveDatabase();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // 关闭对话框
                dialog.dismiss();
                //dialog.cancel(); // needed?
                continueInit();
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 询问数据库密码
    private void showDatabasePasswordDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_database_password);
        EditText passwordEditText = dialog.findViewById(R.id.PasswordEditText);
        Button exitButton = dialog.findViewById(R.id.ExitButton);
        Button okButton = dialog.findViewById(R.id.OkButton);

        okButton.setOnClickListener((View v) -> {
            String password = passwordEditText.getText().toString();
            this.binder.setDatabasePassword(password);
            this.binder.loadDatabase();
            if (this.binder.getDatabase() == null) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            } else {
                // 关闭对话框
                dialog.dismiss();
                continueInit();
            }
        });
        exitButton.setOnClickListener((View v) -> {
            // 关闭应用程序
            dialog.dismiss();
            this.binder.shutdown();
            finish();
        });
        dialog.show();
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
