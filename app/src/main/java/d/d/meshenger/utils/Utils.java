package d.d.meshenger.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import d.d.meshenger.set.AddressEntry;

// 工具类
public class Utils {
    // 判断是否具有读取外部存储权限
    public static boolean hasReadPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }
    // 判断是否具有写入外部存储权限
    public static boolean hasWritePermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }
    // 判断是否具有相机权限
    public static boolean hasCameraPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }
    // 请求相机权限
    public static void requestCameraPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, request_code);
    }
    // 请求读取权限
    public static void requestReadPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, request_code);
    }
    // 请求写入权限
    public static void requestWritePermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, request_code);
    }
    // 批量判断权限检查结果，即是否全部被授予
    public static boolean allGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    // 获取应用的版本号
    public static String getApplicationVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }
    // 将字符串列表，用指定的分隔符进行拼接
    public static String join(List<String> list) {
        return TextUtils.join(", ", list);
    }
    // 将字符串按指定分隔符进行分割
    public static List<String> split(String str) {
        String[] parts = str.split("\\s*,\\s*");
        return Arrays.asList(parts);
    }
    // 检查名称合法性
    private static final Pattern NAME_PATTERN = Pattern.compile("[\\w _-]+");
    public static boolean isValidName(String name) {
        // 是否为空
        if (name == null || name.length() == 0) {
            return false;
        }
        // 前后是否有空格
        if (!name.equals(name.trim())) {
            return false;
        }
        // 判断名称是否只包含字母、数字、空格、下划线和短横线，如果有特殊字符，返回false
        return NAME_PATTERN.matcher(name).matches();
    }
    // 将字节数组转换为十六进制的字符串
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j += 1) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    // 将十六进制字符串转换为字节数组
    public static byte[] hexStringToByteArray(String str) {
        if (str == null) {
            return new byte[0];
        }
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return data;
    }
    // 字符串addr解析成网络地址，返回InetSocketAddress
    public static InetSocketAddress parseInetSocketAddress(String addr, int defaultPort) {
        if (addr == null || addr.length() == 0) {
            return null;
        }
        int firstColon = addr.indexOf(':');
        int lastColon = addr.lastIndexOf(':');
        int port = -1;
        try {
            // 解析端口后缀
            if (firstColon > 0 && lastColon > 0) {
                if (addr.charAt(lastColon - 1) == ']' || firstColon == lastColon) {
                    port = Integer.parseInt(addr.substring(lastColon + 1));
                    addr = addr.substring(0, lastColon);
                }
            }
            // 如果无法解析出端口号，则设置端口号为默认端口号。
            if (port < 0) {
                port = defaultPort;
            }
            return new InetSocketAddress(addr, port);
        } catch (Exception e) {
            return null;
        }
    }
    // 将字节数组转换成MAC地址字符串
    public static String bytesToMacAddress(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        // %02X：表示将整数值b转换为两位宽度的十六进制字符串，不足两位时在前面补0
        // “:”：表示在字符串末尾追加一个冒号
        for (byte b : mac) {
            sb.append(String.format("%02X:", b));
        }
        // 删除末尾的分隔符“：”
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
    // 将MAC地址字符串转换成字节数组
    public static byte[] macAddressToBytes(String mac) {
        String[] elements = mac.split(":");
        byte[] array = new byte[elements.length];
        for (int i = 0; i < elements.length; i += 1) {
            array[i] = Integer.decode("0x" + elements[i]).byteValue();
        }
        return array;
    }
    // 检查MAC地址是否为单播/多播
    public static boolean isMulticastMAC(byte[] mac) {
        return (mac[0] & 1) != 0;
    }
    // 检查MAC地址是否为本地/全局
    public static boolean isUniversalMAC(byte[] mac) {
        return (mac[0] & 2) == 0;
    }
    // 判断MAC地址是否合法
    public static boolean isValidMAC(byte[] mac) {
        // 我们忽略掉了MAC地址的第一个字节（伪mac地址设置了“本地”位设为0x02）
        return ((mac != null)
            && (mac.length == 6)
            && ((mac[1] != 0x0)&& (mac[2] != 0x0) && (mac[3] != 0x0) && (mac[4] != 0x0) && (mac[5] != 0x0))
        );
    }
    // 判断字符是否是十六进制
    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
    // 检查字符串是否为MAC地址（启发式判断）
    public static boolean isMAC(String address) {
        if (address == null || address.length() != 17) {
            return false;
        }
        for (int i : new int[]{0, 1, 3, 4, 6, 7, 9, 10, 12, 13, 15, 16}) {
            if (!isHexChar(address.charAt(i))) {
                return false;
            }
        }
        for (int i : new int[]{2, 5, 8, 11, 14}) {
            if (address.charAt(i) != ':') {
                return false;
            }
        }
        return true;
    }
    // 检查字符串是否为合法域名（启发式判断）
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-z0-9\\-.]+");
    public static boolean isDomain(String domain) {
        if (domain == null || domain.length() == 0) {
            return false;
        }
        if (domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }
        if (domain.contains("..") || !domain.contains(".")) {
            return false;
        }
        if (domain.startsWith("-") || domain.endsWith("-")) {
            return false;
        }
        if (domain.contains(".-") || domain.contains("-.")) {
            return false;
        }
        return DOMAIN_PATTERN.matcher(domain).matches();
    }
    // 检查字符串是否为合法IP地址（三选一）（启发式判断）
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    private static final Pattern IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");
    public static boolean isIP(String address) {
        return IPV4_PATTERN.matcher(address).matches()
            || IPV6_STD_PATTERN.matcher(address).matches()
            || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches();
    }
    // 收集正在运行的网络接口的所有IPV4/IPV6地址。
    public static List<AddressEntry> collectAddresses() {
        ArrayList<AddressEntry> addressList = new ArrayList<>();
        try {
            // 获取所有的网络接口，并通过循环遍历每一个网络接口
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif: all) {
                // log("遍历每一个网络接口AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------" + nif);
                // log("遍历每一个网络接口AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------" + Arrays.toString(nif.getHardwareAddress()));
                if (nif.isLoopback()) {
                    // log("该网络接口是回环接口AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------" + nif);
                    continue;
                }
                if (nif.getInterfaceAddresses().size() == 0){
                    // log("该网络接口没有接口地址！！！AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------");
                    continue;
                }
                // 每个网络接口，获取其硬件地址（即MAC地址）
                byte[] mac = nif.getHardwareAddress();
                if (!isValidMAC(mac)) {
                    // log("此网络接口的MAC地址无效--------------------" + nif.getName());
                    continue;
                }
                // 获取网络接口的所有接口地址（InterfaceAddress）
                for (InterfaceAddress ia: nif.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    // log("遍历网络接口的所有接口地址BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB--------------------" + addr);
                    if (addr.isLoopbackAddress()) {
                        // log("该接口地址是回环地址BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB--------------------" + addr);
                        continue;
                    }
                    // 判断网络地址是否是Inet4Address的实例
                    if (addr instanceof Inet4Address) {
                        // log("该网络地址是Inet4Address的实例CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------" + addr);
                        addressList.add(new AddressEntry(removeDeviceName(addr.getHostAddress()), nif.getName(), addr.isMulticastAddress()));
                    }
                    // 判断网络地址是否是Inet6Address的实例
                    else if (addr instanceof Inet6Address) {
                        // log("该网络地址是Inet6Address的实例CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------" + addr);
                        addressList.add(new AddressEntry(removeDeviceName(addr.getHostAddress()), nif.getName(), addr.isMulticastAddress()));
                    } else{
                        // log("该网络地址不是Inet4Address、Inet6Address的实例！！！BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB--------------------" + addr);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log("收集正在运行的所有网络地址集合--------------------" + addressList.toString());
        return addressList;
    }
    // 将获取到的ip中，可能出现的网络接口名称去除
    public static String removeDeviceName(String addr){
        if (addr.length() > 0) {
            if (addr.contains("%")) {
                return addr.substring(0, addr.indexOf("%"));
            }
        }
        return addr;
    }
    // 列出正在运行的网络接口的所有IP/MAC地址（用于调试）
    public static void printOwnAddresses() {
        for (AddressEntry ae: collectAddresses()) {
            log("调试：网络地址--------------------" + ae.address + " (" + ae.device + (ae.multicast ? ", multicast" : "") + ")");
        }
    }
    // 从IPv6地址中获取给定的MAC地址
    public static byte[] getEUI64MAC(Inet6Address addr6) {
        byte[] bytes = addr6.getAddress();
        if (bytes[11] != ((byte) 0xFF) || bytes[12] != ((byte) 0xFE)) {
            return null;
        }
        byte[] mac = new byte[6];
        mac[0] = (byte) (bytes[8] ^ 2);
        mac[1] = bytes[9];
        mac[2] = bytes[10];
        mac[3] = bytes[13];
        mac[4] = bytes[14];
        mac[5] = bytes[15];
        return mac;
    }
    /**
     * 创建一个使用EUI64方案的IPv6地址，并替换其中的MAC地址
     * 将EUi64方案IPv6地址的MAC地址，替换为另一个MAC地址。
     * 例如：("fe80::aaaa:aaff:faa:aaa"，"bb:bb:bb:bb:bb:bb") => "fe80::9bbb:bbff:febb:bbbb")
     */
    private static Inet6Address createEUI64Address(Inet6Address addr6, byte[] mac) {
        // addr6应为EUI64地址
        try {
            // 获取IPv6地址的字节数组，并根据mac中的字节值替换特定位置上的字节值
            byte[] bytes = addr6.getAddress();
            bytes[8] = (byte) (mac[0] ^ 2);
            bytes[9] = mac[1];
            bytes[10] = mac[2];
            // 已经设置，但不会造成损害
            bytes[11] = (byte) 0xFF;
            bytes[12] = (byte) 0xFE;
            bytes[13] = mac[3];
            bytes[14] = mac[4];
            bytes[15] = mac[5];
            // 返回新的Inet6Address对象，并使用bytes数组、addr6的范围标识符（scope ID）作为参数
            return Inet6Address.getByAddress(null, bytes, addr6.getScopeId());
        } catch (UnknownHostException e) {
            return null;
        }
    }
    /**
     * 获取带有给定MAC地址和端口号的InetSocketAddress数组。
     * 这里发现一个问题：
     * 我的小米手机，WIFI不支持ipv6的socket通信，但4G运营商支持IPV6的socket通信
     * 如果是wifi局域网内，需要通过ipv4建立通信
     * 如果是4G网络，可以通过ipv6建立通信
     * 所以后续统一采用ipv4/ipv6建立socket连接
     * 不采用MAC地址，直接通话扫描二维码后的address获取ip
     */
    public static List<InetSocketAddress> getAddressPermutations(String contact_mac, int port) {
        byte[] contact_mac_bytes = Utils.macAddressToBytes(contact_mac);
        ArrayList<InetSocketAddress> addrs = new ArrayList<InetSocketAddress>();
        try {
            // 获取所有的网络接口，并通过循环遍历每一个网络接口
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif: all) {
                log("遍历每一个网络接口AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------" + nif);
                log("遍历每一个网络接口AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------" + Arrays.toString(nif.getHardwareAddress()));
                if (nif.isLoopback()) {
                    log("该网络接口是回环接口AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------" + nif);
                    continue;
                }
                if (nif.getInterfaceAddresses().size() == 0){
                    log("该网络接口没有接口地址！！！AAAAAAAAAAAAAAAAAAAAAAAAAA--------------------");
                }
                // 获取网络接口的所有接口地址（InterfaceAddress），并将每一个接口地址封装成InetAddress对象
                for (InterfaceAddress ia: nif.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    log("遍历网络接口的所有接口地址BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB--------------------" + addr);
                    if (addr.isLoopbackAddress()) {
                        log("该接口地址是回环地址BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB--------------------" + addr);
                        continue;
                    }
                    // 判断网络地址是否是Inet4Address的实例
                    if (addr instanceof Inet4Address) {
                        log("该网络地址是Inet4Address的实例CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------" + addr);
                        addrs.add(new InetSocketAddress(addr, port));
                        continue;
                    }
                    // 判断网络地址是否是Inet6Address的实例
                    if (addr instanceof Inet6Address) {
                        log("该网络地址是Inet6Address的实例CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------" + addr);
                        Inet6Address addr6 = (Inet6Address) addr;
                        // 获取从IPv6地址中提取到的，MAC地址字节数组
                        byte[] extracted_mac = getEUI64MAC(addr6);
                        log("提取的MAC地址字节数组CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------" + Arrays.toString(extracted_mac));
                        // 判读是否为null，以及与当前网络接口的MAC地址字节数组是否相等（即是否属于同一接口）
                        if (extracted_mac != null && Arrays.equals(extracted_mac, nif.getHardwareAddress())) {
                            log("该MAC地址符合条件CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------");
                            // 创建一个新的带有给定MAC地址的Inet6Address对象，并添加到InetSocketAddress列表addrs中
                            InetAddress new_addr = createEUI64Address(addr6, contact_mac_bytes);
                            if (new_addr != null) {
                                addrs.add(new InetSocketAddress(new_addr, port));
                            }
                        } else{
                            log("该MAC地址不符合条件！！！CCCCCCCCCCCCCCCCCCCCCCCCCC--------------------");
                        }
                    } else{
                        log("该网络地址不是Inet6Address的实例！！！BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB--------------------" + addr);

                    }
                }
                log("----------------------------------------------------------------------------------------------------");
                log("InetSocketAddress集合DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD--------------------" + addrs.toString());
                log("----------------------------------------------------------------------------------------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return addrs;
    }

    // 将给定的基于EUI-64的地址，获取/转换字符串类型的MAC地址
    public static String getGeneralizedAddress(InetAddress address) {
        if (address instanceof Inet6Address) {
            // 如果IPv6地址包含一个MAC地址，取出该MAC地址
            byte[] mac = Utils.getEUI64MAC((Inet6Address) address);
            if (mac != null) {
                return Utils.bytesToMacAddress(mac);
            }
        }
        // 如果没有，则直接返回地址的字符串形式
        return address.getHostAddress();
    }
    // 将字节数组数据写入到指定的外部存储文件中
    public static void writeExternalFile(String filepath, byte[] data) throws IOException {
        File file = new File(filepath);
        if (file.exists() && file.isFile()) {
            if (!file.delete()) {
                throw new IOException("删除现有文件失败！！！--------------------" + filepath);
            }
        }
        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }
    // 从指定的外部存储文件中读取字节数组数据
    public static byte[] readExternalFile(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("文件不存在！！！--------------------" + filepath);
        }
        FileInputStream fis = new FileInputStream(file);
        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((nRead = fis.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
    private static void log(String s) {
        Log.d(Utils.class.getSimpleName(), s);
    }
}
