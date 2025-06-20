package com.example.paySplitter.View;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.transition.Fade;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.example.paySplitter.Controller.APIController;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.R;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// Asks for a group link and loads and adds the user to the group
public class EnterGroup extends AppCompatActivity {
    private final APIController apiController = APIController.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> settingsLauncher;
    private ArrayList<Group> groups;
    private Group group;
    // This method gets the sharedPrefs of darkMode and Language and sets them
    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("app_settings", MODE_PRIVATE);
        String langCode = prefs.getString("lang", Locale.getDefault().getLanguage());

        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);

        int nightMode = prefs.getBoolean("dark_mode", false)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(nightMode);

        super.attachBaseContext(newBase.createConfigurationContext(config));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        groups = (ArrayList<Group>) getIntent().getSerializableExtra("groups");
        //Gets the settings results to restart the settings activity
        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getBooleanExtra("reload", false)) {
                            Intent intent = new Intent(this, ViewSettings.class);
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                            settingsLauncher.launch(intent);
                        }
                    }else{
                        recreate();
                    }
                }
        );
        setContentView(R.layout.enter_group);

        EditText linkInput = findViewById(R.id.link_input);
        Button enterButton = findViewById(R.id.enter_button);
        Button cancelButton = findViewById(R.id.cancel_button);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.comfortaa_bold);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTypeface(typeface);
            }
        }

    // Loads the new Group

        enterButton.setOnClickListener(v -> {
            String link = linkInput.getText().toString().trim();
            if (!link.isEmpty()) {
                loadGroup(link);
            } else {
                Toast.makeText(this, R.string.link_warning, Toast.LENGTH_SHORT).show();
            }
        });
        //Goes back to the main page
        cancelButton.setOnClickListener(v -> {
            Intent intent = new Intent(EnterGroup.this, MainPage.class);
            intent.putExtra("groups", groups);
            startActivity(intent);
        });
    }
    //Checks if the user is already on the group, adds it if not and loads the group
    private void loadGroup(String groupLink) {

        final ArrayList<Group>[] groups = new ArrayList[]{new ArrayList<>()};
        executor.submit(()-> groups[0] = apiController.loadGroupNames());
        Boolean valid = true;

        for (Group g : groups[0]) {
            valid = !g.getId().equals(groupLink);
            if (!valid) {
                group = g;
                break;
            }
        }
        if (valid) {
            setContentView(R.layout.loading);
            executor.submit(() -> {
                try {
                    group = apiController.loadGroup(groupLink,true );
                    runOnUiThread(() -> {
                        while (group == null);
                        Toast.makeText(this, R.string.group_loaded, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, ViewGroup.class);
                        intent.putExtra("group", group);
                        startActivity(intent);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.group_loading_error, Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(this, EnterGroup.class);
                        startActivity(intent);
                    });
                }
            });
        }else{
            Toast.makeText(this, R.string.already_in_group, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ViewGroup.class);
            intent.putExtra("group", group);
            startActivity(intent);
        }


    }
    //Removes the keyboard when tapping outside the screen
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            if (view instanceof EditText) {
                Rect outRect = new Rect();
                view.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    view.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, ViewSettings.class);
            settingsLauncher.launch(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
