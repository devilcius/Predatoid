package com.predatum;

oneway interface IPredatoidSrvCallback {
    void playItemChanged(boolean error, String name);
	void errorReported(String name);
	void playItemPaused(boolean paused);
}