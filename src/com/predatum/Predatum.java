package com.predatum;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import org.json.*;
import com.loopj.android.http.*;
import java.util.List;
import org.apache.http.cookie.Cookie;


public class Predatum {

    private static final String PREDATUM_URL = "http://192.168.2.40";
    private static final String PREDATUM_LOGIN_CONTEXT = "/api/login/format/json";
    private static Predatum instance = null;

    /** Returns singleton instance of this class */
    public static synchronized Predatum getInstance() {
        if (instance == null) {
            instance = new Predatum();
        }
        return instance;
    }

    public void authenticateToPredatum(final String userName, final String userPassword, final Context context) {

        PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(context);
        List<Cookie> predatumCookies = predatumPersistentCookieStore.getCookies();
        if (predatumCookies.size() < 1) {
            Predatum.login(userName, userPassword, context);
        } else {
            AsyncHttpClient client = new AsyncHttpClient();
            client.setCookieStore(predatumPersistentCookieStore);
            client.post(PREDATUM_URL + PREDATUM_LOGIN_CONTEXT, null, new AsyncHttpResponseHandler() {

                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject message = new JSONObject(response);
                        if (message.has("ok")) {
                            Log.i(getClass().getSimpleName(), message.getString("ok"));
                            Toast toast = Toast.makeText(context, "You're logged in predatum", Toast.LENGTH_LONG);
                            toast.show();
                        } else {
                            Predatum.login(userName, userPassword, context);
                        }
                        
                    } catch (JSONException ex) {
                        Log.e(getClass().getSimpleName(), ex.getMessage());
                    }
                }

            });
        }
    }

    private static void login(String username, String password, final Context context) {

        RequestParams params = new RequestParams();
        params.put("login", username);
        params.put("password", password);
        params.put("remember", "1");

        final AsyncHttpClient client = new AsyncHttpClient();
        PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(context);
        predatumPersistentCookieStore.clear();
        client.setCookieStore(predatumPersistentCookieStore);
        //TODO: set user agent
        client.setUserAgent(context.getPackageName());
        client.post(PREDATUM_URL + PREDATUM_LOGIN_CONTEXT, params, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(String response) {
                try {
                    // Pull out the first event on the public timeline
                    JSONObject message = new JSONObject(response);
                    if (message.has("ok")) {
                        Log.d(getClass().getSimpleName(), message.getString("ok"));
                        Toast toast = Toast.makeText(context, "You're logged in predatum", Toast.LENGTH_LONG);
                        toast.show();
                    } else {
                        Log.e("bah", message.getString("error"));
                    }

                } catch (JSONException jsonException) {
                    Log.e("Error while creating json object from http response", jsonException.getMessage());
                }
            }
        });
    }
}
