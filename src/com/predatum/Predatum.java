package com.predatum;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import org.json.*;
import com.loopj.android.http.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.apache.http.cookie.Cookie;

public class Predatum {

	private static final String PREDATUM_URL = "http://192.168.2.40";
	private static final String PREDATUM_LOGIN_CONTEXT = "/api/login/format/json";
	private static final String PREDATUM_SONG_POST_CONTEXT = "/api/nowplaying/format/json";
	private static Predatum instance = null;

	/** Returns singleton instance of this class */
	public static synchronized Predatum getInstance() {
		if (instance == null) {
			instance = new Predatum();
		}
		return instance;
	}

	public void authenticateToPredatum(final String userName,
			final String userPassword, final Context context) {

		PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(
				context);
		if (!userIsLoggedIn(predatumPersistentCookieStore)) {
			this.login(userName, userPassword, context);
		} else {
			AsyncHttpClient client = new AsyncHttpClient();
			client.setCookieStore(predatumPersistentCookieStore);
			client.post(PREDATUM_URL + PREDATUM_LOGIN_CONTEXT, null,
					new AsyncHttpResponseHandler() {

						@Override
						public void onSuccess(String response) {
							try {
								JSONObject message = new JSONObject(response);
								if (message.has("processed")) {
									Log.i(getClass().getSimpleName(),
											message.getString("processed"));
									Toast toast = Toast.makeText(context,
											"You're logged in predatum",
											Toast.LENGTH_LONG);
									toast.show();
								} else {
									Log.e(getClass().getSimpleName(),
											message.getString("error"));
									Toast toast = Toast.makeText(context,
											message.getString("error"),
											Toast.LENGTH_LONG);
									toast.show();
								}

							} catch (JSONException ex) {
								Log.e(getClass().getSimpleName(),
										ex.getMessage());
							}
						}

					});
		}
	}

	public void postSong(HashMap<String, Object> song, Context context) {

		Iterator iterator = song.entrySet().iterator();
		RequestParams params = new RequestParams();

		while (iterator.hasNext()) {
			HashMap.Entry pairs = (HashMap.Entry) iterator.next();
			params.put(pairs.getKey().toString(), pairs.getValue().toString());
			iterator.remove(); // avoids a ConcurrentModificationException
		}

		predatumPost(params, PREDATUM_SONG_POST_CONTEXT, context);

	}

	private boolean userIsLoggedIn(PersistentCookieStore cookieStore) {

		List<Cookie> predatumCookies = cookieStore
				.getCookies();
		return predatumCookies.size() >= 1;
	}

	private void predatumPost(RequestParams params, String controller,
			Context context) {
		final AsyncHttpClient client = new AsyncHttpClient();
		PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(
				context);		
		if (!userIsLoggedIn(predatumPersistentCookieStore)) {
			predatumPersistentCookieStore.clear();		
		}
		client.setCookieStore(predatumPersistentCookieStore);	
		// TODO: set user agent
		client.setUserAgent(context.getPackageName());
		client.post(PREDATUM_URL + controller, params,
				new AsyncHttpResponseHandler() {

					@Override
					public void onSuccess(String response) {
						try {
							// Pull out the first event on the public timeline
							JSONObject message = new JSONObject(response);
							if (message.has("processed")) {
								Log.d(getClass().getSimpleName(),
										message.getString("processed"));
							} else {
								Log.e("bah", message.getString("error"));
							}

						} catch (JSONException jsonException) {
							Log.e("Error while creating json object from http response",
									jsonException.getMessage());
						}
					}

					@Override
					public void onFailure(Throwable error, String content) {
						Log.e("Error while posting to predatum",
								content);						
						for (StackTraceElement ste : error.getStackTrace()) {
						    Log.e(this.getClass().getName(), ste.toString());
						}
					}
				});
	}

	private void login(String username, String password, final Context context) {

		RequestParams params = new RequestParams();
		params.put("login", username);
		params.put("password", password);
		params.put("remember", "1");

		predatumPost(params, PREDATUM_LOGIN_CONTEXT, context);
		Log.d(context.getClass().getSimpleName(), "you're logged in predatum");
		Toast toast = Toast.makeText(context, "You're now logged in predatum",
				Toast.LENGTH_LONG);
		toast.show();
	}
}
