package edu.cornell.audioProbe;

import android.content.ContentValues;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.plugin.studentlife.audio_final.Plugin;
import com.aware.plugin.studentlife.audio_final.Provider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import edu.dartmouth.studentlife.AudioLib.AudioUtil;
import edu.dartmouthcs.UtilLibs.MyDataTypeConverter;

public class AudioManager {
    /**
     * INITIALIZING : recorder is initializing; READY : recorder has been
     * initialized, recorder not yet started RECORDING : recording ERROR :
     * reconstruction needed STOPPED: reset needed
     */
    public enum State {
        INITIALIZING, READY, RECORDING, ERROR, STOPPED
    }

    public static final boolean DISABLE_AUDIO_CLASSIFIER = false;
    public static final boolean SAVE_AUDIO_RAW_DATA = false;
    public static final boolean RECORDING_UNCOMPRESSED = true;
    public static final boolean RECORDING_COMPRESSED = false;

    // The interval in which the recorded samples are output to the file
    // Used only in uncompressed mode
    private static final int TIMER_INTERVAL = 120;

    // Toggles uncompressed recording on/off; RECORDING_UNCOMPRESSED /
    // RECORDING_COMPRESSED
    private boolean rUncompressed;

    // Recorder used for uncompressed recording
    private AudioRecord aRecorder = null;
    // Recorder used for compressed recording
    private MediaRecorder mRecorder = null;

    // Stores current amplitude (only in uncompressed mode)
    private int cAmplitude = 0;
    // Output file path
    private String fPath = null;

    // Recorder state; see State
    private State state;

    private Context mContext;

    static {
        System.loadLibrary("audioclassifier");
    }

    // Number of channels, sample rate, sample size(size in bits), buffer size,
    // audio_final source, sample size(see AudioFormat)
    private short nChannels = 1;
    private int sRate;
    private short bSamples;
    private int bufferSize;
    private int aSource;
    private int mChannelConfig;

    private int aFormat;

    // Number of frames written to file on each output(only in uncompressed
    // mode)
    private int framePeriod;

    // Buffer for output(only in uncompressed mode)
    private short[] buffer;
    private short[] tempBuffer = {-68, 8, 22, 40, 94, 77, 119, 126, 80, 82,
            61, 60, 80, 64, 79, 51, 4, 9, -7, 14, 20, -9, -16, 19, -28, -50,
            -38, -82, -135, -120, -112, -95, -105, -74, 10, 53, 15, 52, 88, 21,
            32, 15, -31, 13, 22, 32, 8, 12, 89, 88, 42, 22, 7, -49, -115, -148,
            -117, 22, 33, 65, 138, 133, 78, 60, 89, 92, 83, 67, 53, 8, -17,
            -35, -31, -35, -21, 4, -2, 27, -18, -97, -79, -63, -54, -26, -3,
            -38, -58, -34, -48, -19, 29, 17, -15, -3, -46, -91, -65, 10, 106,
            112, 110, 72, 83, 46, -14, 13, 54, 117, 116, 77, 23, -4, 48, 76,
            31, -5, 8, 1, -21, -47, -104, -129, -141, -110, -47, -13, 4, 57,
            -7, -40, -87, -62, -12, 20, 48, 40, 41, 34, 34, -7, -29, -57, -115,
            -100, -75, -69, -38, 36, 43, 2, 3, 0, -19, -60, -92, -32, -37, -25,
            -7, -14, -22, -12, 9, 11, 2, -19, 25, 24, -1, 31, 69, 47, -34, -67,
            -101, -129, -130, -115, -51, 1, 29, 53, 42, 26, 9, 22, 33, 65, 138,
            133, 78, 60, 89, 92, 83, 67, 53, 8, -17, -35, -31, -35, -21, 4, -2,
            27, -18, -97, -79, -63, -54, -26, -3, -38, -58, -34, -48, -19, 29,
            17, -15, -3, -46, -91, -65, 10, 106, 112, 110, 72, 83, 46, -14, 13,
            54, 117, 116, 77, 23, -4, 48, 76, 31, -5, 8, 1, -21, -47, -104,
            -129, -141, -110, -47, -13, 4, 57};

