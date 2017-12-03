package com.example.hezib.catalarm;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import com.example.hezib.catalarm.ParametersFetcher.ParametersFetcherEvents;

/**
 * Created by hezib on 20/11/2017.
 */

public class WebSocketRTCClient implements Uv4lRTCClient, WebSocketChannelClient.WebSocketChannelEvents {

    private static final String TAG = WebSocketRTCClient.class.getName();

    private enum ConnectionState {NEW, CONNECTED, CLOSED, ERROR}

    private final Handler handler;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState state;
    private ConnectionParameters connectionParameters;

    public WebSocketRTCClient(SignalingEvents events) {
        this.events = events;
        state = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void connectToUv4l(ConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToUv4lInternal();
            }
        });
    }

    private void connectToUv4lInternal() {
        Log.d(TAG, "Connect to: " + connectionParameters.url);
        state = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(handler, this);

        ParametersFetcherEvents callbacks = new ParametersFetcherEvents() {
            @Override
            public void onSignalingParametersReady(final SignalingParameters params) {
                WebSocketRTCClient.this.handler.post(new Runnable() {
                    @Override
                    public void run() {
                        WebSocketRTCClient.this.signalingParametersReady(params);
                    }
                });
            }

            @Override
            public void onSignalingParametersError(String description) {
                WebSocketRTCClient.this.reportError(description);
            }
        };

        new ParametersFetcher(connectionParameters, callbacks).makeRequest();
    }

    private void signalingParametersReady(final SignalingParameters signalingParameters) {
        Log.d(TAG, "WebRTC connection available.");

        state = ConnectionState.CONNECTED;

        events.onConnectedToUv4l(signalingParameters);

        wsClient.connect(connectionParameters.wsUrl);
    }

    @Override
    public void sendAnswerSdp(SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                JSONObject msg = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                jsonPut(msg, "what", "answer");
                jsonPut(msg, "data", json.toString());

                wsClient.send(msg.toString());
            }
        });
    }

    @Override
    public void sendLocalIceCandidate(IceCandidate candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
                jsonPut(json, "sdpMid", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);
                JSONObject msg = new JSONObject();
                jsonPut(msg, "what", "addIceCandidate");
                jsonPut(msg, "data", json.toString());

                wsClient.send(msg.toString());

            }
        });
    }

    @Override
    public void requestIceCandidates() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "what", "generateIceCandidates");
                wsClient.send(json.toString());
            }
        });
    }

    @Override
    public void disconnectFromUv4l() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromUv4lInternal();
                handler.getLooper().quit();
            }
        });
    }

    private void disconnectFromUv4lInternal() {
        Log.d(TAG, "Disconnect. Room state: " + state);
        if (state == ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            state = ConnectionState.CLOSED;
            if (wsClient != null) {
                wsClient.disconnect(true);
            }
        }
    }

    @Override
    public void onWebSocketMessage(String message) {
        if (wsClient.getState() != WebSocketChannelClient.WebSocketConnectionState.CONNECTED) {
            Log.e(TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(message);
            Log.d(TAG, message);
            String what = json.getString("what");
            switch (what) {
                case "offer":
                    JSONObject data = new JSONObject(json.getString("data"));
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(what), data.getString("sdp"));
                    events.onRemoteDescription(sdp);
                    break;
                case "answer":
                    break;
                case "message":
                    break;
                case "iceCandidates":
                    JSONArray candidates = new JSONArray(json.getString("data"));
                    for (int i = 0; i < candidates.length(); i++) {
                        JSONObject candidate = candidates.getJSONObject(i);
                        events.onRemoteIceCandidate(toJavaCandidate(candidate));
                        Log.d(TAG, "IceCandidate added: " + candidate.toString());
                    }
                    break;
                default:
                    break;
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != ConnectionState.ERROR) {
                    state = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"));
    }
}
