package d.d.meshenger.core.call;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

// SdpObserver（监听SDP生成、设置的接口）的实现类
public class DefaultSdpObserver implements SdpObserver {
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        // nothing to do
    }

    @Override
    public void onSetSuccess() {
        // nothing to do
    }

    @Override
    public void onCreateFailure(String s) {
        // nothing to do
    }

    @Override
    public void onSetFailure(String s) {
        // nothing to do
    }
}
