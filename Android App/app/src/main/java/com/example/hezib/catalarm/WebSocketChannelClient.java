package com.example.hezib.catalarm;

import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

/**
 * Created by hezib on 20/11/2017.
 */

public class WebSocketChannelClient {

    private static final String TAG = WebSocketChannelClient.class.getName();
    private static final int CLOSE_TIMEOUT = 1000;

    private final WebSocketChannelEvents events;
    private final Handler handler;
    private WebSocketConnection ws;
    private String wsServerUrl;
    private WebSocketConnectionState state;
    private WebSocketObserver wsObserver;
    private final Object closeEventLock = new Object();
    private boolean closeEvent;

    public enum WebSocketConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    public interface WebSocketChannelEvents {

        void onWebSocketMessage(final String message);

        void onWebSocketClose();

        void onWebSocketError(final String description);
    }

    public WebSocketChannelClient(Handler handler, WebSocketChannelEvents events) {
        this.handler = handler;
        this.events = events;
        state = WebSocketConnectionState.NEW;
    }

    public WebSocketConnectionState getState() {
        return this.state;
    }

    public void connect(final String wsUrl) {
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.NEW) {
            Log.e(TAG, "WebSocket is already connected.");
            return;
        }
        wsServerUrl = wsUrl;
        closeEvent = false;

        Log.d(TAG, "Connecting WebSocket to: " + wsUrl);
        ws = new WebSocketConnection();
        wsObserver = new WebSocketObserver();
        try {
            ws.connect(new URI(wsServerUrl), wsObserver);
        } catch (URISyntaxException e) {
            reportError("URI error: " + e.getMessage());
        } catch (WebSocketException e) {
            reportError("WebSocket connection error: " + e.getMessage());
        }
    }

    public void send(String message) {
        checkIfCalledOnValidThread();
        switch (state) {
            case NEW:
                Log.e(TAG, "WebSocket send() in new state : " + message);
                return;
            case ERROR:
                Log.e(TAG, "WebSocket send() in error state : " + message);
                return;
            case CLOSED:
                Log.e(TAG, "WebSocket send() in closed state : " + message);
                return;
            case CONNECTED:
                Log.d(TAG, "WebSocket send(): " + message);
                ws.sendTextMessage(message);
                break;
        }
    }

    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();
        Log.d(TAG, "Disconnect WebSocket. State: " + state);
        if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.ERROR) {
            ws.disconnect();
            state = WebSocketConnectionState.CLOSED;

            if (waitForComplete) {
                synchronized (closeEventLock) {
                    while (!closeEvent) {
                        try {
                            closeEventLock.wait(CLOSE_TIMEOUT);
                            break;
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Wait error: " + e.toString());
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Disconnecting WebSocket done.");
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.ERROR) {
                    state = WebSocketConnectionState.ERROR;
                    events.onWebSocketError(errorMessage);
                }
            }
        });
    }

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private void callUv4l() {
        try {
            JSONObject item = new JSONObject()
                    .put("force_hw_vcodec", false)
                    .put("vformat", "60");
            JSONObject msg = new JSONObject()
                    .put("what", "call")
                    .put("options", item.toString());

            send(msg.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class WebSocketObserver implements WebSocket.WebSocketConnectionObserver {

        @Override
        public void onOpen() {
            Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    state = WebSocketConnectionState.CONNECTED;
                    callUv4l();
                }
            });
        }

        @Override
        public void onClose(WebSocketCloseNotification code, String reason) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: " + state);
            synchronized (closeEventLock) {
                closeEvent = true;
                closeEventLock.notify();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state != WebSocketConnectionState.CLOSED) {
                        state = WebSocketConnectionState.CLOSED;
                        events.onWebSocketClose();
                    }
                }
            });
        }

        @Override
        public void onTextMessage(String payload) {
            Log.d(TAG, "WebSocket onTextMessage(): " + payload);
            final String message = payload;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == WebSocketConnectionState.CONNECTED) {
                        events.onWebSocketMessage(message);
                    }
                }
            });
        }

        @Override
        public void onRawTextMessage(byte[] payload) {}

        @Override
        public void onBinaryMessage(byte[] payload) {}
    }
}
