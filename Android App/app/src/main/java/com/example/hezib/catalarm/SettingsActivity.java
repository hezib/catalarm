package com.example.hezib.catalarm;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.ListPreference;
import android.preference.Preference;

import java.util.HashSet;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity implements OnSharedPreferenceChangeListener {

    private SettingsFragment settingsFragment;
    private boolean isChanged = false;

    private String keyPrefRpiIP;

    private String keyPrefUv4lPort;

    private String keyPrefMotionPort;
    private String keyPrefIdleSleep;
    private String keyPrefActiveSleep;

    private String keyPrefAlarm;
    private String keyPrefDifferentEvent;
    private String keyPrefStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        keyPrefRpiIP = getString(R.string.pref_rpi_ip_key);

        keyPrefUv4lPort = getString(R.string.pref_uv4l_port_key);

        keyPrefMotionPort = getString(R.string.pref_motion_port_key);
        keyPrefIdleSleep = getString(R.string.pref_idle_sleep_key);
        keyPrefActiveSleep = getString(R.string.pref_active_sleep_key);

        keyPrefAlarm = getString(R.string.pref_alarm_key);
        keyPrefDifferentEvent = getString(R.string.pref_different_events_key);
        keyPrefStats = getString(R.string.pref_stats_key);


        // Display the fragment as the main content.
        settingsFragment = new SettingsFragment();
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, settingsFragment)
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set summary to be the user-description for the selected value
        SharedPreferences sharedPreferences = settingsFragment.getPreferenceScreen().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        updateSummary(sharedPreferences, keyPrefRpiIP);

        updateSummary(sharedPreferences, keyPrefUv4lPort);

        updateSummary(sharedPreferences, keyPrefMotionPort);
        updateSummary(sharedPreferences, keyPrefIdleSleep);
        updateSummary(sharedPreferences, keyPrefActiveSleep);

        updateSummaryList(sharedPreferences, keyPrefAlarm);
        updateSummary(sharedPreferences, keyPrefDifferentEvent);

    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sharedPreferences =
                settingsFragment.getPreferenceScreen().getSharedPreferences();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(keyPrefRpiIP)
                || key.equals(keyPrefUv4lPort)
                || key.equals(keyPrefMotionPort)
                || key.equals(keyPrefIdleSleep)
                || key.equals(keyPrefActiveSleep)
                || key.equals(keyPrefDifferentEvent)) {
            updateSummaryWithAction(sharedPreferences, key);
        } else if (key.equals(keyPrefAlarm)) {
            updateSummaryList(sharedPreferences, key);
        } else if (key.equals(keyPrefStats)) {
            isChanged = true;
        }
    }

    private void updateSummaryWithAction(SharedPreferences sharedPreferences, String key) {
        updateSummary(sharedPreferences, key);
        isChanged = true;
    }

    private void updateSummary(SharedPreferences sharedPreferences, String key) {
        Preference updatedPref = settingsFragment.findPreference(key);
        // Set summary to be the user-description for the selected value
        updatedPref.setSummary(sharedPreferences.getString(key, ""));
    }


/*    // TODO: do i need this?
    *//*private void updateSummaryB(SharedPreferences sharedPreferences, String key) {
        Preference updatedPref = settingsFragment.findPreference(key);
        updatedPref.setSummary(sharedPreferences.getBoolean(key, true)
                ? getString(R.string.pref_value_enabled)
                : getString(R.string.pref_value_disabled));
    }*/

    private void updateSummaryList(SharedPreferences sharedPreferences, String key) {
        ListPreference updatedPref = (ListPreference) settingsFragment.findPreference(key);
        updatedPref.setSummary(updatedPref.getEntry());
    }

    @Override
    public void onBackPressed() {
        if(isChanged) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }

        super.onBackPressed();
    }

}
