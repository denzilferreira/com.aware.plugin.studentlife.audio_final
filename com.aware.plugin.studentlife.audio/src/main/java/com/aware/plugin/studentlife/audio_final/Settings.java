package com.aware.plugin.studentlife.audio_final;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;
import com.aware.ui.AppCompatPreferenceActivity;

public class Settings extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    //Plugin settings in XML @xml/preferences_conversations
    public static final String STATUS_PLUGIN_STUDENTLIFE_AUDIO = "status_plugin_studentlife_audio";
    public static final String PLUGIN_CONVERSATIONS_DELAY = "plugin_conversations_delay";
    public static final String PLUGIN_CONVERSATIONS_OFF_DUTY = "plugin_conversations_off_duty";
    public static final String PLUGIN_CONVERSATIONS_LENGTH = "plugin_conversations_length";

    //Plugin settings UI elements
    private static CheckBoxPreference status;
    private static EditTextPreference delay, offduty, length;

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

        delay = (EditTextPreference) findPreference(PLUGIN_CONVERSATIONS_DELAY);
        if (Aware.getSetting(this, PLUGIN_CONVERSATIONS_DELAY).length() == 0) {
            Aware.setSetting(this, PLUGIN_CONVERSATIONS_DELAY, 1);
        }
        delay.setText(Aware.getSetting(this, PLUGIN_CONVERSATIONS_DELAY));

        offduty = (EditTextPreference) findPreference(PLUGIN_CONVERSATIONS_OFF_DUTY);
        if (Aware.getSetting(this, PLUGIN_CONVERSATIONS_OFF_DUTY).length() == 0) {
            Aware.setSetting(this, PLUGIN_CONVERSATIONS_OFF_DUTY, 3);
        }
        offduty.setText(Aware.getSetting(this, PLUGIN_CONVERSATIONS_OFF_DUTY));

        length = (EditTextPreference) findPreference(PLUGIN_CONVERSATIONS_LENGTH);
        if (Aware.getSetting(this, PLUGIN_CONVERSATIONS_LENGTH).length() == 0) {
            Aware.setSetting(this, PLUGIN_CONVERSATIONS_LENGTH, 1);
        }
        length.setText(Aware.getSetting(this, PLUGIN_CONVERSATIONS_LENGTH));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = findPreference(key);

        if( setting.getKey().equals(STATUS_PLUGIN_STUDENTLIFE_AUDIO) ) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            status.setChecked(sharedPreferences.getBoolean(key, false));
        }

        if( setting.getKey().equals(PLUGIN_CONVERSATIONS_DELAY) ) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "1"));
            delay.setText(sharedPreferences.getString(key, "1"));
        }

        if( setting.getKey().equals(PLUGIN_CONVERSATIONS_OFF_DUTY) ) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "3"));
            offduty.setText(sharedPreferences.getString(key, "3"));
        }

        if( setting.getKey().equals(PLUGIN_CONVERSATIONS_LENGTH) ) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "1"));
            length.setText(sharedPreferences.getString(key, "1"));
        }

        if (Aware.getSetting(this, STATUS_PLUGIN_STUDENTLIFE_AUDIO).equals("true")) {
            Aware.startPlugin(getApplicationContext(), Plugin.PLUGIN_NAME);
        } else {
            Aware.stopPlugin(getApplicationContext(), Plugin.PLUGIN_NAME);
        }
    }
}
