package com.predatum;

import android.util.Log;
import java.io.File;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

/**
 *
 * @author marcos
 */
public class SongExtraInfo {

    public String getSongGenre(File file) {
        String genre = "";
        try {
            AudioFile f = AudioFileIO.read(file);
            Tag tag = f.getTag();
            genre = tag.getFirst(FieldKey.GENRE);
        } catch (Exception e) {
            Log.e(getClass().getName(), "", e);
        }

        return genre;
    }
}
