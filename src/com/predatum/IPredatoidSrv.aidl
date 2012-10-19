package com.predatum;

import com.predatum.IPredatoidSrvCallback;

interface IPredatoidSrv {
	boolean initPlaylist(in String path, int nitems);
	boolean addToPlaylist(in String track_source, in String track_name, int start_time, int pos, in Map songMetaData);
	boolean play(int n, int start);
	boolean seekTo(int p);
	boolean playNext();
	boolean playPrevious();
	boolean pause();
	boolean resume();
	boolean increaseVolume();
	boolean decreaseVolume();
	boolean shutdown();
	boolean isRunning();
	boolean isPaused();
	String  getCurrentDirectory();
	int	getCurrentPosition();
	String  getCurrentTrackSource();
	int	getCurrentSeconds();
	int	getTrackDuration();
	int	getCurrentTrackLength();
	int	getCurrentTrackStart();
	String  getCurrentTrackName();
	void	setHeadsetMode(int mode);
	void	setLoopPlaying(boolean ok);
	void    registerCallback(IPredatoidSrvCallback cb);
        void    unregisterCallback(IPredatoidSrvCallback cb);
        void	launch(in String path);
}
