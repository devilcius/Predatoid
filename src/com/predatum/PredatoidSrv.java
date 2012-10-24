package com.predatum;

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PredatoidSrv extends Service {

	// Ad hoc value. 0x2000 seems to be a maximum used by the driver. MSM
	// datasheets needed.
	private int volume = 0x1000;
	// The lock to acquire so as the device won't go to sleep when we'are
	// playing.
	private PowerManager.WakeLock wakeLock = null;
	private NotificationManager nm = null;

	private void logMessage(String msg) {
		Log.i(getClass().getSimpleName(), msg);
	}

	private void logErrorMessage(String msg) {
		Log.e(getClass().getSimpleName(), msg);
	}

	// process headset insert/remove events
	public static final int HANDLE_HEADSET_INSERT = 1;
	public static final int HANDLE_HEADSET_REMOVE = 2;
	public static final String ACTION_VIEW = "Predatoid_view";
	private static int headsetMode = 0;
	private static boolean loopPlaying = false;
	public static int currentTrackLength = 0;
	public static int currentTrackStart = 0;
	public static int currentTrackPosition = 0;
	// Callback used to send new track name or error status to the interface
	// thread.
	private static final RemoteCallbackList<IPredatoidSrvCallback> cBacks = new RemoteCallbackList<IPredatoidSrvCallback>();

	private void informTrack(String trackName, boolean error, HashMap<String, Object> songMetaData) {

		PredatoidSrv.this.notifyStatusBar(R.drawable.play_on, trackName);

		final int k = cBacks.beginBroadcast();
		for (int i = 0; i < k; i++) {
			try {
				if (!error) {
					cBacks.getBroadcastItem(i).playItemChanged(false,
							trackName, plist.currentPosition, songMetaData);
				} else {
					cBacks.getBroadcastItem(i).playItemChanged(true,
							getString(R.string.strStopped),
							plist.currentPosition, songMetaData);
					if (trackName.compareTo(getString(R.string.strStopped)) != 0) {
						cBacks.getBroadcastItem(i).errorReported(trackName);
					}
				}
			} catch (RemoteException e) {
				logErrorMessage("remote exception in informTrack(): " + e.toString());
				break;
			}
		}
		cBacks.finishBroadcast();
	}

	private void informPauseResume(boolean pause) {
		final int k = cBacks.beginBroadcast();
		for (int i = 0; i < k; i++) {
			try {
				cBacks.getBroadcastItem(i).playItemPaused(pause);
			} catch (RemoteException e) {
				logErrorMessage("remote exception in informPauseResume(): "
						+ e.toString());
				break;
			}
		}
		cBacks.finishBroadcast();
	}

	private MediaPlayer mplayer = null;
	private Object mplayer_lock = new Object();
	private int fck_start;
	private boolean isPrepared;

	public int extPlay(String file, int start) {
		try {
			isPrepared = false;
			if (mplayer == null) {
				mplayer = new MediaPlayer();
			}
			fck_start = start;
			mplayer.reset();
			mplayer.setDataSource(file);
			mplayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

				public boolean onError(MediaPlayer mp, int what, int extra) {
					synchronized (mplayer_lock) {
						if (mplayer != null) {
							mplayer.release();
						}
						mplayer = null;
					}
					logErrorMessage("mplayer playback aborted with errors: " + what
							+ ", " + extra);
					informTrack(getString(R.string.strMplayerError), true, null);
					synchronized (mplayer_lock) {
						mplayer_lock.notify();
					}
					return false;
				}
			});
			mplayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

				public void onCompletion(MediaPlayer mp) {
					logMessage("mplayer playback completed");
					synchronized (mplayer_lock) {
						mplayer_lock.notify();
					}
				}
			});
			mplayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

				public void onPrepared(MediaPlayer mp) {
					isPrepared = true;
					if (fck_start != 0) {
						mplayer.seekTo(fck_start * 1000);
					}
					currentTrackLength = mplayer.getDuration() / 1000;
					mplayer.start();
				}
			});
			mplayer.prepare();

			synchronized (mplayer_lock) {
				mplayer_lock.wait();
			}
		} catch (Exception e) {
			logErrorMessage("Exception in extPlay(): " + e.toString());
		} finally {
			synchronized (mplayer_lock) {
				if (mplayer != null) {
					if (mplayer.isPlaying()) {
						mplayer.stop();
					}
					mplayer.release();
				}
				mplayer = null;
			}
		}
		return 0;
	}

	private static final int NOTIFY_ID = R.drawable.icon;

	public void notifyStatusBar(int icon, String s) {
		if (nm == null) {
			return;
		}
		if (s == null) {
			nm.cancel(NOTIFY_ID);
			return;
		}
		final Notification notification = new Notification(icon, s,
				System.currentTimeMillis());
		Intent intent = new Intent();
		intent.setAction("android.intent.action.MAIN");
		intent.addCategory("android.intent.category.LAUNCHER");
		intent.setClass(this, Predatoid.class);
		intent.setFlags(0x10100000);
		notification.setLatestEventInfo(getApplicationContext(), "Predatoid",
				s, PendingIntent.getActivity(this, 0, intent, 0));
		nm.notify(NOTIFY_ID, notification);
	}

	// //////////////////////////////////////////////////////////////
	// ////////////////////// Main class ////////////////////////////
	// //////////////////////////////////////////////////////////////
	private class playlist {

		private String dir; // source file(s) path
		private String[] files; // track source files
		private String[] trackNames; // track names from playlist
		private int[] times; // track start times from cue files
		private int currentPosition; // current track
		private int currentStart; // start seconds the file must be played
		private PlayThread thread; // main thread
		private boolean running; // either playing or paused
		private boolean paused;
		private int driver_mode; // driver mode in client preferences
		private ArrayList<HashMap<String, Object>> trackList;

		public boolean initPlaylist(String path, int items) {
			if (items <= 0) {
				return false;
			}

			files = null;
			currentPosition = -1;
			thread = null;
			paused = false;
			running = false;
			times = null;
			trackNames = null;
			try {
				files = new String[items];
				trackNames = new String[items];
				times = new int[items];
				trackList = new ArrayList<HashMap<String, Object>>(items);
			} catch (Exception e) {
				logErrorMessage("exception in initPlaylist(): " + e.toString());
				return false;
			}
			return true;
		}

		public boolean addToPlaylist(String track_source, String track_name,
				int start_time, int pos, HashMap<String, Object> songMetaData) {
			if (pos >= files.length) {
				return false;
			}
			if (track_source == null) {
				return false;
			}
			try {
				files[pos] = new String(track_source);
				if (track_name != null) {
					trackNames[pos] = new String(track_name);
				} else {
					trackNames[pos] = null;
				}
				times[pos] = start_time;
				trackList.add(pos, songMetaData);
			} catch (Exception e) {
				logErrorMessage("exception in add_to_playlist(): " + e.toString());
				return false;
			}
			return true;
		}

		private int getCurPosition() {

			if (mplayer != null) {
				return mplayer.getCurrentPosition() / 1000;
			}

			return 0;
		}

		private int getDuration() {
			// if(!running) return 0;
			int duration = 0;
			do {
				SystemClock.sleep(200);
			} while (!isPrepared);
			if (mplayer != null && isPrepared) {
				duration = mplayer.getDuration() / 1000;
			}
			return duration;
		}

		private class PlayThread extends Thread {

			private int tid = -1;
			private boolean keepOnRocking = true;
			int k;

			@Override
			public void run() {
				tid = Process.myTid();
				running = true;
				logMessage("run(): starting new thread " + tid);
				Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
				if (!wakeLock.isHeld()) {
					wakeLock.acquire();
				}
				while (keepOnRocking) {
					for (k = 1; running && currentPosition < files.length; currentPosition++) {
						logMessage(Process.myTid() + ": trying "
								+ files[currentPosition] + " @ time "
								+ (times[currentPosition] + currentStart)
								+ " mode=" + driver_mode);
						try {
							currentTrackLength = 0;
							currentTrackStart = 0;

							if (trackNames[currentPosition] != null) {
								logMessage("track name = "
										+ trackNames[currentPosition]);
								informTrack(trackNames[currentPosition], false, trackList.get(currentPosition));
							} else {

								String currentTrack = files[currentPosition];
								int start = currentTrack.lastIndexOf("/") + 1;
								int end = currentTrack.lastIndexOf(".");
								String cf = end > start ? currentTrack
										.substring(start, end) : currentTrack
										.substring(start);
								informTrack(cf, false, trackList.get(currentPosition));
							}
							k = extPlay(files[currentPosition],
									times[currentPosition] + currentStart);

							nm.cancel(NOTIFY_ID);
						} catch (Exception e) {
							logErrorMessage("run(): exception in xxxPlay(): "
									+ e.toString());
							currentStart = 0;
							continue;
						}
						currentStart = 0;
						if (k == 0) {
							logMessage(Process.myTid()
									+ ": xxxPlay() returned normally");
						} else {
							logErrorMessage(String.format(
									"run(): xxxPlay() returned error %d", k));
							running = false;
							String err, s[] = getResources().getStringArray(
									R.array.Errors);
							try {
								err = s[k - 1];
							} catch (Exception e) {
								err = getString(R.string.strInternalError);
							}
							informTrack(err, true, trackList.get(currentPosition));
							break;
						}
					}
					if (loopPlaying) {
						keepOnRocking = true; // yeah!
						currentPosition = 0;
					} else {
						keepOnRocking = false;
					}

				}
				if (wakeLock.isHeld()) {
					wakeLock.release();
				}
				logMessage(Process.myTid() + ": thread about to exit");
				if (k == 0) {
					informTrack(getString(R.string.strStopped), true, trackList.get(currentPosition));
				}

				running = false;
			}

			public int getThreadId() {
				return tid;
			}
		}

		public boolean stop() {
			running = false;
			logMessage("stop()");
			nm.cancel(NOTIFY_ID);
			if (thread != null) {
				int i = Process.getThreadPriority(Process.myTid());
				int tid = thread.getThreadId();
				int k = 0;

				logMessage(String.format(
						"stop(): terminating thread %d from %d", tid,
						Process.myTid()));
				Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
				if (mplayer != null) {
					synchronized (mplayer_lock) {
						mplayer_lock.notify();
					}
				}
				if (paused) {
					paused = false;
				}
				try {
					while (thread.isAlive()) {
						thread.join(100);
						k++;
						if (thread.isAlive()) {
							SystemClock.sleep(1);
						} else {
							break;
						}
						if (k > 2) {
							break;
						}
					}
				} catch (InterruptedException e) {
					logErrorMessage("Interrupted exception in stop(): " + e.toString());
				}
				if (thread.isAlive()) {
					thread.interrupt();
					logMessage(String.format(
							"stop(): thread %d interrupted after %d ms",
							tid, k * 100));
				} else {
					logMessage(String.format(
							"stop(): thread terminated after %d ms", k * 100));
				}
				thread = null;
				Process.setThreadPriority(i);
			} else {
				logMessage(String.format(
						"stop(): player thread was null (my tid %d)",
						Process.myTid()));
			}
			return true;
		}

		public boolean play(int n, int start) {
			logMessage(String.format("play(%d)", n));

			if (files == null || n >= files.length || n < 0) {
				return false;
			}
			currentPosition = n;
			currentStart = start;
			thread = new PlayThread();
			logMessage(String.format("play(): created new thread from %d",
					Process.myTid()));
			thread.start();
			return true;
		}

		public boolean seekTo(int p) {
			logMessage(String.format("seekTo(%d)", p));
			mplayer.seekTo(p);
			return true;
		}

		public boolean playNext() {
			logMessage("play_next()");
			return play(currentPosition + 1, 0);
		}

		public boolean playPrevious() {
			logMessage("playPrevious()");
			return play(currentPosition - 1, 0);
		}

		public boolean pause() {
			logMessage("pause()");
			if (files == null || paused) {
				return false;
			}

			if (mplayer != null) {
				paused = true;
				try {
					mplayer.pause();
				} catch (Exception e) {
					logErrorMessage("Mplayer exception in pause(): " + e.toString());
					paused = false;
				}
			} else {
				int i = Process.getThreadPriority(Process.myTid());
				Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
				Process.setThreadPriority(i);
			}
			if (wakeLock.isHeld()) {
				wakeLock.release();
			}

			return paused == true;
		}

		public boolean resume() {
			logMessage("resume()");
			if (files == null || !paused) {
				return false;
			}
			if (mplayer != null) {
				paused = false;
				try {
					mplayer.start();
				} catch (Exception e) {
					logErrorMessage("Mplayer exception in resume(): " + e.toString());
					paused = true;
				}
			} else {
				int i = Process.getThreadPriority(Process.myTid());
				Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
				Process.setThreadPriority(i);
			}

			if (!paused && !wakeLock.isHeld()) {
				wakeLock.acquire();
			}
			return paused == false;
		}

		public boolean decreaseVolume() {
			logMessage("dec_vol()");
			if (files == null || !running) {
				return false;
			}
			if (mplayer != null) {
				return true;
			}
			int i = Process.getThreadPriority(Process.myTid());
			Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
			if (volume >= 0x400) {
				volume -= 0x400;
			}
			Process.setThreadPriority(i);
			return true;
		}

		public boolean increaseVolume() {
			logMessage("inc_vol()");
			if (files == null || !running) {
				return false;
			}
			if (mplayer != null) {
				return true;
			}
			int i = Process.getThreadPriority(Process.myTid());
			Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
			if (volume <= 0x2000) {
				volume += 0x400;
			}
			Process.setThreadPriority(i);
			return true;
		}
	}

	private static playlist plist = null;
	// ////////////////////////////////////////////////
	// // The interface we expose to clients. It's returned to them when the
	// connection is established.
	private static final IPredatoidSrv.Stub binder = new IPredatoidSrv.Stub() {

		public boolean initPlaylist(String path, int nitems) {
			// plist.stop(); // plist = null; plist = new playlist();
			return plist.initPlaylist(path, nitems);
		}

		public boolean addToPlaylist(String src, String name, int start_time,
				int pos, Map songMetaData) {
			return plist.addToPlaylist(src, name, start_time, pos, (HashMap<String, Object>) songMetaData);
		}

		public boolean play(int n, int start) {
			return plist.play(n, start);
		}

		public boolean seekTo(int p) {
			return plist.seekTo(p);
		}

		public boolean playNext() {
			return plist.playNext();
		}

		public boolean playPrevious() {
			return plist.playPrevious();
		}

		public boolean pause() {
			return plist.pause();
		}

		public boolean resume() {
			return plist.resume();
		}

		public boolean increaseVolume() {
			return plist.increaseVolume();
		}

		public boolean decreaseVolume() {
			return plist.decreaseVolume();
		}

		public boolean shutdown() {
			plist.stop();
			return true;
		}

		public boolean isRunning() {
			return plist.running;
		}

		public boolean isPaused() {
			return plist.paused;
		}

		public String getCurrentDirectory() {
			return plist.dir;
		}

		public int getCurrentPosition() {
			return plist.currentPosition;
		}

		public int getCurrentSeconds() {
			return plist.getCurPosition();
		}

		public int getTrackDuration() {
			return plist.getDuration();
		}

		public int getCurrentTrackStart() {
			return currentTrackStart;
		}

		public int getCurrentTrackLength() {
			return currentTrackLength;
		}

		public String getCurrentTrackSource() {
			try {
				return plist.files[plist.currentPosition];
			} catch (Exception e) {
				return null;
			}
		}

		public String getCurrentTrackName() {
			try {
				return plist.trackNames[plist.currentPosition];
			} catch (Exception e) {
				return null;
			}
		}

		public void setHeadsetMode(int m) {
			headsetMode = m;
		}

		public void setLoopPlaying(boolean ok) {
			loopPlaying = ok;
		}

		public void registerCallback(IPredatoidSrvCallback cb) {
			if (cb != null) {
				cBacks.register(cb);
			}
		}

		public void unregisterCallback(IPredatoidSrvCallback cb) {
			if (cb != null) {
				cBacks.unregister(cb);
			}
		}

		public void launch(String path) {
			if (launcher != null) {
				launcher.launch(path);
			}
		}
	};

	private class Launcher {

		void launch(String path) {
			startActivity((new Intent()).setAction(PredatoidSrv.ACTION_VIEW)
					.setData(Uri.fromFile(new File(path))));
		}
	}

	static Launcher launcher = null;

	// /////////////////////////////////////////////////////
	// /////////////////// Overrides ///////////////////////
	@Override
	public IBinder onBind(Intent intent) {
		logMessage("onBind()");
		return binder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		logMessage("onCreate()");
		registerPhoneListener();
		registerHeadsetReciever();
		if (wakeLock == null) {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this
					.getClass().getName());
			wakeLock.setReferenceCounted(false);
		}
		plist = new PredatoidSrv.playlist();
		launcher = new Launcher();
		if (nm == null) {
			nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		}
		Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
	}

	@Override
	public void onDestroy() {
		logMessage("onDestroy()");
		// cBacks.kill();
		unregisterPhoneListener();
		unregisterHeadsetReciever();
		if (plist != null && plist.running) {
			plist.stop();
		}

		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		if (nm != null) {
			nm.cancel(NOTIFY_ID);
		}
	}

	@Override
	public boolean onUnbind(Intent intent) {
		logMessage("onUnbind()");
		// if(nm != null) nm.cancel(NOTIFY_ID);
		return super.onUnbind(intent);
	}

	// /////////////////////////////////////////////////////
	// ////////// Headset Connection Detection /////////////
	private BroadcastReceiver headsetReciever = new BroadcastReceiver() {

		private boolean needResume = false;

		@Override
		public void onReceive(Context context, Intent intent) {
			if (isIntentHeadsetRemoved(intent)) {
				logMessage("Headset Removed: " + intent.getAction());
				if (plist != null && plist.running && !plist.paused) {
					if (((headsetMode & HANDLE_HEADSET_REMOVE) != 0)) {
						plist.pause();
						informPauseResume(true);
					}
					needResume = true;
				}
			} else if (isIntentHeadsetInserted(intent)) {
				logMessage("Headset Inserted: " + intent.getAction());
				if (needResume) {
					if (plist != null
							&& (headsetMode & HANDLE_HEADSET_INSERT) != 0) {
						plist.resume();
						informPauseResume(false);
					}
					needResume = false;
				}
			}
		}

		private boolean isIntentHeadsetInserted(Intent intent) {
			return (intent.getAction().equalsIgnoreCase(
					Intent.ACTION_HEADSET_PLUG) && intent.getIntExtra("state",
					0) != 0);
		}

		private boolean isIntentHeadsetRemoved(Intent intent) {
			return ((intent.getAction().equalsIgnoreCase(
					Intent.ACTION_HEADSET_PLUG) && intent.getIntExtra("state",
					0) == 0) || intent.getAction().equalsIgnoreCase(
					AudioManager.ACTION_AUDIO_BECOMING_NOISY));
		}
	};

	private void registerHeadsetReciever() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		registerReceiver(headsetReciever, filter);
	}

	private void unregisterHeadsetReciever() {
		unregisterReceiver(headsetReciever);
	}

	// //////////////////////////////////////////////////////
	// ////// Pause when the phone rings, and resume after the call
	private PhoneStateListener phoneStateListener = new PhoneStateListener() {

		private boolean needResume = false;

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING
					|| state == TelephonyManager.CALL_STATE_OFFHOOK) {
				if (plist != null && plist.running && !plist.paused) {
					plist.pause();
					informPauseResume(true);
					needResume = true;
				}
			} else if (state == TelephonyManager.CALL_STATE_IDLE) {
				if (needResume) {
					if (plist != null) {
						plist.resume();
						informPauseResume(false);
					}
					needResume = false;
				}
			}
		}
	};

	private void registerPhoneListener() {
		TelephonyManager tmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		tmgr.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
	}

	private void unregisterPhoneListener() {
		TelephonyManager tmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		tmgr.listen(phoneStateListener, 0);
	}

	// //////////////////////////////////////////////////
	// //////// We need read-write access to this device
	final private String MSM_DEVICE = "/dev/msm_pcm_out";

	public boolean checkSetDevicePermissions() {
		java.lang.Process process = null;
		DataOutputStream os = null;
		try {
			File f = new File(MSM_DEVICE);
			if (f.canRead() && f.canWrite() /* && f1.canRead() && f1.canWrite() */) {
				logMessage("neednt set device permissions");
				return true;
			}
			logMessage("attempting to set device permissions");
			process = Runtime.getRuntime().exec("su");
			os = new DataOutputStream(process.getOutputStream());
			os.flush();
			os.writeBytes("chmod 0666 " + MSM_DEVICE + "\n");
			os.flush();
			os.writeBytes("exit\n");
			os.flush();
			process.waitFor();
		} catch (Exception e) {
			logErrorMessage("exception while setting device permissions: "
					+ e.toString());
			return false;
		} finally {
			try {
				if (os != null) {
					os.close();
				}
				process.destroy();
			} catch (Exception e) {
			}
		}
		return true;
	}
}
