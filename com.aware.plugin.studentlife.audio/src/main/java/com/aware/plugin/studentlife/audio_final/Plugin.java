package com.aware.plugin.studentlife.audio_final;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Applications_Provider;
import com.aware.utils.Aware_Plugin;

import edu.dartmouth.studentlife.AudioLib.AudioService;

public class Plugin extends Aware_Plugin {

    public static final String PLUGIN_NAME = "com.aware.plugin.studentlife.audio_final";

    public static final String SHARED_PREF_NAME = "STUDENTLIFE_AUDIO_SHARED_PREF";
    public static final String SHARED_PREF_KEY_VERSION_CODE = "SHARED_PREF_KEY_VERSION_CODE";

    public static final String ACTION_AWARE_PLUGIN_CONVERSATIONS_START = "ACTION_AWARE_PLUGIN_CONVERSATIONS_START";
    public static final String ACTION_AWARE_PLUGIN_CONVERSATIONS_STOP = "ACTION_AWARE_PLUGIN_CONVERSATIONS_STOP";

    private Intent audioProbe = null;

    public static boolean IN_CONVERSATION = false;
    public static ContextProducer sContextProducer;

    @Override
    public void onCreate() {
        super.onCreate();

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

        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Provider.StudentLifeAudio_Data.CONTENT_URI};
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_STUDENTLIFE_AUDIO, true);

            SharedPreferences sp = getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
            int previousVersionCode = sp.getInt(SHARED_PREF_KEY_VERSION_CODE, -1);

            if (previousVersionCode == -1) {
                int versionCode = recordFirstOperationInDatabase();
                sp.edit().putInt(SHARED_PREF_KEY_VERSION_CODE, versionCode).commit();
            } else {
                try {
                    PackageInfo pInfo = getApplicationContext().getPackageManager().getPackageInfo(getPackageName(), 0);
                    int currentVersionCode = pInfo.versionCode;
                    if (currentVersionCode > previousVersionCode) {
                        int versionCode = recordFirstOperationInDatabase();
                        sp.edit().putInt(SHARED_PREF_KEY_VERSION_CODE, versionCode).commit();
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    if (Aware.DEBUG) Log.e(TAG, e.getMessage());
                }
            }

            if (audioProbe == null) {
                audioProbe = new Intent(getApplicationContext(), AudioService.class);
                startService(audioProbe);
            }

            Aware.startPlugin(this, PLUGIN_NAME);
            Aware.startAWARE(this);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Aware.setSetting(this, Settings.STATUS_PLUGIN_STUDENTLIFE_AUDIO, false);

        if (audioProbe != null) stopService(audioProbe);

        Aware.stopAWARE(this);
    }

    private int recordFirstOperationInDatabase() {
        String FLAG_PLUGIN_UPDATE = "[PLUGIN UPDATE INSTALL]";
        int versionCode = 0;
        try {
            PackageInfo pInfo = getApplicationContext().getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            versionCode = pInfo.versionCode;
            //As a temporary solution, we will record plugin update status in the application history database
            ContentValues rowData = new ContentValues();
            rowData.put(Applications_Provider.Applications_History.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Applications_Provider.Applications_History.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Applications_Provider.Applications_History.PACKAGE_NAME, getPackageName());
            rowData.put(Applications_Provider.Applications_History.APPLICATION_NAME, FLAG_PLUGIN_UPDATE + "STUDENTLIFE AUDIO" + ", VersionName:" + versionName + ", VersionCode:" + versionCode);
            rowData.put(Applications_Provider.Applications_History.PROCESS_IMPORTANCE, 0);
            rowData.put(Applications_Provider.Applications_History.PROCESS_ID, 0);
            rowData.put(Applications_Provider.Applications_History.END_TIMESTAMP, pInfo.firstInstallTime); //Installation Time;
            rowData.put(Applications_Provider.Applications_History.IS_SYSTEM_APP, 0);
            try {
                getContentResolver().insert(Applications_Provider.Applications_History.CONTENT_URI, rowData);
            } catch (SQLiteException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
            } catch (SQLException e) {
                if (Aware.DEBUG) Log.e(TAG, e.getMessage());
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (Aware.DEBUG) Log.e(TAG, e.getMessage());
        } catch (Exception e) {
            if (Aware.DEBUG) Log.e(TAG, e.getMessage());
        }

        return versionCode;
    }
}
