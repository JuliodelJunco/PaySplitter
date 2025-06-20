package com.example.paySplitter.Controller;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANG = "lang";

    /**
     * Attaches the correct locale to the context.
     */
    public static Context onAttach(@NonNull Context context) {
        String lang = getPersistedLanguage(context);
        return setLocale(context, lang);
    }

    /**
     * Gets the persisted language from SharedPreferences.
     */
    public static String getPersistedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_LANG, Locale.getDefault().getLanguage());
    }

    /**
     * Sets a new locale and persists it.
     */
    public static Context setLocale(@NonNull Context context, @NonNull String languageCode) {
        persistLanguage(context, languageCode);

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            Resources res = context.getResources();
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }

    /**
     * Persists the selected language in SharedPreferences.
     */
    private static void persistLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, languageCode).apply();
    }
}