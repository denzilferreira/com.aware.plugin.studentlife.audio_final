package com.aware.plugin.studentlife.audio_final;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncRequest;
import android.os.Bundle;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

import java.util.Set;

import edu.dartmouth.studentlife.AudioLib.AudioService;

public class Plugin extends Aware_Plugin {

    public static final String PLUGIN_NAME = "com.aware.plugin.studentlife.audio_final";

    public static final String ACTION_AWARE_PLUGIN_CONVERSATIONS_START = "ACTION_AWARE_PLUGIN_CONVERSATIONS_START";
    public static final String ACTION_AWARE_PLUGIN_CONVERSATIONS_STOP = "ACTION_AWARE_PLUGIN_CONVERSATIONS_STOP";

    private Intent audioProbe = null;

    public static boolean IN_CONVERSATION = false;
    public static ContextProducer sContextProducer;

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Provider.getAuthority(this);

        TAG = "AWARE::" + getResources().getString(R.string.app_name);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {

                if (DEBUG) {
                    Log.d(TAG, "In conversation: " + IN_CONVERSATION);
                }

                if (IN_CONVERSATION) {
                    sendBroadcast(new Intent(ACTION_AWARE_PLUGIN_CONVERSATIONS_START));
                } else {
                    sendBroadcast(new Intent(ACTION_AWARE_PLUGIN_CONVERSATIONS_STOP));
                }
            }
        };
        sContextProducer = CONTEXT_PRODUCER;

        //Add permissions you need (Support for Android M) e.g.,
        REQUIRED_PERMISSIONS.add(Manifest.permission.RECORD_AUDIO);
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            if (Aware.getSetting(getApplicationContext(), Settings.STATUS_PLUGIN_STUDENTLIFE_AUDIO).length() == 0) {
                Aware.setSetting(getApplicationContext(), Settings.STATUS_PLUGIN_STUDENTLIFE_AUDIO, true);
            } else {
                if (Aware.getSetting(getApplicationContext(), Settings.STATUS_PLUGIN_STUDENTLIFE_AUDIO).equalsIgnoreCase("false")) {
                    Aware.stopPlugin(getApplicationContext(), getPackageName());
                    return START_STICKY;
                }
            }

            if (Aware.getSetting(this, Settings.PLUGIN_CONVERSATIONS_DELAY).length() == 0)
                Aware.setSetting(this, Settings.PLUGIN_CONVERSATIONS_DELAY, 1);

            if (Aware.getSetting(this, Settings.PLUGIN_CONVERSATIONS_OFF_DUTY).length() == 0)
                Aware.setSetting(this, Settings.PLUGIN_CONVERSATIONS_OFF_DUTY, 3);

            if (Aware.getSetting(this, Settings.PLUGIN_CONVERSATIONS_LENGTH).length() == 0)
                Aware.setSetting(this, Settings.PLUGIN_CONVERSATIONS_LENGTH, 1);

            if (audioProbe == null) {
                audioProbe = new Intent(getApplicationContext(), AudioService.class);
                startService(audioProbe);
            }

            if (Aware.isStudy(this)) {
                Account aware_account = Aware.getAWAREAccount(getApplicationContext());
                String authority = Provider.getAuthority(getApplicationContext());
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;

                ContentResolver.setIsSyncable(aware_account, authority, 1);
                ContentResolver.setSyncAutomatically(aware_account, authority, true);
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(aware_account, authority)
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Provider.getAuthority(this), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Provider.getAuthority(this),
                Bundle.EMPTY
        );

        Aware.setSetting(this, Settings.STATUS_PLUGIN_STUDENTLIFE_AUDIO, false);

        if (audioProbe != null) stopService(audioProbe);
    }
}