    // audio_final feature options
    private final int FRAME_SIZE_MULTIPLIER = 8;
    private final int FRAME_SIZE = 256;
    private int FRAME_STEP = FRAME_SIZE / 2;
    private int INFERENCE_FRAME_SIZE = 128;
    private short[] audioFrame;

    // audio_final buffer
    private short audioBuffer[][];
    private int audioBufferSize = 500;
    private int audioBufferNextPos = 0;

    // feature and audio_final syncing
    private int sync_id_counter = 0;
    private FileOutputStream fOut;
    private OutputStreamWriter osw;
    private int writeCounter = 0;
    public boolean recordingStopped;

    public class AudioData {
        public short data[];
        public long timestamp;
        public int sync_id;

        public AudioData() {
        }

        public AudioData(short[] data, long timestamp, int sync_id) {
            // this.data = data;
            System.arraycopy(data, 0, audioBuffer[audioBufferNextPos], 0,
                    FRAME_STEP * FRAME_SIZE_MULTIPLIER);
            this.data = audioBuffer[audioBufferNextPos];
            audioBufferNextPos = (audioBufferNextPos + 1) % audioBufferSize;

            this.timestamp = timestamp;
            this.sync_id = sync_id;
        }
    }

    private CircularBufferFeatExtractionInference<AudioData> cirBuffer;
    private AudioData audioFromQueueData = new AudioData();
    private long tempTimestamp = 0;

    // /////////////////////////////////////////////////////////////
    // ////////// Conversation detection codes:start /////////////
    // ////////////////////////////////////////////////////////////

    // all conversation decision making variables are here
    // private double[] extractedFeatures;
    private double currentInference;
    private double leavingInference;
    private double minuteToLookBackForPopup = 1; // means only history of last
    // minute will be kept
    private double[] circularQueueOfInference;
    private final int LengthCircularQueueOfInference = (int) (minuteToLookBackForPopup * 3750); // number
    // of
    // inferences
    // possible
    // in
    // minuteToLookBackForPopup
    // minutes.
    // 60*8000/128
    // =
    // 3750
    private double sumOfPreviousInferences = 0;
    private int indexToCircularQueueOfInference = 0;
    private boolean inCoversation;
    private boolean conversationIntentSent;
    // currently at 3 percent is the threshold
    private double thresholdForConversation = ((double) LengthCircularQueueOfInference) * 3.0 / 100.0;
    private long conversationStartTime;
    private long conversationEndTime;

    // conversation pop-up timer code
    private Handler mHandler = new Handler();
    private final int rateNotification = 1000 * 10; // every 10 seconds

    // ////////////////////////////////////////////////////////////
    // ////////// Conversation detection codes:end /////////////
    // ////////////////////////////////////////////////////////////

    // /////////////////////////////////////////////////////////////
    // ////////// Native Function Declaration /////////////
    // ////////////////////////////////////////////////////////////

    private native int energy(short[] array);

    // private native double[] features(short[] array);
    private native void features(short[] audio, float[] voicingFeatures,
                                 float[] observationProbability, byte[] inferenceResults,
                                 int[] numbeOfPeaks, float[] autoCorrelationPeaks,
                                 short[] autoCorrelationPeakLags);

    private native void audioFeatureExtractionInit();

    private native void audioFeatureExtractionDestroy();

    // Important !!!
    // NOTE: Pay attention to the Primitive Data Types
    private float[] voicingFeatures = new float[6]; // 6 audio_final features
    private byte[] inferanceResults = new byte[20]; // ??? why 20 bytes?
    private float[] observationProbability = new float[2]; // 2 probabilities
    // for voiced and
    // unvoiced
    // respectively
    private float[] autoCorrelationPeaks = new float[128]; // autocorrelation
    // peak values
    private short[] autoCorrelationPeakLags = new short[128]; // autocorrelation
    // peak lags
    private int[] numberOfPeaks = new int[1]; // ??? why redundant?

