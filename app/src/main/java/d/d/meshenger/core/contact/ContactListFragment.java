package d.d.meshenger.core.contact;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;

import org.json.JSONException;

import java.util.List;

import d.d.meshenger.core.call.CallActivity;
import d.d.meshenger.utils.Log;
import d.d.meshenger.R;
import d.d.meshenger.main.MainActivity;

import static android.os.Looper.getMainLooper;

public class ContactListFragment extends Fragment implements AdapterView.OnItemClickListener {
    private ListView contactListView;
    private boolean fabExpanded = false;
    private FloatingActionButton fabScan;
    private FloatingActionButton fabGen;
    private FloatingActionButton fab;
    private MainActivity mainActivity;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        log("onCreateView");
        View view = inflater.inflate(R.layout.fragment_contact_list, container, false);

        mainActivity = (MainActivity) getActivity();
        fab = view.findViewById(R.id.fab);
        fabScan = view.findViewById(R.id.fabScan);
        fabGen = view.findViewById(R.id.fabGenerate);
        contactListView = view.findViewById(R.id.contactList);

        fabScan.setOnClickListener(v -> startActivity(new Intent(this.mainActivity, QRScanActivity.class)));
        fabGen.setOnClickListener(v -> startActivity(new Intent(this.mainActivity, QRShowActivity.class)));
        fab.setOnClickListener(this::runFabAnimation);

