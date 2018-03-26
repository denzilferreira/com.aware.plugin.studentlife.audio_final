package com.aware.plugin.studentlife.audio_final;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

public class Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 6;

    /**
     * Provider authority: com.aware.plugin.ambient_noise.provider.ambient_noise
     */
    public static String AUTHORITY = "com.aware.plugin.studentlife.audio_final.provider.audio_final";

    private static final int URI_CHECK_AUDIO = 1;
    private static final int URI_CHECK_AUDIO_ID = 2;

    public static final String DATABASE_NAME = "plugin_studentlife_audio_android.db";

    public static final String[] DATABASE_TABLES = {
            "plugin_studentlife_audio_android"
    };


    //data type: 0-inferences, 1-features
    public static final String[] TABLES_FIELDS = {
            StudentLifeAudio_Data._ID + " integer primary key autoincrement," +
            StudentLifeAudio_Data.TIMESTAMP + " real default 0," +
            StudentLifeAudio_Data.DEVICE_ID + " text default ''," +
            StudentLifeAudio_Data.DATA_TYPE + " integer default 0," +
            StudentLifeAudio_Data.AUDIO_ENERGY + " real default 0," +
            StudentLifeAudio_Data.INFERENCE + " integer default -1," +
            StudentLifeAudio_Data.FEATURE_VECTOR + " blob default null," +
            StudentLifeAudio_Data.CONVO_START + " real default 0," +
            StudentLifeAudio_Data.CONVO_END + " real default 0"
    };

    public static final class StudentLifeAudio_Data implements BaseColumns {
        public static final int DATA_TYPE_INFERENCE = 0;
        public static final int DATA_TYPE_FEATURE = 1;
        public static final int DATA_TYPE_CONVO = 2;

        private StudentLifeAudio_Data() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + DATABASE_TABLES[0]);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.studentlife.audio_final";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.studentlife.audio_final";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String DATA_TYPE = "datatype";
        public static final String AUDIO_ENERGY = "double_energy";
        public static final String INFERENCE = "inference";
        public static final String FEATURE_VECTOR = "blob_feature";
        public static final String CONVO_START = "double_convo_start";
        public static final String CONVO_END = "double_convo_end";
    }

    private static UriMatcher URIMatcher;
    private static HashMap<String, String> databaseMap;
    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;
    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.audio_final";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {

        AUTHORITY = getContext().getPackageName() + ".provider.audio_final";

        URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], URI_CHECK_AUDIO);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", URI_CHECK_AUDIO_ID);

        databaseMap = new HashMap<>();
        databaseMap.put(StudentLifeAudio_Data._ID, StudentLifeAudio_Data._ID);
        databaseMap.put(StudentLifeAudio_Data.TIMESTAMP, StudentLifeAudio_Data.TIMESTAMP);
        databaseMap.put(StudentLifeAudio_Data.DEVICE_ID, StudentLifeAudio_Data.DEVICE_ID);
        databaseMap.put(StudentLifeAudio_Data.DATA_TYPE, StudentLifeAudio_Data.DATA_TYPE);
        databaseMap.put(StudentLifeAudio_Data.AUDIO_ENERGY, StudentLifeAudio_Data.AUDIO_ENERGY);
        databaseMap.put(StudentLifeAudio_Data.INFERENCE, StudentLifeAudio_Data.INFERENCE);
        databaseMap.put(StudentLifeAudio_Data.FEATURE_VECTOR, StudentLifeAudio_Data.FEATURE_VECTOR);
        databaseMap.put(StudentLifeAudio_Data.CONVO_START, StudentLifeAudio_Data.CONVO_START);
        databaseMap.put(StudentLifeAudio_Data.CONVO_END, StudentLifeAudio_Data.CONVO_END);

        return true;
    }

    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (URIMatcher.match(uri)) {
            case URI_CHECK_AUDIO:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (URIMatcher.match(uri)) {
            case URI_CHECK_AUDIO:
                return StudentLifeAudio_Data.CONTENT_TYPE;
            case URI_CHECK_AUDIO_ID:
                return StudentLifeAudio_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {
        initialiseDatabase();

        ContentValues values = (initialValues != null) ? new ContentValues(
                initialValues) : new ContentValues();

        database.beginTransaction();

        switch (URIMatcher.match(uri)) {
            case URI_CHECK_AUDIO:
                long weather_id = database.insertWithOnConflict(DATABASE_TABLES[0], StudentLifeAudio_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (weather_id > 0) {
                    Uri new_uri = ContentUris.withAppendedId(
                            StudentLifeAudio_Data.CONTENT_URI,
                            weather_id);
                    getContext().getContentResolver().notifyChange(new_uri, null, false);
                    return new_uri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URIMatcher.match(uri)) {
            case URI_CHECK_AUDIO:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(databaseMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());

            return null;
        }
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {

        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (URIMatcher.match(uri)) {
            case URI_CHECK_AUDIO:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
}