    private String dataString;

    private int audioEnergy;


    /**
     * Returns the state of the recorder in a RehearsalAudioRecord.State typed
     * object. Useful, as no exceptions are thrown.
     *
     * @return recorder state
     */
    public State getState() {
        return state;
    }

    /**
     * This thread fetches the audio_final data from the queue And process the data by
     * calling native C functions
     */
    public class MyQueuePopper extends Thread {

        CircularBufferFeatExtractionInference<AudioData> obj;
        double[] audioFrameFeature;
        double[] audioWindowFeature;
        private volatile Thread blinker;

        public MyQueuePopper(
                CircularBufferFeatExtractionInference<AudioData> obj) {
            // initialization
            this.obj = obj;
            // audioFeatureExtractionInit();
            audioFrame = new short[FRAME_SIZE];

            // initialize the first half with zeros
            for (int i = 0; i < FRAME_STEP; i++)
                audioFrame[i] = 0;
        }

        public void stopper() {
            blinker = null;
        }

        @Override
        public void start() {
            blinker = new Thread(this);
            blinker.start();
        }

        private void getFrameInference(int idx) {
            if (!DISABLE_AUDIO_CLASSIFIER) {
                // /////////////////////////////////////////////////////////////
                // ////////// audio_final features is computed here /////////////
                // ////////////////////////////////////////////////////////////

                // decide for conversation
                // a timer has been added which will check every 30 seconds to
                // see whether
                // there is x% percent voice being sensed in the last minute

                features(audioFrame, voicingFeatures, observationProbability,
                        inferanceResults, numberOfPeaks, autoCorrelationPeaks,
                        autoCorrelationPeakLags);

                // /////////////////////////////////////////////////////////////
                // ////////// Conversation detection codes:start /////////////
                // ////////////////////////////////////////////////////////////

                // add the new inference results. 0 = non-human-voice,
                // 1=human-voice.
                leavingInference = circularQueueOfInference[indexToCircularQueueOfInference];
                sumOfPreviousInferences = sumOfPreviousInferences
                        - leavingInference;
                // currentInference = extractedFeatures[8]; // 0 =
                // non-human-voice,
                currentInference = inferanceResults[0]; // 0 = non-human-voice,

                // 1 = human-voice
                sumOfPreviousInferences = sumOfPreviousInferences
                        + currentInference;
                circularQueueOfInference[indexToCircularQueueOfInference] = currentInference;
                indexToCircularQueueOfInference = (indexToCircularQueueOfInference + 1)
                        % LengthCircularQueueOfInference;
            }
            double amplitude = AudioUtil.getAmplitude(buffer);

            recordAudioInference(amplitude, audioFromQueueData.timestamp,
                    audioFromQueueData.sync_id);

            // /////////////////////////////////////////////////////////////
            // ////////// Conversation detection codes:end /////////////
            // ////////////////////////////////////////////////////////////

            // done for overlapping window
            System.arraycopy(audioFromQueueData.data, 0, audioFrame, 0,
                    FRAME_STEP);
        }

        @Override
        public void run() {
            double[] tempFeatures;
            Thread thisThread = Thread.currentThread();
            while (blinker == thisThread) {
                audioFromQueueData = obj.deleteAndHandleData();

                // Log.e("AUDIO", "new audio_final frame!!");
                for (int i = 0; i < FRAME_SIZE_MULTIPLIER; i++) {
                    System.arraycopy(audioFromQueueData.data, FRAME_STEP * i,
                            audioFrame, FRAME_STEP, FRAME_STEP);

                    getFrameInference(i);
                }
            }

        }

    }

    private final int AUDIO_SILENCE = 0;
    private final int AUDIO_NOISE = 1;
    private final int AUDIO_VOICE = 2;
    // private final int AUDIO_ERROR = 3;
    private final double silenceThreshold = 1e8;
    private final int smooth_window = 60; // 80 for 1s
    private int[] cir_inference = new int[smooth_window];
    private double[] cir_audio_energy = new double[smooth_window];
    private int smooth_idx = 0;

