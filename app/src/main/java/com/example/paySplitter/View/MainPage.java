package com.example.paySplitter.View;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.transition.Fade;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.paySplitter.App;
import com.example.paySplitter.Controller.APIController;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.example.paySplitter.R;
import com.google.android.material.button.MaterialButton;


import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//Screen to select a group or to create or enter a new group
public class MainPage extends AppCompatActivity {
    private final APIController apiController = APIController.getInstance();
    private ArrayList<Group> groups;
    private ActivityResultLauncher<Intent> settingsLauncher;

    private ActivityResultLauncher<Intent> groupFormLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        //Receives the result of calling the group form to create a new group
        groupFormLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Group newGroup = (Group) result.getData().getSerializableExtra("newGroup");
                        if (!Objects.equals(newGroup.getName(), "")) {
                            setContentView(R.layout.loading);
                            executor.submit(() -> {
                                try {
                                    //Adds the group to the server
                                    apiController.addGroup(newGroup);
                                    groups = apiController.loadGroupNames();
                                    runOnUiThread(() -> {
                                        new AlertDialog.Builder(this)
                                                .setTitle(R.string.group_created_title)
                                                .setMessage(getString(R.string.group) +" " + newGroup.getName() +" "+ getString(R.string.created_message))
                                                .setNeutralButton(getString(R.string.copy_code) , (dialog, which) -> {
                                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                                    ClipData clip = ClipData.newPlainText(getString(R.string.copied_text), newGroup.getId());
                                                    clipboard.setPrimaryClip(clip);
                                                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                                                    setupUI();
                                                })
                                                .setPositiveButton("OK", (dialog, which) -> setupUI())
                                                .setCancelable(false)
                                                .show();
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, R.string.group_creating_error, Toast.LENGTH_LONG).show();
                                        Intent intent = new Intent(this, App.class);
                                        startActivity(intent);
                                    });
                                }
                            });
                        }
                    }
                }
        );
        setupUI();

    }
    // sets the screen with the groups and the buttons to create and enter a group
    private void setupUI() {
        try{
            setContentView(R.layout.main_page);
        }catch (Exception e){
            e.printStackTrace();
            Intent intent = new Intent(this, App.class);
            startActivity(intent);
        }
        LinearLayout groupContainer = findViewById(R.id.group_container);
        Button enterGroupBtn = findViewById(R.id.enter_group_button);
        Button createGroupBtn = findViewById(R.id.create_group_button);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.comfortaa_bold);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTypeface(typeface);
            }
        }

        groupContainer.removeViews(0, groupContainer.getChildCount() - 2);
        if (groups != null) {
            for (Group group : groups) {
                MaterialButton groupButton = new MaterialButton(this);
                groupButton.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_grey40));
                groupButton.setText(group.getName());
                groupButton.setOnClickListener(v -> viewGroup(group));
                groupContainer.addView(groupButton, 0);
            }
        }else{
            //Text for when there are no expenses
            TextView noGroups = new TextView(this);
            noGroups.setText(getString(R.string.no_groups));
            groupContainer.addView(noGroups);
        }

        enterGroupBtn.setOnClickListener(v -> enterGroup());
        createGroupBtn.setOnClickListener(v -> newGroup());
    }

    //Loads the group and calls the view group screen
    private void viewGroup(Group group) {
        setContentView(R.layout.loading);
        final Group[] finalGroup = {group};
        executor.submit(() -> {
            try {
                finalGroup[0] = apiController.loadGroup(finalGroup[0].getId(),false);
                runOnUiThread(() -> {
                    while (finalGroup[0] == null);
                    Intent intent = new Intent(this, ViewGroup.class);
                    intent.putExtra("group", finalGroup[0]);
                    startActivity(intent);
                });
            }catch (Exception e){
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, R.string.group_loading_error, Toast.LENGTH_LONG).show());
                Intent intent = new Intent(this, App.class);
                startActivity(intent);
            }

        });

    }
    //Calls the enter group screen
    private void enterGroup() {
        Intent intent = new Intent(this, EnterGroup.class);
        intent.putExtra("groups", groups);
        startActivity(intent);
    }
    //Calls the group form to create a new group
    public void newGroup() {
        Intent intent = new Intent(this, GroupFormManagement.class);
        ArrayList<User> participants = new ArrayList<>();
        participants.add(apiController.getUser());
        Group group = new Group();
        group.setParticipants(participants);
        intent.putExtra("group", group);

        groupFormLauncher.launch(intent);
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