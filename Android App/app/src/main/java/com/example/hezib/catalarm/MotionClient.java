package com.example.hezib.catalarm;

import android.util.Log;

import com.example.hezib.catalarm.utils.AsyncHttpURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by hezib on 20/11/2017.
 */

public class MotionClient {

    private static final String TAG = MotionClient.class.getName();

    private ConnectionParameters connectionParameters;

    public MotionClient(String url, String port, String idle_sleep, String active_sleep, String alarmSound) {
        this.connectionParameters = new ConnectionParameters(url, port, idle_sleep, active_sleep, alarmSound);
    }

    class ConnectionParameters {
        private static final String KILL_MOTION_SUFFIX = "killmotion";
        private static final String START_MOTION_SUFFIX = "startmotion";

        public final String url;
        public final String port;
        public final String idle_sleep;
        public final String active_sleep;
        public final String alarmSound;

        public ConnectionParameters(String url, String port, String idle_sleep, String active_sleep, String alarmSound) {
            this.url = url;
            this.port = port;
            this.idle_sleep = idle_sleep;
            this.active_sleep = active_sleep;
            this.alarmSound = alarmSound;
        }

        public String getStartMotionUrl(ConnectionParameters connectionParameters) {
            return connectionParameters.url + ":" + connectionParameters.port + "/" + START_MOTION_SUFFIX;
        }

        public String getKillmotionUrl(ConnectionParameters connectionParameters) {
            return connectionParameters.url + ":" + connectionParameters.port + "/" + KILL_MOTION_SUFFIX;
        }
    }

    public void killMotionServer() {
        String killMotionUrl = connectionParameters.getKillmotionUrl(connectionParameters);
        Log.d(TAG, "Killing motion server: " + killMotionUrl);
        JSONObject msg = new JSONObject();
        try {
            msg.put("app", "catalarm");
            msg.put("command", "killmotion");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        AsyncHttpURLConnection killMotionConnection =
                new AsyncHttpURLConnection("PUT", killMotionUrl, msg.toString(), new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.e(TAG, "error: " + errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        Log.d(TAG, "Motion server killed: " + response);
                    }
                });
        killMotionConnection.setContentType("application/json");
        killMotionConnection.send();
    }

    public void startMotionServer() {
        String startMotionUrl = connectionParameters.getStartMotionUrl(connectionParameters);
        Log.d(TAG, "Starting motion server: " + startMotionUrl);
        JSONObject msg = new JSONObject();
        try {
            msg.put("app", "catalarm");
            msg.put("command", "startmotion");
            msg.put("idle_sleep", connectionParameters.idle_sleep);
            msg.put("active_sleep", connectionParameters.active_sleep);
            msg.put("alarm", connectionParameters.alarmSound);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        AsyncHttpURLConnection startMotionConnection =
                new AsyncHttpURLConnection("PUT", startMotionUrl, msg.toString(), new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.e(TAG, "error: " + errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        Log.d(TAG, "Motion server started: " + response);
                    }
                });
        startMotionConnection.setContentType("application/json");
        startMotionConnection.send();
    }
}
