package com.example.hezib.catalarm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.TextView;

import com.example.hezib.catalarm.utils.CloudantQueries;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements NotificationsClient.NotificationsHandler, CloudantQueries.CloudantQueriesEvents {

    private static final String TAG = MainActivity.class.getName();
    private static final int SETTINGS_REQUEST_CODE = 1;
    private static final int ARRAYLIST_REQUEST_CODE = 2;
    private static final NotificationsClient notifications = new NotificationsClient();
    public static final String SAVE_ARRAYLIST_EXTRA = "save-arraylist";

    private SharedPreferences sharedPref;
    private TextView mTextViewEventsTotal;
    private TextView mTextViewEventsPerDay;
    private TextView mTextViewTimePerEvent;
    private TextView mTextViewMostActiveDay;
    private TextView mTextViewMostActiveHour;
    private TextView mTextViewEventsExplained;
    private CloudantQueries queriesHandler;
    private ArrayList<DatabaseDocument> mDocsList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.activity_main);

        mTextViewEventsTotal = (TextView)findViewById(R.id.events_total);
        mTextViewEventsPerDay = (TextView)findViewById(R.id.events_per_day);
        mTextViewTimePerEvent = (TextView)findViewById(R.id.time_per_event);
        mTextViewMostActiveDay = (TextView)findViewById(R.id.most_active_day);
        mTextViewMostActiveHour = (TextView)findViewById(R.id.most_active_hour);
        mTextViewEventsExplained = (TextView)findViewById(R.id.events_explained);
        showOrHideViews();

        queriesHandler = new CloudantQueries(this);
        if(savedInstanceState == null || !savedInstanceState.containsKey(SAVE_ARRAYLIST_EXTRA)) {
            queriesHandler.getAllFromDatabase();
        } else {
            mDocsList = savedInstanceState.getParcelableArrayList(SAVE_ARRAYLIST_EXTRA);
            getStatistics(mDocsList);
        }

        notifications.init(this);
        askForPermissions();
    }

    public void askForPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissions.size() > 0)
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ARRAYLIST_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                mDocsList = data.getExtras().getParcelableArrayList(SAVE_ARRAYLIST_EXTRA);
                getStatistics(mDocsList);
            }
        } else if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                showOrHideViews();
                getStatistics(mDocsList);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, SETTINGS_REQUEST_CODE);
            return true;
        } else if (item.getItemId() == R.id.action_queries) {
            Intent intent = new Intent(this, QueryActivity.class);
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(SAVE_ARRAYLIST_EXTRA, mDocsList);
            intent.putExtras(bundle);
            startActivityForResult(intent, ARRAYLIST_REQUEST_CODE);
            return true;
        } else if (item.getItemId() == R.id.action_stream) {
            connectToUv4l();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        notifications.hold();
    }

    @Override
    public void onResume() {
        super.onResume();
        notifications.resume();
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used.
     */
    private String sharedPrefGetString(int attributeId, int defaultId) {
        String defaultValue = getString(defaultId);
        String attributeName = getString(attributeId);
        return sharedPref.getString(attributeName, defaultValue);
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used.
     */
    private boolean sharedPrefGetBoolean(int attributeId, int defaultId) {
        boolean defaultValue = Boolean.valueOf(getString(defaultId));
        String attributeName = getString(attributeId);
        return sharedPref.getBoolean(attributeName, defaultValue);
    }

    /**
     * Get a value from the shared preference or from the intent, if it does not
     * exist the default is used.
     */
    private int sharedPrefGetInteger(int attributeId, int defaultId) {
        String defaultString = getString(defaultId);
        int defaultValue = Integer.parseInt(defaultString);
        String attributeName = getString(attributeId);
        String value = sharedPref.getString(attributeName, defaultString);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Wrong setting for: " + attributeName + ":" + value);
            return defaultValue;
        }
    }

    @Override
    public void connectToUv4l() {

        // Get connection parameters.
        String url = sharedPrefGetString(R.string.pref_rpi_ip_key, R.string.pref_rpi_ip_default);
        String uv4lPort = sharedPrefGetString(R.string.pref_uv4l_port_key, R.string.pref_uv4l_port_default);
        String motionPort = sharedPrefGetString(R.string.pref_motion_port_key, R.string.pref_motion_port_default);
        String idleSleep = sharedPrefGetString(R.string.pref_idle_sleep_key, R.string.pref_idle_sleep_default);
        String activeSleep = sharedPrefGetString(R.string.pref_active_sleep_key, R.string.pref_active_sleep_default);
        String alarmSound = sharedPrefGetString(R.string.pref_alarm_key, R.string.pref_alarm_default);

        // Get default codecs.
        String videoCodec = sharedPrefGetString(R.string.pref_videocodec_key, R.string.pref_videocodec_default);
        String audioCodec = sharedPrefGetString(R.string.pref_audiocodec_key, R.string.pref_audiocodec_default);

        // Check HW codec flag.
        boolean hwCodec = sharedPrefGetBoolean(R.string.pref_hwcodec_key, R.string.pref_hwcodec_default);

        // Check OpenSL ES enabled flag.
        boolean useOpenSLES = sharedPrefGetBoolean(R.string.pref_opensles_key, R.string.pref_opensles_default);

        // Check Disable built-in AEC flag.
        boolean disableBuiltInAEC = sharedPrefGetBoolean(R.string.pref_disable_built_in_aec_key, R.string.pref_disable_built_in_aec_default);

        // Check Disable built-in AGC flag.
        boolean disableBuiltInAGC = sharedPrefGetBoolean(R.string.pref_disable_built_in_agc_key, R.string.pref_disable_built_in_agc_default);

        // Check Disable built-in NS flag.
        boolean disableBuiltInNS = sharedPrefGetBoolean(R.string.pref_disable_built_in_ns_key, R.string.pref_disable_built_in_ns_default);

        // Check Enable level control.
        boolean enableLevelControl = sharedPrefGetBoolean(R.string.pref_enable_level_control_key, R.string.pref_enable_level_control_default);

        // Check Disable gain control
        boolean disableWebRtcAGCAndHPF = sharedPrefGetBoolean(R.string.pref_disable_webrtc_agc_and_hpf_key, R.string.pref_disable_webrtc_agc_default);

        int audioStartBitrate = 0;
        {
            String keyPrefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key);
            String keyPrefAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key);
            String bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default);
            String bitrateType = sharedPref.getString(keyPrefAudioBitrateType, bitrateTypeDefault);
            if (!bitrateType.equals(bitrateTypeDefault)) {
                String bitrateValue = sharedPref.getString(keyPrefAudioBitrateValue, getString(R.string.pref_startaudiobitratevalue_default));
                audioStartBitrate = Integer.parseInt(bitrateValue);
            }
        }

        // Start AppRTCMobile activity.
        Log.d(TAG, "Connecting to raspberry pi at URL " + url);
        if (validateUrl(url)) {

            Intent intent = new Intent(this, CallActivity.class);

            intent.putExtra(CallActivity.EXTRA_RPI_IP, url);
            intent.putExtra(CallActivity.EXTRA_UV4L_PORT, uv4lPort);
            intent.putExtra(CallActivity.EXTRA_MOTION_PORT, motionPort);
            intent.putExtra(CallActivity.EXTRA_IDLE_SLEEP, idleSleep);
            intent.putExtra(CallActivity.EXTRA_ACTIVE_SLEEP, activeSleep);
            intent.putExtra(CallActivity.EXTRA_ALARM_SOUND, alarmSound);
            intent.putExtra(CallActivity.EXTRA_VIDEOCODEC, videoCodec);
            intent.putExtra(CallActivity.EXTRA_HWCODEC_ENABLED, hwCodec);
            intent.putExtra(CallActivity.EXTRA_OPENSLES_ENABLED, useOpenSLES);
            intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, disableBuiltInAEC);
            intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, disableBuiltInAGC);
            intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_NS, disableBuiltInNS);
            intent.putExtra(CallActivity.EXTRA_ENABLE_LEVEL_CONTROL, enableLevelControl);
            intent.putExtra(CallActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, disableWebRtcAGCAndHPF);
            intent.putExtra(CallActivity.EXTRA_AUDIO_BITRATE, audioStartBitrate);
            intent.putExtra(CallActivity.EXTRA_AUDIOCODEC, audioCodec);

            startActivity(intent);
        }
    }

    private boolean validateUrl(String url) {
        if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
            return true;
        }

        new AlertDialog.Builder(this)
                .setTitle(getText(R.string.invalid_url_title))
                .setMessage(getString(R.string.invalid_url_text, url))
                .setCancelable(false)
                .setNeutralButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        })
                .create()
                .show();
        return false;
    }

    @Override
    public void onError(String errorMessage) {
        Log.e(TAG, "Error querying database: " + errorMessage);
    }

    @Override
    public void onGetAllResponse(String response) {
        List<DatabaseDocument> list = DatabaseDocument.getListFromJson(response);
        if(list instanceof ArrayList)
            mDocsList = (ArrayList<DatabaseDocument>)list;
        getStatistics(list);
    }

    @Override
    public void onGetDocResponse(String response) {}

    @Override
    public void onDeleteResponse(String response) {}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(SAVE_ARRAYLIST_EXTRA, mDocsList);
        super.onSaveInstanceState(outState);
    }

    private void getStatistics(List<DatabaseDocument> list) {
        if(list == null || list.size() == 0) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String eventsTotal = null;
                String eventsPerDay = null;
                String timeSpentPerEvent = null;
                String mostActiveDay = null;
                String mostActiveHours = null;

                int maxTimePerEvent = sharedPrefGetInteger(R.string.pref_different_events_key, R.string.pref_different_events_default);
                Statistics catStatistics = new Statistics(list, maxTimePerEvent);

                if (mTextViewEventsTotal.getVisibility() == View.VISIBLE)
                    eventsTotal = catStatistics.getEventsTotal();
                if (mTextViewEventsPerDay.getVisibility() == View.VISIBLE)
                    eventsPerDay = catStatistics.getEventsPerDay();
                if (mTextViewTimePerEvent.getVisibility() == View.VISIBLE)
                    timeSpentPerEvent = catStatistics.getTimeSpentPerEvent();
                if (mTextViewMostActiveDay.getVisibility() == View.VISIBLE) {
                    mostActiveDay = catStatistics.getMostActiveDay();
                }
                if (mTextViewMostActiveHour.getVisibility() == View.VISIBLE) {
                    mostActiveHours = catStatistics.getMostActiveHour();
                }

                String text = getString(R.string.events_explained, String.valueOf(maxTimePerEvent));
                mTextViewEventsExplained.setText(text);

                if (eventsTotal != null) {
                    text = getString(R.string.events_total, eventsTotal);
                    mTextViewEventsTotal.setText(text);
                }
                if (eventsPerDay != null) {
                    text = getString(R.string.events_per_day, eventsPerDay);
                    mTextViewEventsPerDay.setText(text);
                }
                if (timeSpentPerEvent != null) {
                    text = getString(R.string.time_per_event, timeSpentPerEvent);
                    mTextViewTimePerEvent.setText(text);
                }
                if(mostActiveDay != null) {
                    text = getString(R.string.most_active_day, mostActiveDay);
                    mTextViewMostActiveDay.setText(text);
                }
                if(mostActiveHours != null) {
                    text = getString(R.string.most_active_hour, mostActiveHours);
                    mTextViewMostActiveHour.setText(text);
                }
            }
        });
    }

    private void showOrHideViews() {
        List<TextView> textViewList = new LinkedList<>();
        textViewList.add(mTextViewEventsTotal);
        textViewList.add(mTextViewEventsPerDay);
        textViewList.add(mTextViewTimePerEvent);
        textViewList.add(mTextViewMostActiveDay);
        textViewList.add(mTextViewMostActiveHour);
        boolean[] isShown = new boolean[textViewList.size()];
        String keyPrefStats = getString(R.string.pref_stats_key);
        Set<String> choices = sharedPref.getStringSet(keyPrefStats, null);
        if (choices == null) return;
        for(String choice: choices) {
            isShown[Integer.parseInt(choice)] = true;
        }
        int i = 0;
        for(TextView tv: textViewList) {
            tv.setVisibility(isShown[i++] ? View.VISIBLE : View.GONE);
        }
    }
}