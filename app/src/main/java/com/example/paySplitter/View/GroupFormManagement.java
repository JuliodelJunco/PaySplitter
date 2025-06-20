package com.example.paySplitter.View;

import android.content.Context;
import android.content.Intent;

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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.paySplitter.App;
import com.example.paySplitter.Controller.APIController;
import com.example.paySplitter.Model.Currency;
import com.example.paySplitter.Model.Expense;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.example.paySplitter.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//Screen to create and edit Groups
public class GroupFormManagement extends AppCompatActivity implements DeleteConfirmation.DeleteConfirmationListener {
    private Group group;
    private final APIController apiController = APIController.getInstance();
    private ActivityResultLauncher<Intent> settingsLauncher;

    private ArrayList<User> editableParticipants = new ArrayList<>();
    private User participantToDelete;
    private LinearLayout rowToDelete;
    private LinearLayout participantList;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // This method gets the sharedPrefs of darkMode and Language and sets them
    @Override
    protected void attachBaseContext(Context newBase) {
        // Gets the shared preferences
        SharedPreferences prefs = newBase.getSharedPreferences("app_settings", MODE_PRIVATE);
        String langCode = prefs.getString("lang", Locale.getDefault().getLanguage());
        // Sets the language
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        // Sets the dark mode
        int nightMode = prefs.getBoolean("dark_mode", false)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(nightMode);

        super.attachBaseContext(newBase.createConfigurationContext(config));
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        setContentView(R.layout.group_form);

        group = (Group) getIntent().getSerializableExtra("group");
        assert group != null;
        for (User participant : group.getParticipants()) {
            if (!editableParticipants.contains(participant)) {
                editableParticipants.add(participant);
            }
        }

        EditText nameInput = findViewById(R.id.group_name_input);
        Spinner currencySpinner = findViewById(R.id.currency_spinner);
        participantList = findViewById(R.id.participant_list);
        Button cancelBtn = findViewById(R.id.cancel_button);
        Button saveBtn = findViewById(R.id.save_button);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.comfortaa_bold);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTypeface(typeface);
            }
        }
        nameInput.setText(group.getName());
        nameInput.setHint(R.string.group_name_hint);
        //An spinner for the currency
        currencySpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                Currency.values()
        ));
        currencySpinner.setSelection(group.getCurrency().ordinal());
        // Provisional list of participants that lets you delete them after confirmation
        for (User participant : editableParticipants) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView name = new TextView(this);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            User user = apiController.getUser();

            if(!Objects.equals(participant.getGmail(), user.getGmail())) {
                name.setText(participant.getName());
                row.addView(name);

                MaterialButton removeBtn = new MaterialButton(this);
                removeBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_grey40));
                removeBtn.setText(R.string.delete);
                removeBtn.setOnClickListener(v -> {
                    participantToDelete = participant;
                    rowToDelete = row;
                    DeleteConfirmation dialog = DeleteConfirmation.newInstance(participant.getName());
                    dialog.show(getSupportFragmentManager(), "confirmDelete");
                });
                row.addView(removeBtn);
            }else{
                //The user can't delete itself from the group like this
                name.setText(String.format("%s"+getText(R.string.me), participant.getName()));
                row.addView(name);

            }
            participantList.addView(row);
        }
        // Goes back to the previous screen
        cancelBtn.setOnClickListener(v -> finish());
        //Saves the group
        saveBtn.setOnClickListener(v -> {
            setContentView(R.layout.loading);
            Boolean valid = true;
            final ArrayList<Group>[] groups = new ArrayList[]{new ArrayList<>()};
            executor.submit(() -> {
                groups[0] = apiController.loadGroupNames();
            });
            //Checks if the group name already exists
            for(Group g : groups[0]){
                valid = !g.getName().equals(nameInput.getText().toString()) || group.getName().equals(g.getName());
                if(!valid) break;
            }
            group.setName(nameInput.getText().toString());
            if(!group.getName().isEmpty()) {
                if(valid) {
                    group.setCurrency((Currency) currencySpinner.getSelectedItem());
                    ArrayList<Expense> expenses = new ArrayList<>();
                    executor.submit(() -> {
                        try {
                            for(Expense expense : group.getExpenses()){
                                Expense loadedExpense = apiController.loadExpense(group, expense.getName());
                                while (loadedExpense == null);
                                expenses.add(loadedExpense);
                            }
                            group.setExpenses(expenses);
                            group.setParticipants(editableParticipants);
                            Intent result = new Intent();
                            result.putExtra("result_type", "GROUP_RESULT");
                            result.putExtra("newGroup", group);
                            setResult(RESULT_OK, result);
                            finish();
                        }catch (Exception e){
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.group_loading_error, Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(this, App.class);
                                startActivity(intent);
                            });
                        }
                    });
                }else{
                    Toast.makeText(this, R.string.group_name_in_use, Toast.LENGTH_LONG).show();
                }
            }else{
                Toast.makeText(this, R.string.group_name_missing, Toast.LENGTH_LONG).show();
            }
        });
    }
    //Deletes the participant from the group
    @Override
    public void onConfirmDelete() {
        if (participantToDelete != null && rowToDelete != null) {
            editableParticipants.remove(participantToDelete);
            participantList.removeView(rowToDelete);
            participantToDelete = null;
            rowToDelete = null;
        }
    }

    @Override
    public void onCancelDelete() {
        participantToDelete = null;
        rowToDelete = null;
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
