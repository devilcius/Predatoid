package com.predatum;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.InputType;
import android.widget.EditText;

public class Preferences extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPreferenceScreen(createPreferenceHierarchy());
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case 0:
                return new AlertDialog.Builder(this).setIcon(R.drawable.new_icon).setTitle(R.string.app_name).setMessage(R.string.strAbout).create();
        }
        return null;
    }

    private PreferenceScreen createPreferenceHierarchy() {
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
        root.setTitle(getString(R.string.app_name) + " " + getString(R.string.strSettings));

        PreferenceCategory launchPrefCat = new PreferenceCategory(this);
        launchPrefCat.setTitle(R.string.strBSettings);
        root.addPreference(launchPrefCat);
        //resume last track played on launching the app
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
        passwordText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordText.setSelection(passwordText.getText().length());
        predatumPrefCat.addPreference(userPasswordPref);

        PreferenceCategory predatoidPrefCat = new PreferenceCategory(this);
        predatoidPrefCat.setTitle(R.string.app_name);
        root.addPreference(predatoidPrefCat);

        PreferenceScreen predatoidPrefAbout = getPreferenceManager().createPreferenceScreen(this);
        predatoidPrefAbout.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            public boolean onPreferenceClick(Preference p) {
                showDialog(0);
                return false;
            }
        });
        predatoidPrefAbout.setTitle(R.string.strAbout1);
        predatoidPrefCat.addPreference(predatoidPrefAbout);
        return root;
    }
}
