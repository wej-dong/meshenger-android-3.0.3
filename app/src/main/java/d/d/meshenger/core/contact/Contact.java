package d.d.meshenger.core.contact;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.libsodium.jni.Sodium;

import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import d.d.meshenger.main.MainService;
import d.d.meshenger.utils.Log;
import d.d.meshenger.utils.Utils;

// 联系人实体类
public class Contact implements Serializable {
    // 在线、离线、等待
    public enum State {
        ONLINE,
        OFFLINE,
        PENDING
    };
    // 联系人姓名、公钥、阻止、网络地址、在线状态
    private String name;
    private byte[] pubkey;
    private boolean blocked;
    private List<String> addresses;
    private State state = State.PENDING;
    // 最后一个工作地址（下次连接时，优先尝试此地址，初始化未知的联系人）
    private InetSocketAddress last_working_address = null;

    public Contact(String name, byte[] pubkey, List<String> addresses) {
        this.name = name;
        this.pubkey = pubkey;
        this.blocked = false;
        this.addresses = addresses;
    }

    private Contact() {
        this.name = "";
        this.pubkey = null;
        this.blocked = false;
        this.addresses = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getPublicKey() {
        return pubkey;
    }

    public boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    // 设置新的最后的地址，以便下次进行尝试连接
    public void setLastWorkingAddress(InetSocketAddress address) {
        log("设置最近访问的网络地址--------------------" + address);
        this.last_working_address = address;
    }

    public InetSocketAddress getLastWorkingAddress() {
        return this.last_working_address;
    }

    public List<String> getAddresses() {
        return this.addresses;
    }

    public void addAddress(String address) {
        if (address.isEmpty()) {
            return;
        }
        for (String addr: this.addresses) {
            if (addr.equalsIgnoreCase(address)) {
                return;
            }
        }
        this.addresses.add(address);
    }

    public List<InetSocketAddress> getAllSocketAddresses() {
        List<InetSocketAddress> addrs = new ArrayList<>();
        for (String address: this.addresses) {
            try {
                // 这里启用之前的置换MAC地址，而是直接采用IPV4/IPV6地址建立连接
                addrs.add(new InetSocketAddress(address, MainService.serverPort));
                /*
                if (Utils.isMAC(address)) {
                    log("address是MAC地址--------------------" + address);
                    addrs.addAll(Utils.getAddressPermutations(address, MainService.serverPort));
                } else {
                    log("address不是MAC地址！！！--------------------" + address);
                    log("address解析成网络地址--------------------" + address);
                    // 解析域名
                    addrs.add(Utils.parseInetSocketAddress(address, MainService.serverPort));
                }
                */
            } catch (Exception e) {
                log("该IP地址可能是无效地址--------------------" + address);
                e.printStackTrace();
            }
        }
        log("获取IP地址集合，准备用于尝试连接...--------------------" + addrs.toString());
        return addrs;
    }

    // 根据网络地址、超时时间，建立Socket连接
    public static Socket establishConnection(InetSocketAddress address, int timeout) {
        Socket socket = new Socket();
        try {
            // 建立超时连接
            socket.connect(address, timeout);
            return socket;
        } catch (SocketTimeoutException e) {
            // ignore
        } catch (ConnectException e) {
            // 设备在线，但未监听给定端口
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    // 创建与联系人的连接，尝试最后一个成功的地址；如果失败，则遍历所有地址
    public Socket createSocket() {
        Socket socket = null;
        int connectionTimeout = 300;
        // 先尝试最后一个成功的地址
        if (this.last_working_address != null) {
            log("尝试最后一个工作过的网络地址--------------------" + this.last_working_address);
            socket = Contact.establishConnection(this.last_working_address, connectionTimeout);
            if (socket != null) {
                return socket;
            }
        }
        List<InetSocketAddress> inetSocketAddresses = this.getAllSocketAddresses();
        for (InetSocketAddress address: inetSocketAddresses) {
            log("尝试网络地址连接--------------------'" + address.getHostName() + "', port: " + address.getPort());
            socket = Contact.establishConnection(address, connectionTimeout);
            if (socket != null) {
                log("连接成功，网络地址为：--------------------'" + address.getHostName() + "', port: " + address.getPort());
                return socket;
            } else{
                log("连接失败，准备尝试下一个！！！--------------------'" + address.getHostName() + "', port: " + address.getPort());
            }
        }
        return null;
    }

    public static JSONObject exportJSON(Contact contact, boolean all) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();
        object.put("name", contact.name);
        object.put("public_key", Utils.byteArrayToHexString(contact.pubkey));

        for (String address : contact.getAddresses()) {
            array.put(address);
        }
        object.put("addresses", array);
        if (all) {
            object.put("blocked", contact.blocked);
        }
        return object;
    }

    public static Contact importJSON(JSONObject object, boolean all) throws JSONException {
        Contact contact = new Contact();
        contact.name = object.getString("name");
        contact.pubkey = Utils.hexStringToByteArray(object.getString("public_key"));

        if (!Utils.isValidName(contact.name)) {
            throw new JSONException("Invalid Name.");
        }
        if (contact.pubkey == null || contact.pubkey.length != Sodium.crypto_sign_publickeybytes()) {
            throw new JSONException("Invalid Public Key.");
        }
        JSONArray array = object.getJSONArray("addresses");
        for (int i = 0; i < array.length(); i += 1) {
            contact.addAddress(array.getString(i).toUpperCase().trim());
        }
        if (all) {
            contact.blocked = object.getBoolean("blocked");
        }
        return contact;
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