    private void recordAudioInference(double amptitude, long ts, int sync_id) {

        int inferent_int = -1;
        if (!DISABLE_AUDIO_CLASSIFIER) {
            inferent_int = (int) inferanceResults[0];

            if (inferent_int == 1) {
                cir_inference[smooth_idx] = AUDIO_VOICE;
            } else {
                cir_inference[smooth_idx] = AUDIO_NOISE;
            }

            byte[] featureArray = MyDataTypeConverter.toByta(voicingFeatures,
                    inferanceResults, observationProbability, numberOfPeaks,
                    autoCorrelationPeaks, autoCorrelationPeakLags);

            //save audio_final features
            //TODO this part is removed for the Balanced Campus project in CMU
//            ContentValues data = new ContentValues();
//            data.put(Provider.StudentLifeAudio_Data.DEVICE_ID,
//                    Aware.getSetting(mContext.getApplicationContext(), Aware_Preferences.DEVICE_ID));
//            data.put(Provider.StudentLifeAudio_Data.DATA_TYPE, Provider.StudentLifeAudio_Data.DATA_TYPE_FEATURE);
//            data.put(Provider.StudentLifeAudio_Data.TIMESTAMP, ts);
//            data.put(Provider.StudentLifeAudio_Data.FEATURE_VECTOR, featureArray);
//
//            mContext.getContentResolver().insert(Provider.StudentLifeAudio_Data.CONTENT_URI, data);

//            if (Plugin.DEBUG) {
//                Log.d(Plugin.TAG, "Features data: " + data.toString());
//            }
        }
        cir_audio_energy[smooth_idx] = amptitude;

        smooth_idx++;
        if (smooth_idx == smooth_window) {
            int energyLevel = (int) getAvgAudioEnergy();

            if (!DISABLE_AUDIO_CLASSIFIER) {
                inferent_int = getSmoothedInference();
            }

            saveAudioInference(inferent_int, energyLevel, ts, sync_id);

            smooth_idx = 0;
        }

    }

    private double getAvgAudioEnergy() {
        double sum = 0;
        for (int i = 0; i < smooth_window; i++) {
            sum += cir_audio_energy[i];
        }

        return sum / smooth_window;
    }

    private int getSmoothedInference() {
        int[] vote = new int[3];
        for (int i = 0; i < smooth_window; i++) {
            vote[cir_inference[i]]++;
        }

        if (vote[AUDIO_SILENCE] * 0.5 > vote[AUDIO_NOISE] + vote[AUDIO_VOICE]) {
            return AUDIO_SILENCE;
        } else if (vote[AUDIO_NOISE] * 0.8 > vote[AUDIO_VOICE]) {
            return AUDIO_NOISE;
        } else {
            return AUDIO_VOICE;
        }
    }

