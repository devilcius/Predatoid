package com.predatum;

import android.util.Log;
import java.io.File;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;

/**
 * 
 * @author Marcos
 */
public class SongExtraInfo {

	private AudioFile audioFile;
	private MP3AudioHeader audioHeader;
	private final String LAME_ID = "LAME";

	public SongExtraInfo(File file) {
		try {
			audioFile = AudioFileIO.read(file);
			audioHeader = (MP3AudioHeader) audioFile.getAudioHeader();
		} catch (Exception exception) {
			Log.e(getClass().getName(), "", exception);
		}

	}

	public String getSongGenre() {

		Tag tag = audioFile.getTag();
		return tag.getFirst(FieldKey.GENRE);

	}
	
	public String getBitrate() {

		return audioHeader.getBitRate();

	}	
	
	public boolean isLameEncoded() {
		
		return audioHeader.getEncoder().substring(0, 4).equals(LAME_ID);
	}

	public String getLamePreset() {
		String preset = null;
		if(this.isLameEncoded())
		{
			if(audioHeader.isVariableBitRate())
			{
				preset =  audioHeader.getPreset();				
			}
			else
			{
				preset = audioHeader.getBitRate();
			}

		}
		return preset;
	}
}
