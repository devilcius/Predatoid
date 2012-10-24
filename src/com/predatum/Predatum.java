package com.predatum;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import org.json.*;
import com.loopj.android.http.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;

public class Predatum {

	private static final String PREDATUM_URL = "http://192.168.2.40";
	private static final String PREDATUM_LOGIN_CONTEXT = "/api/login/format/json";
	private static final String PREDATUM_SONG_POST_CONTEXT = "/api/nowplaying/format/json";

	private static Predatum instance = null;
	private static int currentSongId = -1;

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

	public void updateNowPlaying(HashMap<String, Object> song, Context context)
			throws ClientProtocolException, IOException {

		Iterator<Entry<String, Object>> iterator = song.entrySet().iterator();
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

		while (iterator.hasNext()) {
			@SuppressWarnings("rawtypes")
			HashMap.Entry pairs = (HashMap.Entry) iterator.next();
			nameValuePairs.add(new BasicNameValuePair(
					pairs.getKey().toString(), pairs.getValue().toString()));
			iterator.remove(); // avoids a ConcurrentModificationException
		}

		JSONObject serverResponse = predatumPost(nameValuePairs,
				PREDATUM_SONG_POST_CONTEXT, context);
		try {

			if (serverResponse.has("processed")) {
				// process different response
				if (serverResponse.has("np_data")) {
					Log.d(getClass().getSimpleName(),
							serverResponse.getString("np_data"));
					currentSongId = serverResponse.getJSONObject("np_data")
							.getInt("user_track");
				}
			} else {
				Toast toast = Toast.makeText(context,
						serverResponse.getString("error"), Toast.LENGTH_LONG);
				toast.show();
			}

		} catch (JSONException jsonException) {
			Log.e("Error while creating json object from http response",
					jsonException.getMessage());
		}
	}

	private boolean userIsLoggedIn(PersistentCookieStore cookieStore) {

		List<Cookie> predatumCookies = cookieStore.getCookies();
		return predatumCookies.size() >= 1;
	}

	private JSONObject predatumPost(List<NameValuePair> params,
			String controller, Context context) {

		PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(
				context);
		DefaultHttpClient mHttpClient = new DefaultHttpClient();
		JSONObject jResponse = null;
		try {
			HttpPost httppost = new HttpPost(PREDATUM_URL + controller);
			httppost.setHeader("User-Agent", getPredatoidUserAgent(context));			
			httppost.setEntity(new UrlEncodedFormEntity(params));
			BasicHttpContext mHttpContext = new BasicHttpContext();
			mHttpContext.setAttribute(ClientContext.COOKIE_STORE,
					predatumPersistentCookieStore);
			HttpResponse response = mHttpClient.execute(httppost, mHttpContext);
			String responseBody = EntityUtils.toString(response.getEntity());
			jResponse = new JSONObject(responseBody);
		} catch (ClientProtocolException cpEx) {
			Log.e(getClass().getSimpleName(), cpEx.toString());
		} catch (IOException ioEx) {
			Log.e(getClass().getSimpleName(), ioEx.toString());
		} catch (JSONException jsonEx) {
			Log.e(getClass().getSimpleName(), jsonEx.toString());
		}
		return jResponse;
	}

	private String getPredatoidUserAgent(Context context){
		String userAgent = "";
		try {
			userAgent = "Predatoid-";
			userAgent = userAgent + context.getPackageManager().getPackageInfo(context.getPackageName(), 0 ).versionName;
			userAgent = userAgent + " (" + Build.DEVICE + "/" + Build.VERSION.RELEASE + ")";
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return userAgent;
	}
	
	private void login(String username, String password, final Context context) {

		RequestParams params = new RequestParams();
		params.put("login", username);
		params.put("password", password);
		params.put("remember", "1");
		final AsyncHttpClient client = new AsyncHttpClient();
		PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(
				context);
		if (!userIsLoggedIn(predatumPersistentCookieStore)) {
			predatumPersistentCookieStore.clear();
		}
		client.setCookieStore(predatumPersistentCookieStore);
		// TODO: set user agent
		client.setUserAgent(getPredatoidUserAgent(context));
		client.post(PREDATUM_URL + PREDATUM_LOGIN_CONTEXT, params,
				new AsyncHttpResponseHandler() {

					@Override
					public void onSuccess(String response) {
						try {
							// Pull out the first event on the public timeline
							JSONObject message = new JSONObject(response);
							if (message.has("processed")) {
								Log.d(context.getClass().getSimpleName(),
										"you're logged in predatum");
								Toast toast = Toast.makeText(context,
										"You're now logged in predatum",
										Toast.LENGTH_LONG);
								toast.show();
							} else {
								Log.e(this.getClass().getSimpleName(),
										message.getString("error"));
							}

						} catch (JSONException jsonException) {
							Log.e("Error while creating json object from http response",
									jsonException.getMessage());
						}
					}

					@Override
					public void onFailure(Throwable error, String content) {
						Log.e("Error while posting to predatum", content);
						for (StackTraceElement ste : error.getStackTrace()) {
							Log.e(this.getClass().getName(), ste.toString());
						}
					}
				});
	}

	public int getCurrentSongID() {
		return currentSongId;
	}
}
