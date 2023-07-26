package d.d.meshenger.main;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.json.JSONObject;
import org.libsodium.jni.Sodium;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import d.d.meshenger.core.call.CallActivity;
import d.d.meshenger.core.call.CallEvent;
import d.d.meshenger.core.contact.Contact;
import d.d.meshenger.core.data.Database;
import d.d.meshenger.core.data.MySettings;
import d.d.meshenger.utils.Crypto;
import d.d.meshenger.utils.Log;
import d.d.meshenger.utils.PacketReader;
import d.d.meshenger.utils.PacketWriter;
import d.d.meshenger.core.call.RTCCall;

public class MainService extends Service implements Runnable {
    private Database db = null;
    private boolean first_start = false;
    private String database_path = "";
    private String database_password = "";
    public static final int serverPort = 10001;
    private ServerSocket server;
    private volatile boolean run = true;
    private RTCCall currentCall = null;
    private ArrayList<CallEvent> events = null;

    /**
     * 这段代码主要是在Service的创建时进行一些初始化操作，包括设置数据库路径和启动连接处理线程。同时，还创建一个事件列表对象用于后续存储事件。
     * 获取数据库路径：通过调用getFilesDir方法获取应用的私有文件目录，然后将数据库路径设置为目录下的database.bin文件。
     * 处理传入连接：创建一个新的线程并启动，用于处理手机端的连接请求。
     * 初始化事件列表：创建一个ArrayList对象events，用于存储事件。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        this.database_path = this.getFilesDir() + "/database.bin";
        // 新的线程，专门处理手机端传入的连接请求
        new Thread(this).start();
        events = new ArrayList<>();
    }

    /**
     * 加载数据库文件
     * 在加载数据库时，根据数据库文件是否存在的情况来确定是打开现有数据库还是创建新的数据库。
     * 在保存数据库时，将数据库对象存储到指定路径，并进行加密处理。这样可以保证数据的安全性和一致性。
     */
    private void loadDatabase() {
        try {
            // 如果数据库文件存在，则调用Database.load方法来打开现有数据库，并将结果保存在this.db变量中。
            // 同时，将first_start变量设置为false，表示不是首次启动。
            if ((new File(this.database_path)).exists()) {
                // 如果database已存在，打开文件
                this.db = Database.load(this.database_path, this.database_password);
                this.first_start = false;
            } else {
                // 如果数据库文件不存在，则创建一个新的数据库对象，并将结果保存在this.db变量中。
                // 同时，将first_start变量设置为true，表示首次启动。
                this.db = new Database();
                this.first_start = true;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 数据库保存
     * 通过调用Database.store方法来将数据库对象MainService.this.db保存到指定路径MainService.this.database_path中，
     * 并使用指定密码MainService.this.database_password对数据库进行加密。
     * 在保存过程中，如果发生异常，则通过e.printStackTrace()打印异常信息。
     */
    private void saveDatabase() {
        try {
            Database.store(MainService.this.database_path, MainService.this.db, MainService.this.database_password);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 销毁服务
     * 在Service销毁时完成一些必要的操作，如保存数据库、关闭服务器、更新联系人状态等。
     *
     * 设置run变量为false，用于停止某个线程
     * 判断数据库对象db是否为空。如果不为空，调用Database.store方法将数据库对象db保存到指定路径this.database_path中。
     * 判断数据库对象db、服务器对象server是否为空，并且服务器对象已绑定且未关闭。
     * 如果都不为空，创建一个表示状态变化的下线消息。遍历数据库中的联系人列表。
     * 最后销毁Database对象。
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        this.run = false;
        // database可能为null
        // 设置password以打开database
        if (this.db != null) {
            try {
                Database.store(this.database_path, this.db, this.database_password);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 关闭监听的socket，并发出离线通知
        if (this.db != null && this.server != null && this.server.isBound() && !this.server.isClosed()) {
            try {
                byte[] ownPublicKey = this.db.mySettings.getPublicKey();
                byte[] ownSecretKey = this.db.mySettings.getSecretKey();
                // 消息：连接状态
                String message = "{\"action\": \"status_change\", \"status\", \"offline\"}";
                // 遍历数据库中的联系人
                for (Contact contact : this.db.contacts) {
                    // 略过OFFLINE状态的联系人
                    if (contact.getState() == Contact.State.OFFLINE) {continue; }
                    // 对下线消息进行加密，转为byte数组
                    byte[] encrypted = Crypto.encryptMessage(message, contact.getPublicKey(), ownPublicKey, ownSecretKey);
                    if (encrypted == null) { continue; }
                    Socket socket = null;
                    try {
                        // 创建与联系人通信的socket
                        socket = contact.createSocket();
                        if (socket == null) { continue; }
                        // 创建一个PacketWriter对象，用于向socket写入数据
                        // 将加密后的消息，写入socket
                        PacketWriter pw = new PacketWriter(socket);
                        pw.writeMessage(encrypted);
                        socket.close();
                    } catch (Exception e) {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (Exception ee) {
                                // ignore
                            }
                        }
                    }
                }
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (this.db != null) {
            // 清空数据库内存
            this.db.onDestroy();
        }
    }

    // 保证Service在后台持续运行，Service类的回调确保再次唤起。
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // 用于处理客户端的连接请求
    private void handleClient(Socket client) {
        // 客户端公钥；本地私钥、公钥
        byte[] clientPublicKey = new byte[Sodium.crypto_sign_publickeybytes()];
        byte[] ownSecretKey = this.db.mySettings.getSecretKey();
        byte[] ownPublicKey = this.db.mySettings.getPublicKey();
        try {
            PacketWriter pw = new PacketWriter(client);
            PacketReader pr = new PacketReader(client);
            Contact contact = null;
            // 获取客户端的地址信息
            InetSocketAddress remote_address = (InetSocketAddress) client.getRemoteSocketAddress();
            log("传入的连接来自于--------------------" + remote_address);
            // 循环处理客户端发送的请求
            while (true) {
                // 通过PacketReader的readMessage()，从Socket中读取客户端发送的数据，存储至request数组。
                byte[] request = pr.readMessage();
                if (request == null) { break; }
                // 对读取到的请求数据进行解密操作，将客户端发送的加密数据解密为明文数据。
                // 解密时需要使用客户端的公钥clientPublicKey、自己的公钥ownPublicKey和私钥ownSecretKey。
                String decrypted = Crypto.decryptMessage(request, clientPublicKey, ownPublicKey, ownSecretKey);
                if (decrypted == null) {
                    log("handleClient-处理客户端请求数据解密失败--------------------");
                    break;
                }
                if (contact == null) {
                    // 遍历本地所有的联系人，寻找与客户端公钥匹配的联系人。
                    for (Contact c : this.db.contacts) {
                        if (Arrays.equals(c.getPublicKey(), clientPublicKey)) { contact = c; }
                    }
                    // 如果未找到匹配的联系人
                    // 并且设置了拦截未知联系人选项，拒绝当前呼叫。
                    if (contact == null && this.db.mySettings.getBlockUnknown()) {
                        if (this.currentCall != null) {
                            log("阻止未知的联系人 => 拒绝--------------------");
                            this.currentCall.decline();
                        }
                        break;
                    }
                    // 如果contact不为null，并且该联系人已被阻止（拉黑），则拒绝当前呼叫。
                    if (contact != null && contact.getBlocked()) {
                        if (this.currentCall != null) {
                            log("阻止的联系人 => 拒绝--------------------");
                            this.currentCall.decline();
                        }
                        break;
                    }
                    // 未知的来电者，创建一个新的Contact对象，并使用客户端的公钥进行初始化。
                    if (contact == null) {
                        contact = new Contact("", clientPublicKey.clone(), new ArrayList<>());
                    }
                }
                // 检查呼叫方的公钥是否发生了可疑的更改。
                if (!Arrays.equals(contact.getPublicKey(), clientPublicKey)) {
                    log("发现key更改的可疑行为--------------------");
                    continue;
                }
                // 将呼叫方的最后有效地址信息更新为当前连接的地址信息（呼叫方地址可能会随机变化）。
                contact.setLastWorkingAddress(new InetSocketAddress(remote_address.getAddress(), MainService.serverPort));
                // 通过解密后的明文数据中的action字段判断客户端的请求类型。
                JSONObject obj = new JSONObject(decrypted);
                String action = obj.optString("action", "");
                log("客户端请求类型：" + action + "--------------------");
                /**
                 * 1、如果action字段为"call"，表示发来呼叫请求。
                 * 创建一个新的RTCCall对象，该对象负责处理呼叫请求，并传入需要的参数，然后将对象赋值给currentCall。
                 * 向客户端发送通知，告知客户端接受了呼叫。创建一个Intent对象，并传入参数为ACTION_INCOMING_CALL和contact对象。然后启动CallActivity，以响应呼叫请求。
                 * 2、如果action字段为"ping"，表示有人想要知道我们是否在线。将联系人的状态设置为在线，并向客户端发送响应数据。
                 * 3、如果action字段为"status_change"，表示其发出状态变化的通知。根据请求中的status字段更新联系人的状态。
                 */
                switch (action) {
                    case "call": {
                        // 有人向我们发起通话。
                        log("向我们发起通话请求--------------------");
                        String offer = obj.getString("offer");
                        log(offer + "offer参数信息--------------------");
                        this.currentCall = new RTCCall(this, new MainBinder(), contact, client, offer);
                        // 回复：我方接受了通话请求。
                        byte[] encrypted = Crypto.encryptMessage("{\"action\":\"ringing\"}", contact.getPublicKey(), ownPublicKey, ownSecretKey);
                        pw.writeMessage(encrypted);
                        Intent intent = new Intent(this, CallActivity.class);
                        intent.setAction("ACTION_INCOMING_CALL");
                        intent.putExtra("EXTRA_CONTACT", contact);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return;
                    }
                    case "ping": {
                        log("有人试图知道我们是否在线--------------------");
                        setClientState(contact, Contact.State.ONLINE);
                        byte[] encrypted = Crypto.encryptMessage("{\"action\":\"pong\"}", contact.getPublicKey(), ownPublicKey, ownSecretKey);
                        pw.writeMessage(encrypted);
                        break;
                    }
                    case "status_change": {
                        log("有人试图通知我们，他已离线--------------------");
                        if (obj.optString("status", "").equals("offline")) {
                            setClientState(contact, Contact.State.OFFLINE);
                        } else {
                            log("收入未知的状态变更通知--------------------" + obj.getString("status"));
                        }
                    }
                }
            }
            log("连接结束，与请求方客户端断开连接--------------------");
            // 向本地组件发出，当前呼叫已被拒绝的广播。
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("call_declined"));
        } catch (Exception e) {
            e.printStackTrace();
            log("与请求方客户端断开连接（异常断开）--------------------");
            if (this.currentCall != null) {
                this.currentCall.decline();
            }
        }
        // 客户端公钥数据数组归零
        Arrays.fill(clientPublicKey, (byte) 0);
    }

    // 更新指定联系人的在线状态。
    private void setClientState(Contact contact, Contact.State state) {
        // 原来的代码似乎有误，传入的固定值Contact.State.ONLINE
        // contact.setState(Contact.State.ONLINE);
        contact.setState(state);
    }

    /**
     * 循环监听指定端口上的客户端连接请求。
     * 并通过多线程处理每个客户端的连接请求。
     * 在接受到客户端连接请求后，会创建一个新的线程来处理该连接。
     */
    @Override
    public void run() {
        try {
            // 不断阻塞，等到数据库准备就绪
            while (this.db == null && this.run) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }
            }
            server = new ServerSocket(serverPort);
            while (this.run) {
                try {
                    Socket socket = server.accept();
                    new Thread(() -> handleClient(socket)).start();
                } catch (IOException e) {
                    // ignore
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
            new Handler(getMainLooper()).post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            // 停止Service运行
            stopSelf();
        }
    }

    /**
     * 内部类MainBinder，继承Binder。
     * 建立MainService与其他对象之间的通信，为客户提供MainService的一些操作和数据的访问方法。
     *
     * MainBinder类中定义了一系列方法，用于获取当前呼叫的RTCCall对象、判断服务是否是第一次启动、通过公钥获取联系人的方法、
     * 通过名称获取联系人的方法、添加联系人的方法、删除联系人的方法、关闭服务的方法、获取数据库密码的方法、设置数据库密码的方法、获取数据库的方法、加载数据库的方法。
     */
    public class MainBinder extends Binder {
        // 返回当前呼叫的RTCCall对象
        public RTCCall getCurrentCall() {
            return currentCall;
        }
        // 判断应用服务是否是第一次启动
        public boolean isFirstStart() {
            return MainService.this.first_start;
        }
        // 通过公钥获取联系人
        public Contact getContactByPublicKey(byte[] pubKey) {
            for (Contact contact : MainService.this.db.contacts) {
                if (Arrays.equals(contact.getPublicKey(), pubKey)) {
                    return contact;
                }
            }
            return null;
        }
        // 通过名称获取联系人
        public Contact getContactByName(String name) {
            for (Contact contact : MainService.this.db.contacts) {
                if (contact.getName().equals(name)) {
                    return contact;
                }
            }
            return null;
        }
        // 添加联系人
        public void addContact(Contact contact) {
            db.addContact(contact);
            saveDatabase();
            LocalBroadcastManager.getInstance(MainService.this).sendBroadcast(new Intent("refresh_contact_list"));
        }
        // 删除联系人
        public void deleteContact(byte[] pubKey) {
            db.deleteContact(pubKey);
            saveDatabase();
            LocalBroadcastManager.getInstance(MainService.this).sendBroadcast(new Intent("refresh_contact_list"));
        }
        // 关闭服务
        public void shutdown() {
            MainService.this.stopSelf();
        }
        // 获取数据库密码
        public String getDatabasePassword() {
            return MainService.this.database_password;
        }
        // 设置数据库密码
        public void setDatabasePassword(String password) {
            MainService.this.database_password = password;
        }
        // 获取数据库对象
        public Database getDatabase() {
            return MainService.this.db;
        }
        // 加载数据库文件
        public void loadDatabase() {
            MainService.this.loadDatabase();
        }
        // 将给定database对象替换当前的数据库
        public void replaceDatabase(Database db) {
            if (db != null) {
                if (MainService.this.db == null) {
                    MainService.this.db = db;
                } else {
                    MainService.this.db = db;
                    saveDatabase();
                }
            }
        }
        // 创建一个新的线程
        // 向通讯录中的联系人发送ping消息
        public void pingContacts() {
            new Thread(
                    new PingRunnable(
                            MainService.this,
                            // 在一些特殊的场景下，会出现mainactivity或mainactivity.binder为null的情况，导致崩溃
                            getContactsCopy(),
                            getSettings().getPublicKey(),
                            getSettings().getSecretKey()
                    )
            ).start();
        }
        // 保存当前数据库
        public void saveDatabase() {
            MainService.this.saveDatabase();
        }
        // 获取当前数据库设置
        public MySettings getSettings() {
            return MainService.this.db.mySettings;
        }
        // 返回一个当前通讯录列表的克隆副本
        public List<Contact> getContactsCopy() {
            // 阻塞，崩溃时会null
            return new ArrayList<>(MainService.this.db.contacts);
        }
        // 向MainService的事件列表中添加一个呼叫事件。
        // 根据给定的联系人和事件类型创建一个呼叫事件对象，并将其添加到事件列表中。
        public void addCallEvent(Contact contact, CallEvent.Type type) {
            InetSocketAddress last_working = contact.getLastWorkingAddress();
            MainService.this.events.add(
                    new CallEvent(
                            contact.getPublicKey(),
                            (last_working != null) ? last_working.getAddress() : null,
                            type
                    )
            );
            LocalBroadcastManager.getInstance(MainService.this).sendBroadcast(new Intent("refresh_event_list"));
        }
        // 返回一个当前事件列表的克隆副本
        public List<CallEvent> getEventsCopy() {
            return new ArrayList<>(MainService.this.events);
        }
        // 清空MainService的事件列表
        public void clearEvents() {
            MainService.this.events.clear();
            LocalBroadcastManager.getInstance(MainService.this).sendBroadcast(new Intent("refresh_event_list"));
        }
    }

    /**
     * PingRunnable用于在一个新的线程中执行ping操作，用来测试通讯录中的联系人是否在线。
     * 通过使用MainBinder对象，获取具体联系人的对象并更新其状态。通过发送广播，通知其他组件更新联系人列表。
     */
    class PingRunnable implements Runnable {
        // 上下文对象；联系人列表；当前用户公钥；当前用户私钥；MainBinder对象，用于操作MainService
        Context context;
        private List<Contact> contacts;
        byte[] ownPublicKey;
        byte[] ownSecretKey;
        MainBinder binder;

        // 构造方法
        PingRunnable(Context context, List<Contact> contacts, byte[] ownPublicKey, byte[] ownSecretKey) {
            this.context = context;
            this.contacts = contacts;
            this.ownPublicKey = ownPublicKey;
            this.ownSecretKey = ownSecretKey;
            this.binder = new MainBinder();
        }

        // 根据给定公钥，获取联系人，并更新联系人状态
        private void setState(byte[] publicKey, Contact.State state) {
            Contact contact = this.binder.getContactByPublicKey(publicKey);
            if (contact != null) {
                contact.setState(state);
            }
        }

        // 对contacts列表中的每个联系人进行循环遍历，依次执行ping操作。
        @Override
        public void run() {
            for (Contact contact : contacts) {
                Socket socket = null;
                byte[] publicKey = contact.getPublicKey();
                try {
                    socket = contact.createSocket();
                    log("通过此Socket连接通道--------------------" + socket);
                    log("向联系人发出ping信号--------------------" + contact.getName());
                    if (socket == null) {
                        setState(publicKey, Contact.State.OFFLINE);
                        continue;
                    }
                    PacketWriter pw = new PacketWriter(socket);
                    PacketReader pr = new PacketReader(socket);
                    byte[] encrypted = Crypto.encryptMessage("{\"action\":\"ping\"}", publicKey, ownPublicKey, ownSecretKey);
                    if (encrypted == null) {
                        socket.close();
                        continue;
                    }
                    pw.writeMessage(encrypted);
                    byte[] request = pr.readMessage();
                    if (request == null) {
                        socket.close();
                        continue;
                    }
                    String decrypted = Crypto.decryptMessage(request, publicKey, ownPublicKey, ownSecretKey);
                    if (decrypted == null) {
                        log("ping请求，响应数据解密失败！！！--------------------");
                        socket.close();
                        continue;
                    }
                    JSONObject obj = new JSONObject(decrypted);
                    String action = obj.optString("action", "");
                    if (action.equals("pong")) {
                        log("ping请求，响应成功，用户在线--------------------");
                        setState(publicKey, Contact.State.ONLINE);
                    }
                    socket.close();
                } catch (Exception e) {
                    setState(publicKey, Contact.State.OFFLINE);
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception ee) {
                            // ignore
                        }
                    }
                    e.printStackTrace();
                }
            }
            log("广播发送refresh_contact_list消息--------------------");
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("refresh_contact_list"));
        }
    }

    /**
     * 返回MainBinder对象，将Service与调用方进行绑定。
     */
    @Nullable @Override
    public IBinder onBind(Intent intent) {
        return new MainBinder();
    }

    /**
     * 将指定的数据写入日志中，并添加当前类的名称作为标签。
     */
    private void log(String data) {
        Log.d(this, data);
    }
}