        return view;
    }

    public void onServiceConnected() {
        refreshContactList();
    }

    private void runFabAnimation(View fab) {
        log("runFabAnimation");
        AnimationSet scanSet = new AnimationSet(this.mainActivity, null);
        AnimationSet generateSet = new AnimationSet(this.mainActivity, null);

        final int distance = 200;
        final int duration = 300;
        TranslateAnimation scanAnimation;
        TranslateAnimation generateAnimnation;
        AlphaAnimation alphaAnimation;

        if (fabExpanded) {
            scanAnimation = new TranslateAnimation(0, 0, -distance, 0);
            generateAnimnation = new TranslateAnimation(0, 0, -distance * 2, 0);
            alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
            ((FloatingActionButton) fab).setImageResource(R.drawable.qr_glass);
            fabScan.setY(fabScan.getY() + distance);
            fabGen.setY(fabGen.getY() + distance * 2);
        } else {
            scanAnimation = new TranslateAnimation(0, 0, distance, 0);
            generateAnimnation = new TranslateAnimation(0, 0, distance * 2, 0);
            alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            ((FloatingActionButton) fab).setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            fabScan.setY(fabScan.getY() - distance);
            fabGen.setY(fabGen.getY() - distance * 2);
        }

        scanSet.addAnimation(scanAnimation);
        scanSet.addAnimation(alphaAnimation);
        scanSet.setFillAfter(true);

        generateSet.addAnimation(generateAnimnation);
        generateSet.addAnimation(alphaAnimation);
        generateSet.setFillAfter(true);

        scanSet.setDuration(duration);
        generateSet.setDuration(duration);

        fabScan.setVisibility(View.VISIBLE);
        fabGen.setVisibility(View.VISIBLE);

        fabScan.startAnimation(scanSet);
        fabGen.startAnimation(generateSet);

        fabExpanded = !fabExpanded;
    }

    // 折叠Fab功能栏
    private void collapseFab() {
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass);
            fabScan.clearAnimation();
            fabGen.clearAnimation();
            fabScan.setY(fabScan.getY() + 200);
            fabGen.setY(fabGen.getY() + 200 * 2);
            fabExpanded = false;
        }
    }

    private void showDeleteDialog(byte[] publicKey, String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mainActivity);

        builder.setTitle(R.string.confirm);
        builder.setMessage(getResources().getString(R.string.contact_remove) + " " + name);
        builder.setCancelable(false); // prevent key shortcut to cancel dialog

        builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
            this.mainActivity.binder.deleteContact(publicKey);
            dialog.cancel();
        });

        builder.setNegativeButton(R.string.no, (DialogInterface dialog, int id) -> {
            dialog.cancel();
        });

        // create dialog box
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * 刷新联系人列表？？？
     */
    public void refreshContactList() {
        // log("刷新联系人列表--------------------");
        if (this.mainActivity == null || this.mainActivity.binder == null) {
            log("1-初始化未完成，尝试加载后刷新--------------------");
            new Thread(() -> {
                log("等待...加载完成后，再刷新联系人列表--------------------");
                while (true){
                    if (this.mainActivity != null && this.mainActivity.binder != null){
                        break;
                    }
                }
                refreshContactList();
            }).start();
            return;
        }
        // 创建一个Handler对象，并传入主线程的Looper，使用post方法在主线程中执行一段代码。
        List<Contact> contacts = ContactListFragment.this.mainActivity.binder.getContactsCopy();
        new Handler(getMainLooper()).post(() -> {
            log("刷新联系人列表，列表数量：--------------------" + contacts.size());
            // 创建ContactListAdapter对象，将联系人列表和自定义布局，设置为适配器的参数，然后将适配器设置为contactListView的适配器。
            contactListView.setAdapter(new ContactListAdapter(ContactListFragment.this.mainActivity, R.layout.item_contact, contacts));
            // 设置contactListView的点击监听器
            contactListView.setOnItemClickListener(ContactListFragment.this);
            // 设置contactListView的长按监听器
            contactListView.setOnItemLongClickListener((AdapterView<?> adapterView, View view, int i, long l) -> {
                Contact contact = contacts.get(i);
                PopupMenu menu = new PopupMenu(ContactListFragment.this.mainActivity, view);
                Resources res = getResources();
                String delete = res.getString(R.string.delete);
                String rename = res.getString(R.string.rename);
                String block = res.getString(R.string.block);
                String unblock = res.getString(R.string.unblock);
                String share = res.getString(R.string.share);
                String qr = "QR-ify";

                menu.getMenu().add(delete);
                menu.getMenu().add(rename);
                menu.getMenu().add(share);
                if (contact.getBlocked()) {
                    menu.getMenu().add(unblock);
                } else {
                    menu.getMenu().add(block);
                }
                menu.getMenu().add(qr);

                menu.setOnMenuItemClickListener((MenuItem menuItem) -> {
                    String title = menuItem.getTitle().toString();
                    byte[] publicKey = contact.getPublicKey();
                    if (title.equals(delete)) {
                        showDeleteDialog(publicKey, contact.getName());
                    } else if (title.equals(rename)) {
                        showContactEditDialog(publicKey, contact.getName());
                    } else if (title.equals(share)) {
                        shareContact(contact);
                    } else if (title.equals(block)) {
                        setBlocked(publicKey, true);
                    } else if (title.equals(unblock)) {
                        setBlocked(publicKey, false);
                    } else if (title.equals(qr)) {
                        Intent intent = new Intent(ContactListFragment.this.mainActivity, QRShowActivity.class);
                        intent.putExtra("EXTRA_CONTACT", contact);
                        startActivity(intent);
                    }
                    return false;
                });
                menu.show();
                return true;
            });
        });
    }

    private void setBlocked(byte[] publicKey, boolean blocked) {
        Contact contact = this.mainActivity.binder.getContactByPublicKey(publicKey);
        if (contact != null) {
            contact.setBlocked(blocked);
            this.mainActivity.binder.addContact(contact);
        }
    }

    private void shareContact(Contact c) {
        log("shareContact");
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, Contact.exportJSON(c, false).toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showContactEditDialog(byte[] publicKey, String name) {
        log("showContactEditDialog--------------------");
        EditText et = new EditText(this.mainActivity);
        et.setText(name);
        AlertDialog dialog = new AlertDialog.Builder(this.mainActivity)
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(getResources().getString(R.string.cancel), null)
            .setPositiveButton(R.string.ok, (DialogInterface dialogInterface, int i) -> {
                String newName = et.getText().toString();
                if (newName.length() > 0) {
                    Contact contact = this.mainActivity.binder.getContactByPublicKey(publicKey);
                    if (contact != null) {
                        contact.setName(newName);
                        this.mainActivity.binder.addContact(contact);
                    }
                }
            }).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        collapseFab();
    }

    // 跳转呼叫页面
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        log("联系人列表点击事件--------------------");
        Contact contact = this.mainActivity.binder.getContactsCopy().get(i);
        Intent intent = new Intent(this.mainActivity, CallActivity.class);
        intent.setAction("ACTION_OUTGOING_CALL");
        intent.putExtra("EXTRA_CONTACT", contact);
        startActivity(intent);
    }

    private static void log(String s) {
        Log.d(ContactListFragment.class.getSimpleName(), s);
    }
}