    private void saveAudioInference(int inference_int, int energyLevel,
                                    long ts, int sync_id) {
        ContentValues data = new ContentValues();
        data.put(Provider.StudentLifeAudio_Data.DEVICE_ID, Aware.getSetting(mContext.getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put(Provider.StudentLifeAudio_Data.DATA_TYPE, Provider.StudentLifeAudio_Data.DATA_TYPE_INFERENCE);
        data.put(Provider.StudentLifeAudio_Data.AUDIO_ENERGY, energyLevel);
        data.put(Provider.StudentLifeAudio_Data.INFERENCE, inference_int);
        data.put(Provider.StudentLifeAudio_Data.TIMESTAMP, ts);

        mContext.getContentResolver().insert(Provider.StudentLifeAudio_Data.CONTENT_URI, data);

        if (Aware.DEBUG) {
            Log.d(Aware.TAG, "Inference data: " + data.toString());
        }
    }

    private MyQueuePopper myQueuePopper;
    public boolean freeCMemoryActivated;

    /**
     * Method used for recording and storing data to the buffer for queue
     * insertion
     */
    private int rec_counter = 0;
    private int rec_sampling_rate = 2;
    private long lastFrameTimeStamp = 0;
    private AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener() {
        public void onPeriodicNotification(AudioRecord recorder) {
            rec_counter = (rec_counter + 1) % rec_sampling_rate;

            // ////////////////////////////////////////////////////
            // /////// buffer contains the audio_final data /////////////
            // ////////////////////////////////////////////////////
            aRecorder.read(buffer, 0, buffer.length); // Fill buffer with
            // available audio_final
            lastFrameTimeStamp = System.currentTimeMillis();

            // Log.e("AudioCapture","Got a Frame");
            if (rec_counter != 0) {
                // return;
            }
            if (!recordingStopped) {
                // put data in circular buffer for processing
                // you can do other stuffs with the data
                tempTimestamp = System.currentTimeMillis();
                ++sync_id_counter;

                // input to cicular buffer
                cirBuffer.insert(new AudioData(buffer, tempTimestamp,
                        sync_id_counter % 16384));
            } else {
            }

        }

        public void onMarkerReached(AudioRecord recorder) {
            // NOT USED
        }
    };

    // /////////////////////////////////////////////////////////////
    // ////////// Conversation detection codes:start /////////////
    // ////////////////////////////////////////////////////////////

    public boolean isInConversation() {
        return inCoversation;
    }

    // start and end time of conversation get set here
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            final double start_threshold = 600;
            final double stop_threshold = 400;

            if (!recordingStopped && System.currentTimeMillis() - lastFrameTimeStamp > 5 * 1000) {
                Log.e(Aware.TAG, "no audio_final frames in 1000ms, restart");
                restartRecording();
            }
            // this code every 10 seconds look how much conversation is present
            // in the last minute (or minuteToLookBackForPopup)

            if (sumOfPreviousInferences > start_threshold)// 2*thresholdForConversation)
            {
                if (inCoversation == false) {// then we are going from
                    // non-conversation to conversation
                    conversationStartTime = System.currentTimeMillis() - 10 * 1000;
                    inCoversation = true;
                    conversationIntentSent = false;// means send it next time
                    Log.e(Aware.TAG, "Starting a conversation");
                    //appState.convo_inference = "in a converstion";

                    Plugin.IN_CONVERSATION = true;
                    if (Plugin.sContextProducer != null)
                        Plugin.sContextProducer.onContext();
                }
            } else if (sumOfPreviousInferences < stop_threshold)// thresholdForConversation)
            // //conversation finished
            {
                inCoversation = false;
                if (conversationIntentSent == false)// I want to send only when
                // previous call was in
                // conversation. This
                // happens when a
                // conversation ends.
                {

                    Plugin.IN_CONVERSATION = false;
                    if (Plugin.sContextProducer != null)
                        Plugin.sContextProducer.onContext();

                    // Log.e("CurrentSum From Timer","Intent Sent");
                    conversationIntentSent = true;
                    // vibrateNotification();

                    conversationEndTime = System.currentTimeMillis();
                    Log.e(Aware.TAG, "Finished a conversation");
                    //appState.convo_inference = "not in a converstion";
                    sendConversationInfo(conversationStartTime,
                            conversationEndTime);
                }

            }

            mHandler.removeCallbacks(mUpdateTimeTask);
            mHandler.postDelayed(mUpdateTimeTask, rateNotification);
        }
    };

    // /////////////////////////////////////////////////////////////
    // ////////// Conversation detection codes:end /////////////
    // ////////////////////////////////////////////////////////////

