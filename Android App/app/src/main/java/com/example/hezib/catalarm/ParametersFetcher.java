package com.example.hezib.catalarm;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Scanner;

import com.example.hezib.catalarm.Uv4lRTCClient.SignalingParameters;
import com.example.hezib.catalarm.Uv4lRTCClient.ConnectionParameters;
import com.example.hezib.catalarm.utils.AsyncHttpURLConnection;
import com.example.hezib.catalarm.utils.AsyncHttpURLConnection.AsyncHttpEvents;

/**
 * Created by hezib on 20/11/2017.
 */

public class ParametersFetcher {
    private static final String TAG = ParametersFetcher.class.getName();
    private static final String REST_SUFFIX = "/api/webrtc/settings";
    private static final int TURN_HTTP_TIMEOUT_MS = 5000;

    private final ParametersFetcherEvents events;
    private final ConnectionParameters connectionParameters;

    public ParametersFetcher(ConnectionParameters connectionParameters, final ParametersFetcherEvents events) {
        this.connectionParameters = connectionParameters;
        this.events = events;
    }

    public static String getConnectionUrl(ConnectionParameters connectionParameters) {
        return connectionParameters.url + ":" + connectionParameters.port + REST_SUFFIX ;
    }

    public interface ParametersFetcherEvents {

        void onSignalingParametersReady(final SignalingParameters params);

        void onSignalingParametersError(final String description);
    }

    public void makeRequest() {
        // MOVED KILL MOTION TO WEBSOCKETRTCCLIENT CONNECTINTERNAL
        String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(TAG, "Connecting to: " + connectionUrl);
        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("GET", connectionUrl, null, new AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.e(TAG, "Connection error: " + errorMessage);
                        events.onSignalingParametersError(errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        roomHttpResponseParse(response);
                    }
                });
        httpConnection.send();
    }

    private void roomHttpResponseParse(String response) {
        Log.d(TAG, "Response: " + response);
        LinkedList<PeerConnection.IceServer> iceServers = null;
        try {
            JSONObject json = new JSONObject(response).getJSONObject("global");
            String strIceServers = json.getString("ice_servers");
            if(strIceServers.equals("")) {
                iceServers = new LinkedList<>();
                iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            } else {
                iceServers = iceServersFromPCConfigJSON(strIceServers);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SignalingParameters params = new SignalingParameters(iceServers);
        events.onSignalingParametersReady(params);
    }

    // Requests & returns a TURN ICE Server based on a request URL.  Must be run
    // off the main thread!
    private LinkedList<PeerConnection.IceServer> requestTurnServers(String url)
            throws IOException, JSONException {
        LinkedList<PeerConnection.IceServer> turnServers = new LinkedList<>();
        Log.d(TAG, "Request TURN from: " + url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("REFERER", "https://appr.tc");
        connection.setConnectTimeout(TURN_HTTP_TIMEOUT_MS);
        connection.setReadTimeout(TURN_HTTP_TIMEOUT_MS);
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Non-200 response when requesting TURN server from " + url + " : "
                    + connection.getHeaderField(null));
        }
        InputStream responseStream = connection.getInputStream();
        String response = drainStream(responseStream);
        connection.disconnect();
        Log.d(TAG, "TURN response: " + response);
        JSONObject responseJSON = new JSONObject(response);
        JSONArray iceServers = responseJSON.getJSONArray("iceServers");
        for (int i = 0; i < iceServers.length(); ++i) {
            JSONObject server = iceServers.getJSONObject(i);
            JSONArray turnUrls = server.getJSONArray("urls");
            String username = server.has("username") ? server.getString("username") : "";
            String credential = server.has("credential") ? server.getString("credential") : "";
            for (int j = 0; j < turnUrls.length(); j++) {
                String turnUrl = turnUrls.getString(j);
                PeerConnection.IceServer turnServer =
                        PeerConnection.IceServer.builder(turnUrl)
                                .setUsername(username)
                                .setPassword(credential)
                                .createIceServer();
                turnServers.add(turnServer);
            }
        }
        return turnServers;
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(String pcConfig)
            throws JSONException {
        JSONObject json = new JSONObject(pcConfig);
        JSONArray servers = json.getJSONArray("iceServers");
        LinkedList<PeerConnection.IceServer> ret = new LinkedList<>();
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.getJSONObject(i);
            String url = server.getString("urls");
            String credential = server.has("credential") ? server.getString("credential") : "";
            PeerConnection.IceServer turnServer =
                    PeerConnection.IceServer.builder(url)
                            .setPassword(credential)
                            .createIceServer();
            ret.add(turnServer);
        }
        return ret;
    }

    // Return the contents of an InputStream as a String.
    private static String drainStream(InputStream in) {
        Scanner s = new Scanner(in).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
