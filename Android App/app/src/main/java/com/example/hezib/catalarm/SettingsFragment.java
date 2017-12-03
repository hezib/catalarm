package com.example.hezib.catalarm;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by hezib on 12/11/2017.
 */

public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
}
