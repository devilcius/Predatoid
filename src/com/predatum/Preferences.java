package com.predatum;

import com.loopj.android.http.PersistentCookieStore;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

public class Preferences extends PreferenceActivity implements
		SharedPreferences.OnSharedPreferenceChangeListener {

	private boolean predatumUseCredsAreChanged = false;
	private boolean loginToPredatum = false;
	private String predatumUser = "";
	private String predatumUserPass = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setPreferenceScreen(createPreferenceHierarchy());
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case 0:
			return new AlertDialog.Builder(this).setIcon(R.drawable.new_icon)
					.setTitle(R.string.app_name).setMessage(R.string.strAbout)
					.create();
		}
		return null;
	}

	private PreferenceScreen createPreferenceHierarchy() {
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(
				this);
		root.setTitle(getString(R.string.app_name) + " "
				+ getString(R.string.strSettings));

		PreferenceCategory launchPrefCat = new PreferenceCategory(this);
		launchPrefCat.setTitle(R.string.strBSettings);
		root.addPreference(launchPrefCat);
		// resume last track played on launching the app
		CheckBoxPreference resumeLastTrack = new CheckBoxPreference(this);
		resumeLastTrack.setTitle(R.string.strResumeLastTrackPlayed);
		resumeLastTrack.setKey("resume_last_track_played");
		launchPrefCat.addPreference(resumeLastTrack);

		CheckBoxPreference loopMode = new CheckBoxPreference(this);
		loopMode.setTitle(R.string.strLoopMode);
		loopMode.setKey("loop_mode");
		launchPrefCat.addPreference(loopMode);

		CheckBoxPreference book_mode = new CheckBoxPreference(this);
		book_mode.setTitle(R.string.strSaveBooks);
		book_mode.setKey("book_mode");
		launchPrefCat.addPreference(book_mode);

		CheckBoxPreference hs_remove_mode = new CheckBoxPreference(this);
		hs_remove_mode.setTitle(R.string.strHsRemove);
		hs_remove_mode.setKey("hs_remove_mode");
		launchPrefCat.addPreference(hs_remove_mode);

		CheckBoxPreference hsInsertMode = new CheckBoxPreference(this);
		hsInsertMode.setTitle(R.string.strHsInsert);
		hsInsertMode.setKey("hs_insert_mode");
		launchPrefCat.addPreference(hsInsertMode);

		PreferenceCategory predatumPrefCat = new PreferenceCategory(this);
		predatumPrefCat.setTitle(R.string.web_app_name);
		root.addPreference(predatumPrefCat);

		CheckBoxPreference loginToPredatumPref = new CheckBoxPreference(this);
		loginToPredatumPref.setTitle(R.string.strLoginToPredatum);
		loginToPredatumPref.setKey("login_to_predatum");
		predatumPrefCat.addPreference(loginToPredatumPref);

		EditTextPreference userNamePref = new EditTextPreference(this);
		userNamePref.setTitle(R.string.strLoginUserName);
		userNamePref.setKey("login_username");
		predatumPrefCat.addPreference(userNamePref);

		EditTextPreference userPasswordPref = new EditTextPreference(this);
		userPasswordPref.setTitle(R.string.strLoginPassword);
		userPasswordPref.setKey("login_password");
		EditText passwordText = (EditText) userPasswordPref.getEditText();
		passwordText.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_VARIATION_PASSWORD);
		passwordText.setSelection(passwordText.getText().length());
		predatumPrefCat.addPreference(userPasswordPref);

		PreferenceCategory predatoidPrefCat = new PreferenceCategory(this);
		predatoidPrefCat.setTitle(R.string.app_name);
		root.addPreference(predatoidPrefCat);

		PreferenceScreen predatoidPrefAbout = getPreferenceManager()
				.createPreferenceScreen(this);
		predatoidPrefAbout
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {

					public boolean onPreferenceClick(Preference p) {
						showDialog(0);
						return false;
					}
				});
		predatoidPrefAbout.setTitle(R.string.strAbout1);
		predatoidPrefCat.addPreference(predatoidPrefAbout);
		return root;
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Set up a listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		// Unregister the listener whenever a key changes
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		loginToPredatum = sharedPreferences.getBoolean("login_to_predatum", false);
		
		if (key.equals("login_username")) {
			predatumUser = sharedPreferences.getString("login_username", "");
			predatumUseCredsAreChanged = true;
		}
		if (key.equals("login_password")) {
			predatumUserPass = sharedPreferences
					.getString("login_password", "");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (predatumUseCredsAreChanged && loginToPredatum) {
			PersistentCookieStore predatumPersistentCookieStore = new PersistentCookieStore(
					getApplicationContext());
			predatumPersistentCookieStore.clear();
			Predatum predatum = new Predatum();
			predatum.authenticateToPredatum(predatumUser, predatumUserPass,
					getApplicationContext());
			
			predatumUseCredsAreChanged = false;
		}
	}
}
