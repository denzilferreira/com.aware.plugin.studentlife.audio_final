package com.aware.plugin.studentlife.audio_final;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;
import com.aware.ui.AppCompatPreferenceActivity;

public class Settings extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    //Plugin settings in XML @xml/preferences_conversations
    public static final String STATUS_PLUGIN_STUDENTLIFE_AUDIO = "status_plugin_studentlife_audio";

    //Plugin settings UI elements
    private static CheckBoxPreference status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_conversations);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_STUDENTLIFE_AUDIO);

        if (Aware.getSetting(this, STATUS_PLUGIN_STUDENTLIFE_AUDIO).length() == 0)
            Aware.setSetting(this, STATUS_PLUGIN_STUDENTLIFE_AUDIO, true); //as soon as we install the plugin, it is activated, so default here

        status.setChecked(Aware.getSetting(this, STATUS_PLUGIN_STUDENTLIFE_AUDIO).equals("true"));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = findPreference(key);

        if( setting.getKey().equals(STATUS_PLUGIN_STUDENTLIFE_AUDIO) ) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            status.setChecked(sharedPreferences.getBoolean(key, false));
        }

        if (Aware.getSetting(this, STATUS_PLUGIN_STUDENTLIFE_AUDIO).equals("true")) {
            Aware.startPlugin(getApplicationContext(), Plugin.PLUGIN_NAME);
        } else {
            Aware.stopPlugin(getApplicationContext(), Plugin.PLUGIN_NAME);
        }
    }
}
