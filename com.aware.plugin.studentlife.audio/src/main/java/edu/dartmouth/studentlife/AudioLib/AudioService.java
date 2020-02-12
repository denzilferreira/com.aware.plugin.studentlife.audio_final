package edu.dartmouth.studentlife.AudioLib;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.plugin.studentlife.audio_final.Settings;

import java.io.File;

import edu.cornell.audioProbe.AudioManager;
import edu.cornell.audioProbe.AudioManager.State;

// Need the following import to get access to the app resources, since this
// class is in a sub-package.

public class AudioService extends Service {
    private static Context CONTEXT;

    private AudioManager ar;

    // binder
    private final IBinder binder = new AudioBinder();

    // audio_final status
    public String inferred_audio_Status = "Not Available";
    public String prev_inferred_audio_Status = "Not Available";
    private Thread t;

    private Handler mHandler = new Handler();

    private boolean enableDutyCycle = true;

    public long audio_no_of_records;

    int inConversationDelay = 1 * 60 * 1000; //how long we wait listening until we classify
    int inDutyCycleOff = 3 * 60 * 1000; //how long to wait until we sample again
    long audioDutyCyclingSensingInterval = 1 * 60 * 1000; //how long we sample

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate() {
        audio_no_of_records = 0;
        CONTEXT = this;

        inConversationDelay = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_CONVERSATIONS_DELAY)) * 60 * 1000; //how long we wait listening until we classify
        inDutyCycleOff = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_CONVERSATIONS_OFF_DUTY)) * 60 * 1000; //how long to wait until we sample again
        audioDutyCyclingSensingInterval = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_CONVERSATIONS_LENGTH)) * 60 * 1000; //how long we sample

        Log.e("AudioService", "AudioService Created!!" + "\nDelay: " + inConversationDelay + ", offduty: "+ inDutyCycleOff + ", recording for:" + audioDutyCyclingSensingInterval);
    }

    public static Context getContext() {
        return CONTEXT;
    }

    private void startAudioManager() {
        ar = new AudioManager(this, true,
                MediaRecorder.AudioSource.MIC, 8000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
//        ar = new AudioManager(this, true,
//                MediaRecorder.AudioSource.VOICE_RECOGNITION, 8000,
//                AudioFormat.CHANNEL_IN_MONO,
//                AudioFormat.ENCODING_PCM_16BIT);
        try {
            // ar.setOutputFile("/sdcard/audio2.txt");
            // ar.prepare();
            // ar.start();

            t = new Thread() {
                public void run() {
                    startAudioRecording();
                }
            };
            // t.setPriority(Thread.NORM_PRIORITY+1);
            t.start();
        } catch (Exception ex) {
            ar = null;
            Toast.makeText(this, "Cannot create audio_final file \n" + ex.toString(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startAudioRecording() {
        ar.setOutputFile(new File(Environment.getExternalStorageDirectory(),
                "audio3.txt").getAbsolutePath());
        ar.prepare();
        ar.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (isMicrophoneAvailable(getContext())) startAudioManager();

        // duty cycling code
        if (enableAudioDutyCycling) {
            inDutyCycle = true;
            mHandler.removeCallbacks(mUpdateTimeTask);

            // sensing has started already so will need to stop it after sensing
            // interval now
            mHandler.postDelayed(mUpdateTimeTask,
                    audioDutyCyclingSensingInterval);
        }

        audioSensorOn = true;

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    /**
     * Check if the microphone is available or not
     * @param context
     * @return
     */
    public static boolean isMicrophoneAvailable(Context context) {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(new File(context.getCacheDir(), "MediaUtil#micAvailTestFile").getAbsolutePath());
        boolean available = true;
        try {
            recorder.prepare();
            recorder.start();
        } catch (Exception exception) {
            available = false;
        }
        recorder.release();
        return available;
    }

    // final int inConversationDelay = 10 * 1000;
    // final int inDutyCycleOff = 30 * 1000;
    private synchronized void doDutyCycleSchedule() {
        mHandler.removeCallbacks(mUpdateTimeTask);
        if (inDutyCycle) {
            if (ar != null && ar.isInConversation()) {
                mHandler.postDelayed(mUpdateTimeTask, inConversationDelay);
                Log.d("AudioDutyCycle", "AS: In Conversation, postponed for "
                        + inConversationDelay);
            } else {
                try {
                    if (ar != null && ar.getManagerState() == State.RECORDING) {

                        Log.d("AudioDutyCycle", "stop audio_final recording");
                        ar.stopRecording();
                    }
                } catch (Exception ex) {
                    Log.d("AudioDutyCycle",
                            "Failed to stop recording \n" + ex.toString());
                }

                inDutyCycle = false;
                mHandler.postDelayed(mUpdateTimeTask, inDutyCycleOff); // add 3
                // seconds

                Log.d("AudioDutyCycle",
                        "AS: audioDutyCyclingRestartInterval is: "
                                + inDutyCycleOff);
            }
        } else {
            // sensing will start soon. So, restart after
            // appState.audioDutyCyclingSensingInterval
            if (ar.getManagerState() != State.RECORDING) {
                Log.d("AudioDutyCycle", "restart audio_final manager");
                ar.startRecording();
            }
            Log.d("AudioDutyCycle", "AS: audioDutyCyclingNextInterval is: "
                    + inConversationDelay);
            mHandler.postDelayed(mUpdateTimeTask, inConversationDelay);
            inDutyCycle = true;
        }
    }

    boolean audioSensorOn = false;
    boolean enableAudioDutyCycling = true;

    public boolean inDutyCycle;

    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            if (!enableAudioDutyCycling
                    || audioSensorOn == false) {
                mHandler.removeCallbacks(mUpdateTimeTask);
                // sensing has started already so will need to stop it after
                // sensing interval now
                mHandler.postDelayed(mUpdateTimeTask,
                        audioDutyCyclingSensingInterval);

                inDutyCycle = false;

                return;
            }

            if (enableDutyCycle) {
                doDutyCycleSchedule();
            }

        }
    };

    @Override
    public void onDestroy() {
        mHandler.removeCallbacks(mUpdateTimeTask);

        try {
            if (ar != null) {
                ar.release();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Failed to stop recording \n" + ex.toString(),
                    Toast.LENGTH_SHORT).show();
        }
        ar = null;

        Log.e("AudioService", "AudioService Destroyed!!");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class AudioBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }

    public void updateNotificationArea() {
    }
}
