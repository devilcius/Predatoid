package com.predatum;

oneway interface IPredatoidSrvCallback {
    void playItemChanged(boolean error, String name, int trackNum, in Map songMetaData);
	void errorReported(String name);
	void playItemPaused(boolean paused);
}