package com.predatum;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.predatum.iconifiedlist.IconifiedText;
import com.predatum.iconifiedlist.IconifiedTextListAdapter;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Predatoid extends Activity {

    // Currently playing album id
    private int currDisplayedAlbumID = 0;
    // Currently "opened" album id
    private int currPlayingAlbumID = 0;
    // File/directory names together with their icons displayed in the list widget
    private ArrayList<IconifiedText> albumEntries = new ArrayList<IconifiedText>();
    // List of tracks
    private ArrayList<IconifiedText> trackEntries = new ArrayList<IconifiedText>();
    // Full paths to files in current dir/playlist/cue
    private ArrayList<String> files = new ArrayList<String>();
    // Full paths to files in current playlist
    private ArrayList<String> filesToPlay = new ArrayList<String>();
    // Track names to be displayed in status bar
    private ArrayList<String> songNames = new ArrayList<String>();
    // At the start, set this flag and emulate the pause if the last file was bookmarked
    //currentTrack playing or paused
    private int currentTrack = 0;
    private boolean pauseOnStart = false;
    //Phone's back button go back to previous item list
    private boolean canGoBack = false;

    private void logMessage(String msg) {
        Log.i(getClass().getSimpleName(), msg);
    }

    private void log_err(String msg) {
        Log.e(getClass().getSimpleName(), msg);
    }
    // UI elements defined in layout XML file.
    private Button buttPause, buttPrev, buttNext, buttVMinus, buttVPlus, ButtonVolume;
    private TextView nowTime, allTime;
    private ListView fileList;
    private SeekBar progressBar;
    private String curWindowTitle = null;
    private static final String resume_bmark = "/resume.bmark";
    // Interface which is an entry point to server functions. Returned upon connection to the server.
    private IPredatoidSrv srv = null;
    // If we're called through intent
    private String startfile = null;
    // Callback for server to report track/state changes.  Invokes the above handler to set window title.
    private IPredatoidSrvCallback cBack = new IPredatoidSrvCallback.Stub() {

        public void playItemChanged(boolean error, String name, final int trackNum) {
            logMessage(String.format("track name changed to %s", name));
            Message msg = new Message();
            Bundle data = new Bundle();
            data.putString("filename", name);
            data.putBoolean("error", error);
            msg.setData(data);
            hdl.sendMessage(msg);
            if (fileList.getChildCount() > 0) {
                currentTrack = trackNum;
                new SendSrvCmd().execute(SendSrvCmd.cmd_hilight_item);
            }
        }

        public void errorReported(String name) {
            logMessage(String.format("error \"%s\" received", name));
            Message msg = new Message();
            Bundle data = new Bundle();
            data.putString("errormsg", name);
            msg.setData(data);
            hdl.sendMessage(msg);
        }

        public void playItemPaused(boolean paused) {
            pauseResumeHandler.sendEmptyMessage(paused ? 1 : 0);
        }
    };
    IBinder.DeathRecipient bdeath = new IBinder.DeathRecipient() {

        public void binderDied() {
            log_err("Binder died, trying to reconnect");
            conn = newConnection();
            Intent intie = new Intent();
            intie.setClassName("com.predatum", "com.predatum.PredatoidSrv");
            if (!stopService(intie)) {
                log_err("service not stopped");
            }
            if (startService(intie) == null) {
                logMessage("service not started");
            } else {
                logMessage("started service");
            }
            if (!bindService(intie, conn, 0)) {
                log_err("cannot bind service");
            } else {
                logMessage("service bound");
            }
        }
    };
    // On connection, obtain the service interface and setup screen according to current server state
    private ServiceConnection conn = null;

    ServiceConnection newConnection() {
        return new ServiceConnection() {

            public void onServiceConnected(ComponentName cn, IBinder obj) {
                logMessage("#### SERVICE CONNECTED");
                srv = IPredatoidSrv.Stub.asInterface(obj);
                if (srv == null) {
                    log_err("failed to get service interface");
                    errExit(R.string.strErrSrvIf);
                    return;
                }
                try {

                    obj.linkToDeath(bdeath, 0);

                    logMessage("#### ADAPTER SET");
                    fileList.setSelection(0);
                    boolean lastPlayedTrackInfoIsComplete = (prefs.lastPlayedFile != null && (new File(prefs.lastPlayedFile)).exists()
                            && prefs.lastPlayedAlbumID != 0);
                    if (prefs.resumeLastTrackPlayed && lastPlayedTrackInfoIsComplete && !srv.isRunning()) {
                        currDisplayedAlbumID = currPlayingAlbumID = prefs.lastPlayedAlbumID;

                        if (!setAdapterFromAlbum()) {
                            log_err("cannot set adapter from album!!");
                            return;
                        }

                        logMessage("resume last played");

                        canGoBack = true;
                        currentTrack = prefs.lastPlayedPosition;
                        pauseOnStart = false;
                        playContents(prefs.lastPlayedFile, filesToPlay, songNames, prefs.lastPlayedPosition, prefs.lastPlayedTime);
                        TrackTimeUpdater ttu = new TrackTimeUpdater();
                        ttu.start(songNames.get(currentTrack));
                        new SendSrvCmd().execute(SendSrvCmd.cmd_hilight_item);

                    } else {
                        if (!setAdapterFromMedia()) {
                            log_err("cannot set adapter from media!!!");
                        }
                        cBack.playItemChanged(true, getString(R.string.strStopped), 0);
                    }

                    srv.registerCallback(cBack);
                    update_headset_mode(null);
                } catch (RemoteException e) {
                    logMessage("remote exception in onServiceConnected: " + e.toString());
                }
                //	Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            }

            public void onServiceDisconnected(ComponentName cn) {
                srv = null;
            }
        };
    }

    // Helper class to improve interface responsiveness. When the user clicks a button (play, pause, etc), it executes
    // the corresponding server command in background, and setups the UI upon its completion.
    private class SendSrvCmd extends AsyncTask<Integer, Void, Integer> {

        public static final int cmd_pause = 1, cmd_prev = 2, cmd_next = 3, cmd_hilight_item = 4;
        private final int dont_change_btn = 0, change_to_pause_btn = 1, change_to_play_btn = 2;
        private String nowPlaying = null;

        protected Integer doInBackground(Integer... func) {
            try {

                switch (func[0]) {
                    case cmd_pause:
                        if (pauseOnStart) {
                            return change_to_pause_btn;
                        }
                        if (srv.isPaused()) {
                            if (srv.resume() && srv.isRunning()) {
                                nowPlaying = curWindowTitle;
                                if (nowPlaying != null) {
                                    return change_to_pause_btn;
                                }
                                nowPlaying = srv.getCurrentTrackName();
                                if (nowPlaying != null) {
                                    return change_to_pause_btn;
                                }
                                nowPlaying = srv.getCurrentTrackSource();
                                if (nowPlaying != null) {
                                    int i = nowPlaying.lastIndexOf('/');
                                    if (i >= 0) {
                                        nowPlaying = nowPlaying.substring(i + 1);
                                    }
                                }
                                return change_to_pause_btn;
                            }
                        } else if (srv.pause()) {
                            return change_to_play_btn;
                        }
                        break;
                    case cmd_prev:
                        srv.playPrevious();
                        break;
                    case cmd_next:
                        srv.playNext();
                        break;
                    case cmd_hilight_item:
                        runOnUiThread(new Runnable() {

                            public void run() {
                                for (int i = 0; i < fileList.getChildCount(); i++) {
                                    fileList.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                                }
                                if (currDisplayedAlbumID != currPlayingAlbumID) {
                                    return;
                                }
                                if (fileList.getChildCount() > 0 && currentTrack < fileList.getChildCount()) {
                                    fileList.getChildAt(currentTrack).setBackgroundColor(Color.GREEN);
                                }
                            }
                        });
                }
            } catch (Exception e) {
                log_err("exception in SendSrvCmd (" + func[0] + "): " + e.toString());
                if (srv == null || conn == null) {
                    conn = newConnection();
                }
            }
            return dont_change_btn;
        }

        protected void onPostExecute(Integer result) {
            switch (result) {
                case change_to_pause_btn:
                    if (pauseOnStart) {
                        selItem.onItemClick(null, null, 0, 0); // dirty hack
                    }
                    if (nowPlaying != null) {
                        getWindow().setTitle(nowPlaying);
                    }
                    buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_pause));
                    break;
                case change_to_play_btn:
                    getWindow().setTitle(getString(R.string.strPaused));
                    buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_play));
                    break;
            }
            pauseOnStart = false;
        }
    }
    View.OnClickListener onButtPause = new OnClickListener() {

        public void onClick(View v) {

            new SendSrvCmd().execute(SendSrvCmd.cmd_pause);
        }
    };
    View.OnClickListener onButtPrev = new OnClickListener() {

        public void onClick(View v) {
            new SendSrvCmd().execute(SendSrvCmd.cmd_prev);
        }
    };
    View.OnClickListener onButtNext = new OnClickListener() {

        public void onClick(View v) {
            new SendSrvCmd().execute(SendSrvCmd.cmd_next);
        }
    };

    private void ExitFromProgram() {
        try {
            if (srv != null) {
                if (srv.isRunning()) {
                    saveBook();
                }
                srv.shutdown();
            }
            prefs.save();
            if (conn != null) {
                logMessage("unbinding service");
                unbindService(conn);
                conn = null;
            }
        } catch (Exception e) {
            log_err("exception while shutting down");
        }
        Intent intie = new Intent();
        intie.setClassName("com.predatum", "com.predatum.PredatoidSrv");
        if (!stopService(intie)) {
            log_err("service not stopped");
        } else {
            logMessage("service stopped");
        }
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    View.OnClickListener onButtUp = new OnClickListener() {

        public void onClick(View v) {
            if (currDisplayedAlbumID == 0) {
                return;
            }
            v.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(), R.anim.blink));
        }
    };
    OnSeekBarChangeListener onSeekBar = new OnSeekBarChangeListener() {

        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            String sTime = (progress < 3600) ? String.format("%d:%02d", progress / 60, progress % 60)
                    : String.format("%d:%02d:%02d", progress / 3600, (progress % 3600) / 60, progress % 60);
            nowTime.setText(sTime);
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
            try {

                srv.seekTo(seekBar.getProgress() * 1000);

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };

    private void onButtUp() {
        if (currDisplayedAlbumID == 0) {
            return;
        }
    }

    // Load the server playlist with contents of arrays, and play starting from the k-th item @ time start.
    private boolean playContents(String fpath, ArrayList<String> audioFiles, ArrayList<String> songs,
            int trackNum, int startPos) {
        try {
            if (!srv.initPlaylist(fpath, audioFiles.size())) {
                log_err("failed to initialize new playlist on server");
                Toast.makeText(getApplicationContext(), R.string.strSrvFail, Toast.LENGTH_SHORT).show();
                return false;
            }
            for (int i = 0; i < audioFiles.size(); i++) {
                if (!srv.addToPlaylist(audioFiles.get(i), (songs != null) ? songs.get(i) : null, 0, i)) {
                    log_err("failed to add a file to server playlist");
                    Toast.makeText(getApplicationContext(), R.string.strSrvFail, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            srv.setLoopPlaying(prefs.loopMode);
            if (!srv.play(trackNum, startPos)) {
                Toast.makeText(getApplicationContext(), R.string.strSrvFail, Toast.LENGTH_SHORT).show();
                log_err("failed to start playing <contents>");
                return false;
            }
            if (!pauseOnStart) {
                buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_pause));
            }
            return true;
        } catch (Exception e) {
            log_err("exception in playContents: " + e.toString());
            e.printStackTrace();
            return false;
        }

    }
    // Save the last played file in format "file:pos:time"
    // Also saves this info to preferences.

    void saveBook() {
        try {
            File book_file;
            String s = srv.getCurrentDirectory();


            book_file = new File(s + resume_bmark);
            s = srv.getCurrentTrackSource();

            prefs.lastPlayedFile = new String(s);
            int seconds = srv.getCurrentSeconds() - srv.getCurrentTrackStart();
            int index = srv.getCurrentPosition();
            prefs.lastPlayedPosition = index;
            prefs.lastPlayedTime = seconds;
            if (!prefs.savebooks) {
                return;
            }
            if (book_file.exists()) {
                book_file.delete();
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(book_file, false), 8192);
            String g = s + String.format(":%d:%d", seconds, index);
            writer.write(g);
            writer.close();
            logMessage("Saving bookmark: " + book_file.toString() + ": " + g);
        } catch (Exception e) {
            log_err("exception in saveBook: " + e.toString());
        }
    }
    ////////////  Change to the selected directory/cue/playlist, or play starting from the selected track
    AdapterView.OnItemClickListener selItem = new OnItemClickListener() {

        public void onItemClick(AdapterView<?> a, View v, int i, long k) {


            IconifiedText iconifiedText = (IconifiedText) a.getAdapter().getItem(i);


            if (iconifiedText.hasChildren()) {
                currDisplayedAlbumID = iconifiedText.getAlbumID();
                setAdapterFromAlbum();
                try {
                    if (!srv.isRunning()) {
                        playContents(startfile, filesToPlay, songNames, 0, 0);
                        currentTrack = 0;
                        currPlayingAlbumID = iconifiedText.getAlbumID();
                    }
                } catch (RemoteException ex) {
                    Logger.getLogger(Predatoid.class.getName()).log(Level.SEVERE, null, ex);
                }
                canGoBack = true;
            } else {
                playContents(startfile, filesToPlay, songNames, i, 0);
                currentTrack = i;
                currPlayingAlbumID = iconifiedText.getAlbumID();
            }

            new SendSrvCmd().execute(SendSrvCmd.cmd_hilight_item);
            pauseOnStart = false;

        }
    };
    AdapterView.OnItemLongClickListener pressItem = new OnItemLongClickListener() {

        public boolean onItemLongClick(AdapterView<?> a, View v, int i, long k) {
            if (i == 0) {
                onButtUp();
                return false;
            }
            k = k - 1;
            if ((int) k >= files.size()) {
                log_err("long-pressed item out of range!");
                return false;
            }
            File f = new File(files.get((int) k));
            if (!f.exists()) {
                log_err("non-existing item long-pressed in the list!");
                return false;
            }

            log_err("unknown item long-pressed!");
            return false;
        }
    };

    ////////////////////////////////////////////////////////////////
    ////////////////////////////// Handlers ////////////////////////
    ////////////////////////////////////////////////////////////////
    private class TrackTimeUpdater {

        private String track_name;
        private boolean init_completed = false;
        private boolean need_update = false;
        private Timer timer;
        private UpdaterTask timer_task;
        private Handler progressUpdate = new Handler();
        private final int first_delay = 500;
        private final int update_period = 500;

        private class UpdaterTask extends TimerTask {

            public void run() {
                if (track_name == null) {
                    shutdown();
                    return;
                }
                if (init_completed) {
                    progressUpdate.post(new Runnable() {

                        public void run() {
                            if (srv == null) {
                                return;
                            }
                            if (!progressBar.isPressed()) {
                                try {
                                    if (!srv.isRunning() || srv.isPaused()) {
                                        return;
                                    }
                                    if (need_update) {		// track_time was unknown at init time
                                        //	int track_time = PredatoidSrv.currentTrackLength;
                                        int track_time = srv.getCurrentTrackLength();
                                        if (track_time <= 0) {
                                            track_time = srv.getTrackDuration();
                                            if (track_time <= 0) {
                                                return;
                                            }
                                        }
                                        curWindowTitle = (track_time < 3600) ? String.format("[%d:%02d] %s", track_time / 60, track_time % 60, track_name)
                                                : String.format("[%d:%02d:%02d] %s", track_time / 3600, (track_time % 3600) / 60, track_time % 60, track_name);
                                        getWindow().setTitle(curWindowTitle);
                                        progressBar.setMax(track_time);
                                        String sTime = (track_time < 3600) ? String.format("%d:%02d", track_time / 60, track_time % 60)
                                                : String.format("%d:%02d:%02d", track_time / 3600, (track_time % 3600) / 60, track_time % 60);
                                        allTime.setText(sTime);
                                        need_update = false;
                                    }
                                    //	pBar.setProgress(srv.getCurrentSeconds() - PredatoidSrv.currentTrackStart);
                                    int progress = srv.getCurrentSeconds() - srv.getCurrentTrackStart();
                                    if (progress > 0) {
                                        progressBar.setProgress(progress);
                                    }
                                    String sTime = (srv.getCurrentSeconds() < 3600) ? String.format("%d:%02d", progress / 60, progress % 60)
                                            : String.format("%d:%02d:%02d", progress / 3600, (progress % 3600) / 60, progress % 60);
                                    nowTime.setText(sTime);
                                } catch (Exception e) {
                                    log_err("exception 1 in progress update handler: " + e.toString());
                                }
                            }
                        }
                    });
                    return;
                }
                progressUpdate.post(new Runnable() {	// initialize

                    public void run() {
                        if (srv == null) {
                            return;
                        }
                        if (!progressBar.isPressed()) {
                            try {
                                //	int track_time = PredatoidSrv.currentTrackLength;
                                int track_time = srv.getCurrentTrackLength();
                                need_update = false;
                                if (track_time <= 0) {
                                    logMessage("progressUpdate(): fishy track_time " + track_time);
                                    track_time = srv.getTrackDuration();
                                    if (track_time <= 0) {
                                        need_update = true;
                                    }
                                }
                                curWindowTitle = (track_time < 3600) ? String.format("[%d:%02d] %s", track_time / 60, track_time % 60, track_name)
                                        : String.format("[%d:%02d:%02d] %s", track_time / 3600, (track_time % 3600) / 60, track_time % 60, track_name);

                                getWindow().setTitle(curWindowTitle);
                                progressBar.setMax(track_time);
                                String sTime = (track_time < 3600) ? String.format("%d:%02d", track_time / 60, track_time % 60)
                                        : String.format("%d:%02d:%02d", track_time / 3600, (track_time % 3600) / 60, track_time % 60);
                                allTime.setText(sTime);
                            } catch (Exception e) {
                                log_err("exception 2 in progress update handler: " + e.toString());
                            }
                        }
                    }
                });
                init_completed = true;
            }
        }

        public void shutdown() {
            if (timer_task != null) {
                timer_task.cancel();
            }
            if (timer != null) {
                timer.cancel();
            }
            timer = null;
            timer_task = null;
            track_name = null;
            init_completed = false;
        }

        private void reset() {
            shutdown();
            timer_task = new UpdaterTask();
            timer = new Timer();
        }

        public void start(String s) {
            reset();
            track_name = new String(s);
            timer.schedule(timer_task, first_delay, update_period);
        }
    }
    TrackTimeUpdater ttu = new TrackTimeUpdater();
    Handler hdl = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle bb = msg.getData();
            if (bb == null) {
                return;
            }
            String curfile = bb.getString("filename");
            if (curfile != null) {
                boolean error = bb.getBoolean("error");
                if (!error) {
                    // normal track, need to setup track time/progress update stuff

                    ttu.start(curfile);
                    if (buttPause != null) {
                        buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_pause));
                    }
                    return;
                } else {
                    //	  if(pBar != null) pBar.setProgress(0);
                    getWindow().setTitle(curfile);
                    ttu.shutdown();
                    if (buttPause != null) {
                        buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_play));
                    }
                    return;
                }
            }
            ttu.shutdown();

            // if(pBar != null) pBar.setProgress(0);
            curfile = bb.getString("errormsg");
            if (curfile == null) {
                return;
            }
            showMsg(curfile);
        }
    };
    @SuppressLint("HandlerLeak")
	Handler pauseResumeHandler = new Handler() {

        private String nowPlaying = null;

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_pause));
                    if (nowPlaying != null) {
                        getWindow().setTitle(nowPlaying);
                    }
                    break;
                case 1:
                    nowPlaying = curWindowTitle;
                    buttPause.setBackgroundDrawable(getResources().getDrawable(R.drawable.s_play));
                    getWindow().setTitle(getString(R.string.strPaused));
                    break;
            }
        }
    };

    ////////////////////////////////////////////////////////////////
    ///////////////////////// Entry point //////////////////////////
    ////////////////////////////////////////////////////////////////
    @Override
    protected void onResume() {
        super.onResume();

        // getting settings
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean resumeLastTrackPlayed = settings.getBoolean("resume_last_track_played", false);
        boolean loopMode = settings.getBoolean("loop_mode", false);
        boolean bookMode = settings.getBoolean("book_mode", false);
        boolean loginToPredatum = settings.getBoolean("login_to_predatum", false);
        String loginUsername = settings.getString("login_username", null);
        String loginPassword = settings.getString("login_password", null);

        if (bookMode) {
            prefs.savebooks = true;
        } else {
            prefs.savebooks = false;
        }
        if (loginToPredatum) {
            prefs.loginToPredatum = true;
        } else {
            prefs.loginToPredatum = false;
        }

        if (resumeLastTrackPlayed) {
            prefs.resumeLastTrackPlayed = true;
        } else {
            prefs.resumeLastTrackPlayed = false;
        }
        if (loopMode) {
            prefs.loopMode = true;
        } else {
            prefs.loopMode = false;
        }
        prefs.loginUserName = loginUsername;
        prefs.loginPassword = loginPassword;

        update_headset_mode(settings);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //back button pressed
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (canGoBack) {
                setAdapterFromMedia();
                canGoBack = false;
                return false;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    void update_headset_mode(SharedPreferences settings) {
        if (settings == null) {
            settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        }
        prefs.headsetMode = 0;
        if (settings.getBoolean("hs_remove_mode", false)) {
            prefs.headsetMode |= PredatoidSrv.HANDLE_HEADSET_REMOVE;
        }
        if (settings.getBoolean("hs_insert_mode", false)) {
            prefs.headsetMode |= PredatoidSrv.HANDLE_HEADSET_INSERT;
        }
        if (srv != null) {
            try {
                srv.setHeadsetMode(prefs.headsetMode);
            } catch (RemoteException r) {
                log_err("remote exception while trying to set headset_mode");
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        Intent ii = getIntent();
        prefs = new Prefs();
        prefs.load();

        // ui preferences
        setTheme(android.R.style.Theme_Light);
        setContentView(R.layout.main);
        setContent();
        fileList.setBackgroundResource(android.R.color.background_light);
        buttPause.setEnabled(true);

        //predatum login
        if (prefs.loginToPredatum) {
            Predatum.getInstance().authenticateToPredatum(prefs.loginUserName, prefs.loginPassword, this);
        }


        Intent intie = new Intent();
        intie.setClassName("com.predatum", "com.predatum.PredatoidSrv");

        if (startService(intie) == null) {
            logMessage("service not started");
        } else {
            logMessage("started service");
        }

        if (conn == null) {
            conn = newConnection();
        }

        if (ii.getAction().equals(Intent.ACTION_VIEW) || ii.getAction().equals(PredatoidSrv.ACTION_VIEW)) {
            try {
                startfile = Uri.decode(ii.getDataString());
                if (startfile != null && startfile.startsWith("file:///")) {
                    startfile = startfile.substring(7);
                } else {
                    startfile = null;
                }
            } catch (Exception s) {
                startfile = null;
            }
        } else {
            startfile = null;
        }

        if (!getApplicationContext().bindService(intie, conn, 0)) {
            log_err("cannot bind service");
        } else {
            logMessage("service bound");
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        prefs.save();
        if (srv != null) {
            try {
                srv.unregisterCallback(cBack);

            } catch (RemoteException e) {
                log_err("remote exception in onDestroy(): " + e.toString());
            }
        }
        if (conn != null) {
            logMessage("unbinding service");
            getApplicationContext().unbindService(conn);
            conn = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    // Save/restore user preferences.
    class Prefs {

        public static final String PREFS_NAME = "predatoid_prefs";
        public String lastPath;
        public String plistPath;
        public String plistName;
        public String lastPlayedFile;
        public String loginUserName;
        public String loginPassword;
        public boolean resumeLastTrackPlayed;
        public boolean loopMode;
        public boolean savebooks;
        public boolean loginToPredatum;
        public int headsetMode;
        public int lastPlayedPosition;
        public int lastPlayedTime;
        public int lastPlayedAlbumID;

        public void load() {
            SharedPreferences shpr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            resumeLastTrackPlayed = shpr.getBoolean("resume_last_track_played", false);
            savebooks = shpr.getBoolean("save_books", false);
            loginToPredatum = shpr.getBoolean("login_to_predatum", false);
            loopMode = shpr.getBoolean("loop_mode", false);
            lastPath = shpr.getString("last_path", null);
            lastPlayedFile = shpr.getString("last_played_file", null);
            lastPlayedPosition = shpr.getInt("last_played_pos", 0);
            lastPlayedTime = shpr.getInt("last_played_time", 0);
            lastPlayedAlbumID = shpr.getInt("last_played_album_id", 0);
            plistPath = shpr.getString("plist_path", Environment.getExternalStorageDirectory().toString());
            plistName = shpr.getString("plist_name", "Favorites");
            loginUserName = shpr.getString("login_username", null);
            loginPassword = shpr.getString("login_password", null);

            headsetMode = 0;
            if (shpr.getBoolean("hs_remove_mode", false)) {
                headsetMode |= PredatoidSrv.HANDLE_HEADSET_REMOVE;
            }
            if (shpr.getBoolean("hs_insert_mode", false)) {
                headsetMode |= PredatoidSrv.HANDLE_HEADSET_INSERT;
            }
        }

        public void save() {
            SharedPreferences shpr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = shpr.edit();
            editor.putBoolean("resume_last_track_played", resumeLastTrackPlayed);
            editor.putBoolean("save_books", savebooks);
            editor.putBoolean("loop_mode", loopMode);
            editor.putBoolean("hs_remove_mode", (headsetMode & PredatoidSrv.HANDLE_HEADSET_REMOVE) != 0);
            editor.putBoolean("hs_insert_mode", (headsetMode & PredatoidSrv.HANDLE_HEADSET_INSERT) != 0);
            editor.putBoolean("login_to_predatum", loginToPredatum);
            editor.putString("login_username", loginUserName);
            editor.putString("login_password", loginPassword);
            if (currDisplayedAlbumID != 0) {
//                editor.putString("lastPath", cur_album_id.toString());
            }
            if (plistPath != null) {
                editor.putString("plist_path", plistPath);
            }
            if (plistName != null) {
                editor.putString("plist_name", plistName);
            }
            if (lastPlayedFile != null) {
                editor.putString("last_played_file", lastPlayedFile);
                editor.putInt("last_played_pos", lastPlayedPosition);
                editor.putInt("last_played_time", lastPlayedTime);
                editor.putInt("last_played_album_id", currPlayingAlbumID);
            }
            if (!editor.commit()) {
                showMsg(getString(R.string.strErrPrefs));
            }

        }
    }
    public static Prefs prefs;

    ////////////////////////////////////////////////////////////////
    ///////////////////// Menus and dialogs ////////////////////////
    ////////////////////////////////////////////////////////////////
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mm, menu);
        return true;

    }

    private void setContent() {
        setRequestedOrientation(1);
        buttPause = (Button) findViewById(R.id.ButtonPause);
        buttPrev = (Button) findViewById(R.id.ButtonPrev);
        buttNext = (Button) findViewById(R.id.ButtonNext);

        ButtonVolume = (Button) findViewById(R.id.ButtonVolume);
        fileList = (ListView) findViewById(R.id.FileList);
        nowTime = (TextView) findViewById(R.id.nowTime);
        allTime = (TextView) findViewById(R.id.allTime);
        progressBar = (SeekBar) findViewById(R.id.PBar);
        progressBar.setOnSeekBarChangeListener(onSeekBar);
        buttPause.setOnClickListener(onButtPause);
        buttPrev.setOnClickListener(onButtPrev);
        buttNext.setOnClickListener(onButtNext);
        fileList.setOnItemClickListener(selItem);
        fileList.setOnItemLongClickListener(pressItem);

        try {
            if (srv != null && srv.isRunning()) //pBar.setMax(PredatoidSrv.currentTrackLength);
            {
                progressBar.setMax(srv.getCurrentTrackLength());

            }
        } catch (RemoteException e) {
            log_err("remote exception in setContent");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.Setup:
                //showDialog(SETTINGS_DLG);
                Intent i = new Intent(this, Preferences.class);
                startActivity(i);
                return true;
            case R.id.Quit:
                ExitFromProgram();
                return true;
        }
        return false;
    }

    public void errExit(String errMsg) {
        if (errMsg != null) {
            showMsg(errMsg);
        }
        prefs.save();
        new AlertDialog.Builder(this).setMessage(errMsg).setCancelable(false).setPositiveButton("OK",
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                }).show();
    }

    public void showMsg(String errMsg) {
        new AlertDialog.Builder(this).setMessage(errMsg).setCancelable(false).setPositiveButton("OK",
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int id) {
                    }
                }).show();
    }

    public void errExit(int resource) {
        errExit(getString(resource));
    }

    private boolean setAdapterFromMedia() {
        try {

            ArrayList<HashMap<String, Object>> sdcardMusic = new ArrayList<HashMap<String, Object>>();
            ContentResolver resolver = getBaseContext().getContentResolver();
            Cursor cursor = resolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[]{"ALBUM", "ARTIST", "NUMSONGS", "_id", "MINYEAR"}, null, null, "ARTIST");

            if (cursor == null) {
                Log.e(getClass().getSimpleName(), "no albums found!");
                Toast.makeText(getApplicationContext(), R.string.strNoFiles, Toast.LENGTH_SHORT).show();
            } else {
                while (cursor.moveToNext()) {

                    HashMap<String, Object> album = new HashMap<String, Object>();

                    album.put("album", cursor.getString(0));
                    album.put("artist", cursor.getString(1));
                    album.put("numsongs", cursor.getString(2));
                    album.put("album_id", cursor.getString(3));
                    album.put("year", cursor.getString(4));


                    sdcardMusic.add(album);

                }
            }
            cursor.close();

            albumEntries.clear();

            Drawable dir_icon = getResources().getDrawable(R.drawable.folder);

            for (int i = 0; i < sdcardMusic.size(); i++) {

                albumEntries.add(new IconifiedText(
                        sdcardMusic.get(i).get("album").toString() + " " + sdcardMusic.get(i).get("year") + " (" + sdcardMusic.get(i).get("numsongs").toString() + " songs)",
                        sdcardMusic.get(i).get("artist").toString(),
                        dir_icon, Integer.parseInt(sdcardMusic.get(i).get("album_id").toString()), true));
            }

            IconifiedTextListAdapter ita = new IconifiedTextListAdapter(this);
            ita.setListItems(albumEntries);
            fileList.setAdapter(ita);
            return true;

        } catch (Exception e) {
            log_err("Exception in setAdapterFromMedia(): " + e.toString());
            return false;
        }

    }

    private boolean setAdapterFromAlbum() {
        try {

            ArrayList<HashMap<String, Object>> trackList = new ArrayList<HashMap<String, Object>>();
            ContentResolver resolver = getBaseContext().getContentResolver();
            Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{"ALBUM", "ARTIST", "YEAR", "TITLE", "TRACK", "_display_name", "_data", "_size", "duration"}, "album_id=" + currDisplayedAlbumID, null, "TRACK");
            int trackNum = 1;
            filesToPlay.clear();
            songNames.clear();
            while (cursor.moveToNext()) {

                HashMap<String, Object> song = new HashMap<String, Object>();

                song.put("album", cursor.getString(0));
                song.put("artist", cursor.getString(1));
                song.put("year", cursor.getInt(2));
                song.put("title", cursor.getString(3));
                song.put("track", trackNum);
                song.put("filename", cursor.getString(5));
                song.put("file_path", cursor.getString(6));
                song.put("file_size", cursor.getString(7));
                song.put("duration", (Integer.parseInt(cursor.getString(8))) / 1000);                
                SongExtraInfo songExtraInfo = new SongExtraInfo(new File(cursor.getString(6)));
                song.put("genre", songExtraInfo.getSongGenre());
                song.put("lame_encoded", songExtraInfo.isLameEncoded());
                song.put("quality", songExtraInfo.getLamePreset());
                song.put("bitrate", songExtraInfo.getBitrate());
                
                trackList.add(song);

                //fills list of files path of current playlist
                filesToPlay.add(cursor.getString(6));
                //fills list of track names to be shown in the status bar
                songNames.add(cursor.getString(3) + " from " + cursor.getString(1));

                trackNum++;


            }

            cursor.close();
            trackEntries.clear();

            Drawable songIcon = getResources().getDrawable(R.drawable.audio);

            for (int i = 0; i < trackList.size(); i++) {
                trackEntries.add(new IconifiedText(
                        trackList.get(i).get("track").toString() + ". "
                        + trackList.get(i).get("artist") + " - "
                        + trackList.get(i).get("title"),
                        songDurationFormat(Long.parseLong(trackList.get(i).get("duration").toString()))
                        + " :: " + trackList.get(i).get("genre"),
                        songIcon, currDisplayedAlbumID, false));
            }

            IconifiedTextListAdapter ita = new IconifiedTextListAdapter(this);
            ita.setListItems(trackEntries);
            fileList.setAdapter(ita);
            return true;

        } catch (Exception e) {
            Log.e(this.getClass().getName(), "exception in setAdapterFromAlbum(): " + e.toString());
            return false;
        }
    }

    private String songDurationFormat(Long songDuration) {
        return String.format("%d:%02d ",
        		(int) ((songDuration / (1000*60)) % 60),
                TimeUnit.MILLISECONDS.toSeconds(songDuration)
                - (60 * (int) ((songDuration / (1000*60)) % 60)));

    }
}
