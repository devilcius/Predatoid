package com.predatum;

import android.app.Service;
import android.os.IBinder;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore.Audio;
import android.util.Log;
import java.io.File;

public class ScanService extends Service {

    public static final String MIME_EX_AUDIO = "ex-audio/";
    public static final String ACTION_SCAN_DIR = "predatum.com.scan.SCAN_DIR";

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.i(getClass().getSimpleName(), "Extra scanner started, intent=" + intent);
        String path = intent.getData().getPath();
        if (path == null) {
            return;
        }
        scanRecursively(new File(path));
    }

    private void scanRecursively(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                if (f.isDirectory()) {
                    scanRecursively(f);
                } else {
                    String s = f.getPath();
                    String s_lower = s.toLowerCase();
                    if (s_lower.endsWith("flac") || s_lower.endsWith("ogg")
                            || s_lower.endsWith("mp3")
                            || s_lower.endsWith("mp4")
                            || s_lower.endsWith("m4a")
                            || s_lower.endsWith("mp4p")) {
                        new MediaScanTask().execute(s);
                        //this.setMediaContent(s);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "", e);
        }
    }

    class MediaScanTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                // Log.v(TAG, params[0]);
                AudioFile f = AudioFileIO.read(new File(params[0]));
                Tag tag = f.getTag();
                ContentValues values = new ContentValues();
                String title = tag.getFirst(FieldKey.TITLE);
                String data;
                if (Integer.parseInt(Build.VERSION.SDK) >= 8) {
                    data = f.getFile().getCanonicalPath();
                } else {
                    data = f.getFile().getPath();
                }
                values.put(Audio.Media.TITLE, title);
                values.put(Audio.Media.ARTIST, tag.getFirst(FieldKey.ARTIST));
                values.put(Audio.Media.ALBUM, tag.getFirst(FieldKey.ALBUM));
                values.put(Audio.Media.COMPOSER,
                        tag.getFirst(FieldKey.COMPOSER));
                values.put(Audio.Media.DATE_ADDED, System.currentTimeMillis());
                values.put(Audio.Media.DATE_MODIFIED,
                        System.currentTimeMillis());
                values.put(Audio.Media.DISPLAY_NAME, tag.getFirst(title));
                values.put(Audio.Media.DURATION, f.getAudioHeader().getTrackLength() * 1000);
                values.put(Audio.Media.IS_MUSIC, 1);
                values.put(Audio.Media.SIZE, f.getFile().length());
                values.put(Audio.Media.TRACK, tag.getFirst(FieldKey.TRACK));
                values.put(Audio.Media.YEAR, tag.getFirst(FieldKey.YEAR));
  //              values.put(Audio.Media.GENRE, tag.getFirst(FieldKey.GENRE));
                values.put(Audio.Media.DATA, data);
                values.put(Audio.Media.MIME_TYPE, MIME_EX_AUDIO
                        + f.getAudioHeader().getFormat().split(" ")[0]);
                int updated = getContentResolver().update(
                        Audio.Media.EXTERNAL_CONTENT_URI, values,
                        Audio.Media.DATA + "=?", new String[]{data});
                // Log.i(TAG, updated + " rows updated.");
                if (updated <= 0) {
                    Uri uri = getContentResolver().insert(
                            Audio.Media.EXTERNAL_CONTENT_URI, values);
                    // Log.i(TAG, "Newly inserted, uri=" + uri);
                }
            } catch (Exception e) {
                Log.e(getClass().getName(), "", e);
            }
            return null;
        }
    }

    protected ContentValues setMediaContent(String... params) {

        try {
            // Log.v(TAG, params[0]);
            AudioFile f = AudioFileIO.read(new File(params[0]));
            Tag tag = f.getTag();
            ContentValues values = new ContentValues();
            String title = tag.getFirst(FieldKey.TITLE);
            String data;
            if (Integer.parseInt(Build.VERSION.SDK) >= 8) {
                data = f.getFile().getCanonicalPath();
            } else {
                data = f.getFile().getPath();
            }
            values.put(Audio.Media.TITLE, title);
            values.put(Audio.Media.ARTIST, tag.getFirst(FieldKey.ARTIST));
            values.put(Audio.Media.ALBUM, tag.getFirst(FieldKey.ALBUM));
            values.put(Audio.Media.COMPOSER,
                    tag.getFirst(FieldKey.COMPOSER));
            values.put(Audio.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(Audio.Media.DATE_MODIFIED,
                    System.currentTimeMillis());
            values.put(Audio.Media.DISPLAY_NAME, tag.getFirst(title));
            values.put(Audio.Media.DURATION, f.getAudioHeader().getTrackLength() * 1000);
            values.put(Audio.Media.IS_MUSIC, 1);
            values.put(Audio.Media.SIZE, f.getFile().length());
            values.put(Audio.Media.TRACK, tag.getFirst(FieldKey.TRACK));
            values.put(Audio.Media.YEAR, tag.getFirst(FieldKey.YEAR));
//            values.put(Audio.Genres.NAME, tag.getFirst(FieldKey.GENRE));
            values.put(Audio.Media.DATA, data);
            values.put(Audio.Media.MIME_TYPE, MIME_EX_AUDIO
                    + f.getAudioHeader().getFormat().split(" ")[0]);
            int updated = getContentResolver().update(
                    Audio.Media.EXTERNAL_CONTENT_URI, values,
                    Audio.Media.DATA + "=?", new String[]{data});
            // Log.i(TAG, updated + " rows updated.");
            if (updated <= 0) {
                Uri uri = getContentResolver().insert(
                        Audio.Media.EXTERNAL_CONTENT_URI, values);
                // Log.i(TAG, "Newly inserted, uri=" + uri);
            }
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
        }
        return null;


    }

    @Override
    public IBinder onBind(Intent arg0) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
