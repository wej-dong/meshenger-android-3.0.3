package d.d.meshenger.main;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import d.d.meshenger.R;
import d.d.meshenger.core.contact.ContactListFragment;
import d.d.meshenger.core.contact.EventListFragment;
import d.d.meshenger.set.AboutActivity;
import d.d.meshenger.set.BackupActivity;
import d.d.meshenger.set.SettingsActivity;
import d.d.meshenger.utils.Log;

public class MainActivity extends MeshengerActivity implements ServiceConnection {
    public MainService.MainBinder binder;
    private ViewPager mViewPager;
    private ContactListFragment contactListFragment;
    private EventListFragment eventListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 使用sections adapter设置ViewPager
        mViewPager = findViewById(R.id.container);
        TabLayout tabLayout = findViewById(R.id.tabs);
        // 将mViewPager与tabLayout关联起来，实现选项卡导航的功能。
        tabLayout.setupWithViewPager(mViewPager);
        contactListFragment = new ContactListFragment();
        eventListFragment = new EventListFragment();
        // 请求录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }
        // 监听refresh_event_list、refresh_contact_list两个广播。
        LocalBroadcastManager.getInstance(this).registerReceiver(refreshEventListReceiver, new IntentFilter("refresh_event_list"));
        LocalBroadcastManager.getInstance(this).registerReceiver(refreshContactListReceiver, new IntentFilter("refresh_contact_list"));

        com.example.myapp.MainActivity mainActivity = new com.example.myapp.MainActivity();
        log("---------------------------------------------------------------------" + mainActivity.getHelloString());
    }

    @Override
    protected void onDestroy() {
        log("onDestroy--------------------");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshEventListReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshContactListReceiver);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        log("绑定Service--------------------");
        this.binder = (MainService.MainBinder) iBinder;
        // 避免语言发生变化？？？
        SectionsPageAdapter adapter = new SectionsPageAdapter(getSupportFragmentManager());
        adapter.addFragment(contactListFragment, this.getResources().getString(R.string.title_contacts));
        adapter.addFragment(eventListFragment, this.getResources().getString(R.string.title_history));
        // 将adapter设置为ViewPager的适配器
        mViewPager.setAdapter(adapter);
        contactListFragment.onServiceConnected();
        eventListFragment.onServiceConnected();
        // 在这里调用它，因为EventListFragment.onResume被触发了两次
        // this.binder.loadDatabase();
        this.binder.pingContacts();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        log("解绑Service--------------------");
        this.binder = null;
    }

    // OptionsMenu的选项点击事件处理方法
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log("触发onOptionsItemSelected事件--------------------");
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                break;
            }
            case R.id.action_backup: {
                startActivity(new Intent(this, BackupActivity.class));
                break;
            }
            case R.id.action_about: {
                startActivity(new Intent(this, AboutActivity.class));
                break;
            }
        }
        // 调用父类onOptionsItemSelected()方法，以便父类执行一些默认操作。
        return super.onOptionsItemSelected(item);
    }

    // 刷新历史事件列表接收器
    private BroadcastReceiver refreshEventListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            eventListFragment.refreshEventList();
        }
    };

    // 刷新联系人列表接收器
    private BroadcastReceiver refreshContactListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            contactListFragment.refreshContactList();
        }
    };

    @Override
    protected void onResume() {
        log("OnResume--------------------");
        super.onResume();
        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        log("onPause--------------------");
        super.onPause();
        unbindService(this);
    }

    // 处理APP请求权限时的回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 如果第一个元素不等于PERMISSION_GRANTED，即权限未被授予。
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // 创建选项菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        log("onCreateOptionsMenu--------------------");
        // 调用getMenuInflater()方法获取一个MenuInflater对象。
        // 使用inflate()方法将menu_main_activity菜单资源文件填充到参数menu中。
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return true;
    }

    /**
     * 静态内部类SectionsPageAdapter
     * 用于管理ViewPager中的Fragment列表。
     */
    public static class SectionsPageAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();
        // 将Fragment和它对应的标题添加到mFragmentList和mFragmentTitleList中。
        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }
        public SectionsPageAdapter(FragmentManager fm) {
            super(fm);
        }
        // 可以根据指定位置返回对应的标题。
        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
        // 可以根据指定位置返回对应的Fragment。
        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }
        // 可以返回Fragment列表的大小。
        @Override
        public int getCount() {
            return mFragmentList.size();
        }
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
