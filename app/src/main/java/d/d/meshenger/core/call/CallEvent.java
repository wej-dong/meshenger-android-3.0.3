package d.d.meshenger.core.call;

import java.net.InetAddress;
import java.util.Date;


public class CallEvent {
    public enum Type {
        OUTGOING_UNKNOWN,
        OUTGOING_ACCEPTED,
        OUTGOING_DECLINED,
        OUTGOING_MISSED,
        OUTGOING_ERROR,
        INCOMING_UNKNOWN,
        INCOMING_ACCEPTED,
        INCOMING_DECLINED,
        INCOMING_MISSED,
        INCOMING_ERROR
    };

    public byte[] pubKey;
    public InetAddress address; // may be null in case the call attempt failed
    public Type type;
    public Date date;

    public CallEvent(byte[] pubKey, InetAddress address, Type type) {
        this.pubKey = pubKey;
        this.address = address;
        this.type = type;
        this.date = new Date();
    }
}
