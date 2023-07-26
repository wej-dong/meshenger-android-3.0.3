package d.d.meshenger.main;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;

import d.d.meshenger.R;

/**
 * 应用夜间模式对的Activity基类
 */
public class MeshengerActivity extends AppCompatActivity {
    boolean dark_active; // 深色模式

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark_active = darkModeEnabled();
        setTheme(dark_active ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    private boolean darkModeEnabled() {
        return (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 重现活动，应用模式
        boolean dark_active_now = darkModeEnabled();
        if (dark_active != dark_active_now) {
            dark_active = dark_active_now;
            recreate();
        }
    }
}
