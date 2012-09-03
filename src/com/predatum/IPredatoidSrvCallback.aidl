package com.predatum;

oneway interface IPredatoidSrvCallback {
    void playItemChanged(boolean error, String name, int trackNum);
	void errorReported(String name);
	void playItemPaused(boolean paused);
}