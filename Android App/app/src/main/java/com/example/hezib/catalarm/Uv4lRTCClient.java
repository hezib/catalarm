package com.example.hezib.catalarm;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * Created by hezib on 20/11/2017.
 */

public interface Uv4lRTCClient {

    class ConnectionParameters {
        private static final String WS_PREFIX = "ws://";
        private static final String WS_SUFFIX = "/stream/webrtc";
        public final String url;
        public final String port;
        public final String wsUrl;

        public ConnectionParameters(String url, String port) {
            this.url = url;
            this.port = port;
            this.wsUrl = WS_PREFIX + this.url.replaceFirst("https?://", "") + ":" + this.port + WS_SUFFIX;
        }
    }

    void connectToUv4l(ConnectionParameters connectionParameters);

    void sendAnswerSdp(final SessionDescription sdp);

    void sendLocalIceCandidate(final IceCandidate candidate);

    void requestIceCandidates();

    void disconnectFromUv4l();

    class SignalingParameters {

        public final List<IceServer> iceServers;

        public SignalingParameters(List<IceServer> iceServers) {
            this.iceServers = iceServers;
        }
    }

    interface SignalingEvents {

        void onConnectedToUv4l(final SignalingParameters params);

        void onRemoteDescription(final SessionDescription sdp);

        void onRemoteIceCandidate(final IceCandidate candidate);

        void onChannelClose();

        void onChannelError(final String description);
    }
}
