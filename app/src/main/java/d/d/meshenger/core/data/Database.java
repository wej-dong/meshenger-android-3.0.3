package d.d.meshenger.core.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

import d.d.meshenger.core.contact.Contact;
import d.d.meshenger.utils.Log;
import d.d.meshenger.utils.Utils;
import d.d.meshenger.utils.Crypto;

public class Database {
    // 设置项；联系人列表；当前数据库版本。
    public MySettings mySettings;
    public ArrayList<Contact> contacts;
    static String version = "3.0.3";

    public Database() {
        this.contacts = new ArrayList<>();
        this.mySettings = new MySettings();
    }

    public void addContact(Contact contact) {
        int idx = findContact(contact.getPublicKey());
        if (idx >= 0) {
            // 联系人存在，则替换
            this.contacts.set(idx, contact);
        } else {
            this.contacts.add(contact);
        }
    }

    public void deleteContact(byte[] publicKey) {
        int idx = this.findContact(publicKey);
        if (idx >= 0) {
            this.contacts.remove(idx);
        }
    }

    public int findContact(byte[] publicKey) {
        for (int i = 0; i < this.contacts.size(); i += 1) {
            if (Arrays.equals(this.contacts.get(i).getPublicKey(), publicKey)) {
                return i;
            }
        }
        return -1;
    }

    public void onDestroy() {
        // 清空内存
        if (this.mySettings.getSecretKey() != null) {
            Arrays.fill(this.mySettings.getSecretKey(), (byte) 0);
        }
        if (this.mySettings.getPublicKey() != null) {
            Arrays.fill(this.mySettings.getPublicKey(), (byte) 0);
        }
        for (Contact contact : this.contacts) {
            if (contact.getPublicKey() != null) {
                Arrays.fill(contact.getPublicKey(), (byte) 0);
            }
        }
    }

    public static Database load(String path, String password) throws IOException, JSONException {
        // 读取数据库文件
        byte[] data = Utils.readExternalFile(path);
        // 解密数据库
        if (password != null && password.length() > 0) {
            data = Crypto.decryptDatabase(data, password.getBytes());
            if (data == null) {
                throw new IOException("数据库密码错误--------------------");
            }
        }

        JSONObject obj = new JSONObject(new String(data, Charset.forName("UTF-8")));
        boolean upgraded = upgradeDatabase(obj.getString("version"), Database.version, obj);
        Database db = Database.fromJSON(obj);
        if (upgraded) {
            log("存储更新后的数据库--------------------");
            Database.store(path, db, password);
        }
        return db;
    }

    public static void store(String path, Database db, String password) throws IOException, JSONException {
        JSONObject obj = Database.toJSON(db);
        byte[] data = obj.toString().getBytes();
        // 加密数据库
        if (password != null && password.length() > 0) {
            data = Crypto.encryptDatabase(data, password.getBytes());
        }
        // 写入数据库文件
        Utils.writeExternalFile(path, data);
    }

    // 版本更新
    private static boolean upgradeDatabase(String from, String to, JSONObject obj) throws JSONException {
        if (from.equals(to)) {
            return false;
        }
        log("升级数据库，从" + from + "到" + to + "--------------------");
        // 2.0.0 => 2.1.0
        if (from.equals("2.0.0")) {
            // 添加“拒绝”字段 (added in 2.1.0)
            JSONArray contacts = obj.getJSONArray("contacts");
            for (int i = 0; i < contacts.length(); i += 1) {
                contacts.getJSONObject(i).put("blocked", false);
            }
            from = "2.1.0";
        }
        // 2.1.0 => 3.0.0
        if (from.equals("2.1.0")) {
            // 添加新的字段
            obj.getJSONObject("mySettings").put("ice_servers", new JSONArray());
            obj.getJSONObject("mySettings").put("development_mode", false);
            from = "3.0.0";
        }
        // 3.0.0 => 3.0.1
        // do nothing
        if (from.equals("3.0.0")) {
            from = "3.0.1";
        }
        // 3.0.1 => 3.0.2
        if (from.equals("3.0.1")) {
            // 修复设置名称中的拼音错误
            obj.getJSONObject("mySettings").put("night_mode", obj.getJSONObject("mySettings").getBoolean("might_mode"));
            from = "3.0.2";
        }
        // 3.0.2 => 3.0.3
        if (from.equals("3.0.2")) {
            from = "3.0.3";
        }
        obj.put("version", from);
        return true;
    }

    public static JSONObject toJSON(Database db) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", db.version);
        obj.put("mySettings", MySettings.exportJSON(db.mySettings));

        JSONArray contacts = new JSONArray();
        for (Contact contact : db.contacts) {
            contacts.put(Contact.exportJSON(contact, true));
        }
        obj.put("contacts", contacts);
        return obj;
    }

    public static Database fromJSON(JSONObject obj) throws JSONException {
        Database db = new Database();
        // 导入版本
        db.version = obj.getString("version");
        // 导入联系人
        JSONArray array = obj.getJSONArray("contacts");
        for (int i = 0; i < array.length(); i += 1) {
            db.contacts.add(
                Contact.importJSON(array.getJSONObject(i), true)
            );
        }
        // 导入设置项
        JSONObject settings = obj.getJSONObject("mySettings");
        db.mySettings = MySettings.importJSON(settings);
        return db;
    }

    private static void log(String s) {
        Log.d("Database", s);
    }
}
