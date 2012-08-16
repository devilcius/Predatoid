package com.predatum;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

public class RemoteControlReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Log.i("Predatoid.RemoteControlReceiver", "intent: " + intent);
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            IBinder iBinder = this.peekService(context, new Intent(context,
                    PredatoidSrv.class));
            // Log.i("Predatoid.RemoteControlReceiver", "iBinder" + iBinder);
            IPredatoidSrv srv = IPredatoidSrv.Stub.asInterface(iBinder);
            ;
            if (srv == null) {
                Log.e("Predatoid.RemoteControlReceiver", "srv==null");
                return;
            }
            // Log.i("Predatoid.RemoteControlReceiver", "srv" + srv);
            KeyEvent event = (KeyEvent) intent
                    .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            // Log.i("Predatoid.RemoteControlReceiver", "event" + event);

            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN)
                return;
            try {
                switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    srv.pause();
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (srv.is_running() && !srv.is_paused())
                        srv.pause();
                    else
                        srv.resume();
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    srv.play_next();
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    srv.play_prev();
                    break;
                }
            } catch (Exception e) {
            }

            this.abortBroadcast();

        }
    }
}