    protected void sendConversationInfo(final long start_ts, final long finish_ts) {
        ContentValues data = new ContentValues();
        data.put(Provider.StudentLifeAudio_Data.TIMESTAMP, System.currentTimeMillis());
        data.put(Provider.StudentLifeAudio_Data.DEVICE_ID, Aware.getSetting(mContext.getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put(Provider.StudentLifeAudio_Data.DATA_TYPE, Provider.StudentLifeAudio_Data.DATA_TYPE_CONVO);
        data.put(Provider.StudentLifeAudio_Data.CONVO_START, start_ts);
        data.put(Provider.StudentLifeAudio_Data.CONVO_END, finish_ts);

        mContext.getContentResolver().insert(Provider.StudentLifeAudio_Data.CONTENT_URI, data);

        if (Aware.DEBUG) {
            Log.d(Aware.TAG, "Conversation data: " + data.toString());
        }
    }

    /**
     * Default constructor
     * <p/>
     * Instantiates a new recorder, in case of compressed recording the
     * parameters can be left as 0. In case of errors, no exception is thrown,
     * but the state is set to ERROR
     */
    public AudioManager(Context context,
                        boolean uncompressed, int audioSource, int sampleRate,
                        int channelConfig, int audioFormat) {

        mContext = context;

        try {
            rUncompressed = uncompressed;
            if (rUncompressed) { // RECORDING_UNCOMPRESSED

                if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                    bSamples = 16;
                } else {
                    bSamples = 8;
                }

                if (channelConfig == AudioFormat.CHANNEL_IN_MONO) {
                    nChannels = 1;
                } else {
                    nChannels = 2;
                }

                aSource = audioSource;
                sRate = sampleRate;
                aFormat = audioFormat;
                mChannelConfig = channelConfig;

                framePeriod = this.FRAME_STEP * FRAME_SIZE_MULTIPLIER;
                bufferSize = framePeriod * 100 * bSamples * nChannels / 8;
                if (bufferSize < AudioRecord.getMinBufferSize(sampleRate,
                        channelConfig, audioFormat)) { // Check to make sure
                    // buffer size is not
                    // smaller than the
                    // smallest allowed one
                    bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                            channelConfig, audioFormat);
                    // Set frame period and timer interval accordingly
                    framePeriod = bufferSize / (2 * bSamples * nChannels / 8);
                    Log.w(AudioManager.class.getName(),
                            "Increasing buffer size to "
                                    + Integer.toString(bufferSize));
                }

                aRecorder = new AudioRecord(aSource, sRate,
                        channelConfig, aFormat, bufferSize);
                if (aRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    //FileLogger.dblog(appState,"AudioRecord initialization failed");
                    //throw new Exception("AudioRecord initialization failed");
                }
                aRecorder.setRecordPositionUpdateListener(updateListener);
                aRecorder.setPositionNotificationPeriod(framePeriod);

                // add a new buffer for putting audio_final-stuff
                cirBuffer = new CircularBufferFeatExtractionInference<AudioData>(
                        null, 400);

                // array puller
                audioBuffer = new short[audioBufferSize][FRAME_STEP
                        * FRAME_SIZE_MULTIPLIER];

                // start a new thread for reading audio_final stuff
                myQueuePopper = new MyQueuePopper(cirBuffer);
                myQueuePopper.start();

                // initialize percentage computation queue
                // total number of voiced frames in the last
                // minuteToLookBackForPopup minutes
                // 60*8000/128=3750
                circularQueueOfInference = new double[(int) (minuteToLookBackForPopup * 3750)];

                inCoversation = false;

                // if conversationIntentSent==false and if non-conversation is
                // found then we will send intent.
                // If conversationIntentSent==true then if non-conversation is
                // found then we will not send intent.
                conversationIntentSent = true;

                // timer for conversation decision
                if (!DISABLE_AUDIO_CLASSIFIER) {
                    mHandler.removeCallbacks(mUpdateTimeTask);
                    mHandler.postDelayed(mUpdateTimeTask, rateNotification);
                }

            } else { // RECORDING_COMPRESSED
                // not used
                mRecorder = new MediaRecorder();
                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            }
            cAmplitude = 0;
            fPath = null;
            state = State.INITIALIZING;
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(AudioManager.class.getName(), e.getMessage());
            } else {
                Log.e(AudioManager.class.getName(),
                        "Unknown error occured while initializing recording");
            }
            state = State.ERROR;
        }
    }

    /**
     * Sets output file path, call directly after construction/reset.
     * <p/>
     * file path
     */
    public void setOutputFile(String argPath) {
        try {
            if (state == State.INITIALIZING) {
                fPath = argPath;
                if (!rUncompressed) {
                    mRecorder.setOutputFile(fPath);
                }
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(AudioManager.class.getName(), e.getMessage());
            } else {
                Log.e(AudioManager.class.getName(),
                        "Unknown error occured while setting output path");
            }
            state = State.ERROR;
        }
    }

    /**
     * Returns the largest amplitude sampled since the last call to this method.
     *
     * @return returns the largest amplitude since the last call, or 0 when not
     * in recording state.
     */
    public int getMaxAmplitude() {
        if (state == State.RECORDING) {
            if (rUncompressed) {
                int result = cAmplitude;
                cAmplitude = 0;
                return result;
            } else {
                try {
                    return mRecorder.getMaxAmplitude();
                } catch (IllegalStateException e) {
                    return 0;
                }
            }
        } else {
            return 0;
        }
    }

    /**
     * Prepares the recorder for recording, in case the recorder is not in the
     * INITIALIZING state and the file path was not set the recorder is set to
     * the ERROR state, which makes a reconstruction necessary. In case
     * uncompressed recording is toggled, the header of the wave file is
     * written. In case of an exception, the state is changed to ERROR
     */
    public void prepare() {
        try {
            if (state == State.INITIALIZING) {
                if (rUncompressed) {
                    if ((aRecorder.getState() == AudioRecord.STATE_INITIALIZED)
                            & (fPath != null)) {
                        buffer = new short[framePeriod * bSamples / 16
                                * nChannels];
                        state = State.READY;
                    } else {
                        Log.e(AudioManager.class.getName(),
                                "prepare() method called on uninitialized recorder");
                        state = State.ERROR;
                    }
                } else {
                    mRecorder.prepare();
                    state = State.READY;
                }
            } else {
                Log.e(AudioManager.class.getName(),
                        "prepare() method called on illegal state");
                release();
                state = State.ERROR;
            }
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.e(AudioManager.class.getName(), e.getMessage());
            } else {
                Log.e(AudioManager.class.getName(),
                        "Unknown error occured in prepare()");
            }
            state = State.ERROR;
        }
    }

    public State getManagerState() {
        return state;
    }

    /**
     * Releases the resources associated with this class, and removes the
     * unnecessary files, when necessary
     */
    public void release() {
        if (state == State.RECORDING) {

            stop();

        } else {
            if ((state == State.READY) & (rUncompressed)) {
                (new File(fPath)).delete();
            }
        }

        if (rUncompressed) {
            if (aRecorder != null) {
                aRecorder.release();

                recordingStopped = true;
                // aRecorder = null;

                // audioFeatureExtractionDestroy();
                // stop the timer.
                mHandler.removeCallbacks(mUpdateTimeTask);
                if (inCoversation) {
                    conversationEndTime = System.currentTimeMillis();
                    Log.e("AudioManager",
                            "Finished a conversation due to release()");

                    sendConversationInfo(conversationStartTime,
                            conversationEndTime);
                }
            }
        } else {
            if (mRecorder != null) {
                mRecorder.release();

            }
        }
    }

    /**
     * Resets the recorder to the INITIALIZING state, as if it was just created.
     * In case the class was in RECORDING state, the recording is stopped. In
     * case of exceptions the class is set to the ERROR state.
     */
    public void reset() {
        try {
            if (state != State.ERROR) {
                release();
                fPath = null; // Reset file path
                cAmplitude = 0; // Reset amplitude
                if (rUncompressed) {
                    aRecorder = new AudioRecord(aSource, sRate, nChannels + 1,
                            aFormat, bufferSize);
                } else {
                    mRecorder = new MediaRecorder();
                    mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mRecorder
                            .setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                    mRecorder
                            .setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                }
                state = State.INITIALIZING;
            }
        } catch (Exception e) {
            Log.e(AudioManager.class.getName(), e.getMessage());
            state = State.ERROR;
        }
    }

    /**
     * Starts the recording, and sets the state to RECORDING. Call after
     * prepare().
     */
    public void start() {
        try {
            if (state == State.READY) {
                if (rUncompressed) {
                    audioFeatureExtractionInit();
                    aRecorder.startRecording();
                    aRecorder.read(buffer, 0, buffer.length);
                    recordingStopped = false;
                    freeCMemoryActivated = false;
                } else {
                    mRecorder.start();
                }
                state = State.RECORDING;
            } else {
                Log.e(AudioManager.class.getName(),
                        "start() called on illegal state");
                state = State.ERROR;
            }

            Log.d("AudioManager", "start");
        } catch (IllegalStateException e) {
            Log.e(AudioManager.class.getName(), "start() called on illegal state");
            state = State.ERROR;
        }
    }

    /**
     * Stops the recording, and sets the state to STOPPED. In case of further
     * usage, a reset is needed. Also finalizes the wave file in case of
     * uncompressed recording.
     */
    public void stop() {
        if (state == State.RECORDING) {
            if (rUncompressed) {
                aRecorder.stop();
            } else {
                mRecorder.stop();
            }
            audioFeatureExtractionDestroy();

            state = State.STOPPED;
        } else {
            Log.e(AudioManager.class.getName(),
                    "stop() called on illegal state");
            state = State.ERROR;
        }

        Log.d("AudioManager", "stop");
    }

    public void restartRecording() {
        synchronized (this) {
            if (aRecorder == null) {
                aRecorder = new AudioRecord(aSource, sRate,
                        mChannelConfig, aFormat, bufferSize);
                if (aRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioManager", "AudioRecord initialization failed");
                    //FileLogger.dblog(appState,"AudioRecord initialization failed");
                }
                aRecorder.setRecordPositionUpdateListener(updateListener);
                aRecorder.setPositionNotificationPeriod(framePeriod);
            }

            try {
                if (state == State.RECORDING) {
                    aRecorder.stop();
                }
            } catch (Exception ex) {
            }

            try {
                aRecorder.startRecording();
                state = State.RECORDING;
                recordingStopped = false;
                aRecorder.read(buffer, 0, buffer.length);
            } catch (IllegalStateException ex) {
                Log.e("AudioManager", "restartRecording throws " + ex.toString());

                //FileLogger.dblog(appState, "startRecording throws" + ex.toString() +
                //		"; msg: " + ex.getMessage());
            }
        }

        Log.d("AudioManager", "restartRecording");
    }

    public void stopRecording() {
        synchronized (this) {
            try {
                if (state == State.RECORDING) {
                    aRecorder.stop();
                    state = State.STOPPED;
                    recordingStopped = true;
                    sumOfPreviousInferences = 0;
                    for (int i = 0; i < circularQueueOfInference.length; i++) {
                        circularQueueOfInference[i] = 0;
                    }
                    indexToCircularQueueOfInference = 0;
                }
            } catch (IllegalStateException ex) {
                Log.e("AudioManager", "stopRecording throws" + ex.toString());
            }
        }
        Log.d("AudioManager", "stopRecording");
    }

    public void startRecording() {
        synchronized (this) {
            try {
                if (state == State.STOPPED) {
                    aRecorder.startRecording();
                    state = State.RECORDING;
                    recordingStopped = false;
                    aRecorder.read(buffer, 0, buffer.length);
                }
            } catch (IllegalStateException ex) {
                Log.e("AudioManager", "startRecording throws " + ex.toString());
            }
        }
        Log.d("AudioManager", "startRecording");
    }

    /*
     *
     * Converts a byte[2] to a short, in LITTLE_ENDIAN format
     */
    private short getShort(byte argB1, byte argB2) {
        return (short) (argB1 | (argB2 << 8));
    }
}
