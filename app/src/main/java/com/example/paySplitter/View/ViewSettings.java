package com.example.paySplitter.View;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.Fade;
import android.transition.Transition;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;

import com.example.paySplitter.App;
import com.example.paySplitter.Controller.LocaleHelper;
import com.example.paySplitter.Controller.SimpleItemSelectedListener;
import com.example.paySplitter.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class ViewSettings extends AppCompatActivity {

    private SwitchMaterial themeSwitch;
    private Spinner languageSpinner;
    private SharedPreferences prefs;
    private boolean isApplyingChanges = false;

    // Modified to use LocaleHelper
    @Override
    protected void attachBaseContext(Context newBase) {
        // Get theme preference first
        SharedPreferences prefs = newBase.getSharedPreferences("app_settings", MODE_PRIVATE);
        int nightMode = prefs.getBoolean("dark_mode", false)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(nightMode);

        // Let LocaleHelper handle the locale configuration
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            isApplyingChanges = true;
            super.onCreate(savedInstanceState);
            setContentView(R.layout.view_settings);

            prefs = getSharedPreferences("app_settings", MODE_PRIVATE);

            // Initialize views and toolbar (unchanged)
            themeSwitch = findViewById(R.id.switch_theme);
            languageSpinner = findViewById(R.id.spinner_language);
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            toolbar.setNavigationOnClickListener(v -> {
                if(!isApplyingChanges) finish();
            });

            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish();
                }
            });

            Typeface typeface = ResourcesCompat.getFont(this, R.font.comfortaa_bold);
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View view = toolbar.getChildAt(i);
                if (view instanceof TextView) {
                    ((TextView) view).setTypeface(typeface);
                }
            }

            // Set up spinner (unchanged)
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    this,
                    R.array.language_options,
                    android.R.layout.simple_spinner_item
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            languageSpinner.setAdapter(adapter);

            // Set up theme switch (unchanged)
            themeSwitch.setChecked(prefs.getBoolean("dark_mode", false));
            themeSwitch.setOnClickListener(v -> {
                boolean newState = themeSwitch.isChecked();
                themeSwitch.setChecked(newState);
                prefs.edit().putBoolean("dark_mode", newState).apply();
                restartApp();
            });
            themeSwitch.setOnCheckedChangeListener(null);

            // Modified to use LocaleHelper
            languageSpinner.setSelection(getLanguageIndex(LocaleHelper.getPersistedLanguage(this)));
            languageSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(pos -> {
                String langCode = getResources().getStringArray(R.array.language_codes)[pos];
                setAppLocale(langCode);
            }));

            isApplyingChanges = false;

        } catch (Exception e) {
            e.printStackTrace();
            Intent intent = new Intent(this, App.class);
            startActivity(intent);
        }
    }

    private int getLanguageIndex(String code) {
        String[] codes = getResources().getStringArray(R.array.language_codes);
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(code)) return i;
        }
        return 0;
    }

    // Modified to use LocaleHelper
    private void setAppLocale(String langCode) {
        String currentLang = LocaleHelper.getPersistedLanguage(this);
        if (currentLang.equals(langCode)) return;

        // Let LocaleHelper handle the locale change
        LocaleHelper.setLocale(this, langCode);
        restartApp();
    }

    private void restartApp() {
        if (isApplyingChanges) return;
        isApplyingChanges = true;
        new Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent result = new Intent();
            result.putExtra("reload", true);
            setResult(RESULT_OK, result);
            isApplyingChanges = false;
            finish();
        }, 300);
    }
}