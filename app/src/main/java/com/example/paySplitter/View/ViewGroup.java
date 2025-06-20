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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
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
import com.example.paySplitter.Model.Currency;
import com.example.paySplitter.Model.Debt;
import com.example.paySplitter.Model.Expense;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.example.paySplitter.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//Shows the name, participants, expenses and code of a group
public class ViewGroup extends AppCompatActivity implements DeleteConfirmation.DeleteConfirmationListener{
    private Group group;
    private final APIController apiController = APIController.getInstance();
    private ActivityResultLauncher<Intent> settingsLauncher;

    private ActivityResultLauncher<Intent> formLauncher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ArrayList<Debt> debts = new ArrayList<>();

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
    public void onCreate(Bundle savedInstanceState) {
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
        setContentView(R.layout.view_group);
        group = (Group) getIntent().getSerializableExtra("group");

        if (group == null) {
            Toast.makeText(this, R.string.group_loading_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextView nameText = findViewById(R.id.group_name);
        LinearLayout codeLayout = findViewById(R.id.group_code);
        LinearLayout participantsLayout = findViewById(R.id.participants_list);
        LinearLayout expensesLayout = findViewById(R.id.expenses_list);

        Button deleteBtn = findViewById(R.id.delete_group_button);
        Button editBtn = findViewById(R.id.edit_group_button);
        Button debtBtn = findViewById(R.id.view_debt_button);
        Button newBtn = findViewById(R.id.new_expense_button);
        Button backButton = findViewById(R.id.back_button);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.comfortaa_bold);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTypeface(typeface);
            }
        }
        //Setting the name, the group code with a button to copy it and the participants
        nameText.setText(group.getName());

        TextView codeText = new TextView(this);
        SpannableString str = new SpannableString(getString(R.string.group_code));
        str.setSpan(new StyleSpan(Typeface.BOLD), 0, str.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        codeText.setText(TextUtils.concat(str," ", group.getId()));
        MaterialButton codeBtn = new MaterialButton(this);
        codeBtn.setText(getString(R.string.copy_code));
        codeBtn.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getString(R.string.copied_text), group.getId());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
        });
        codeLayout.addView(codeText);
        codeLayout.addView(codeBtn);

        User user = apiController.getUser();

        for (User participant : group.getParticipants()) {
            TextView participantView = new TextView(this);
            if(!Objects.equals(participant.getGmail(), user.getGmail())) {
                participantView.setText(participant.getName());
            }else participantView.setText(String.format("%s" + getString(R.string.me), participant.getName()));
            participantsLayout.addView(participantView);
        }
        if(group.getExpenses() != null && !group.getExpenses().isEmpty()) {
            Currency currency = group.getCurrency();
            String symbol = currency.getSymbol(currency);
            for (Expense expense : group.getExpenses()) {
                MaterialButton expenseBtn = new MaterialButton(this);
                expenseBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_grey40));
                expenseBtn.setText(String.format("%s - %s%s", expense.getName(), expense.getAmount(), symbol));
                expenseBtn.setOnClickListener(v -> viewExpense(expense));
                expensesLayout.addView(expenseBtn);
            }
        }else{
            //Text for when there are no expenses
            TextView noExpenses = new TextView(this);
            noExpenses.setText(getString(R.string.no_expenses));
            expensesLayout.addView(noExpenses);
        }
        //Call to delete the group
        deleteBtn.setOnClickListener(v -> deleteGroup());
        //Call to edit the group
        editBtn.setOnClickListener(v -> editGroup());

        //Check if there are debts to show the button
        for (Debt debt: group.getDebts()){
            if(debt.getCreditor().equals(user) || debt.getDebtor().equals(user)){
                debts.add(debt);
            }
        }
        if(debts == null || debts.isEmpty()){
            debtBtn.setVisibility(View.GONE);
        }
        debtBtn.setOnClickListener(v -> viewDebt());
        //Call to create a new expense
        newBtn.setOnClickListener(v -> newExpense());
        //Back button
        backButton.setOnClickListener(v -> {
            setContentView(R.layout.loading);
            final ArrayList<Group>[] groups = new ArrayList[]{new ArrayList<>()};
            groups[0] = null;
            executor.submit(() -> {
                try {
                    // Executes the API call
                    groups[0] = apiController.loadGroupNames();

                    runOnUiThread(() -> {
                        while (groups[0] == null);
                        Intent intent = new Intent(this, MainPage.class);
                        intent.putExtra("groups", groups[0]);
                        startActivity(intent);
                        finish();
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.group_loading_error, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        });
        //Catches the result of the form when editing the group or adding an expense
        formLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String resultType = result.getData().getStringExtra("result_type");

                        switch (Objects.requireNonNull(resultType)) {
                            //Case to edit the group
                            case "GROUP_RESULT":
                                setContentView(R.layout.loading);
                                Group newGroup = (Group) result.getData().getSerializableExtra("newGroup");
                                if (!Objects.equals(Objects.requireNonNull(newGroup).getName(), "")) {
                                    groupEdited(group.getName(),newGroup);
                                }else{
                                    recreate();
                                }
                                break;
                            //Case to add an expense
                            case "EXPENSE_RESULT":
                                setContentView(R.layout.loading);
                                ArrayList<Expense> expenses = group.getExpenses();
                                Expense newExpense = (Expense) result.getData().getSerializableExtra("expense");
                                if(newExpense != null && !Objects.equals(newExpense.getName(), "")){

                                    expenses.add(newExpense);
                                    group.setExpenses(expenses);

                                    group.updateDebts(newExpense);
                                    Future<?> future = executor.submit(() -> {
                                        apiController.setExpense(group.getId(),group.getDebts(),group.getBalances(),newExpense);
                                    });
                                    executor.submit(() -> {
                                        try {
                                            future.get(); // Wait for the future to complete
                                            runOnUiThread(() -> {
                                                new AlertDialog.Builder(this)
                                                        .setTitle(R.string.expense_created)
                                                        .setMessage(getString(R.string.expense) +" " + newExpense.getName()+" " + getString(R.string.created_message))
                                                        .setPositiveButton("OK", (dialog, which) -> {
                                                            Intent intent = new Intent(this, ViewGroup.class);
                                                            intent.putExtra("group", group);
                                                            startActivity(intent);
                                                        })
                                                        .setCancelable(false)
                                                        .show();
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            Intent intent = new Intent(this, App.class);
                                            startActivity(intent);
                                        }
                                    });
                                }else{
                                    recreate();
                                }
                        }

                    }else{
                        recreate();
                    }
                }
        );
    }
    //Calls to ViewExpense if expense properly loaded
    private void viewExpense(Expense expense) {
        setContentView(R.layout.loading);
        executor.submit(() ->{
        try {
            final Expense[] finalExpense = {null};
            finalExpense[0] = apiController.loadExpense(group, expense.getName());
            runOnUiThread(() -> {
                while (finalExpense[0] == null);
                ArrayList<Expense> expenses = group.getExpenses();
                for(int i = 0; i < expenses.size(); i++){
                    if(Objects.equals(expenses.get(i).getName(), finalExpense[0].getName())){
                        expenses.set(i,finalExpense[0]);
                        break;
                    }
                }
                Intent intent = new Intent(this, ViewExpense.class);
                intent.putExtra("group", group);
                intent.putExtra("expense", finalExpense[0]);
                startActivity(intent);
            });
        }catch (Exception e){
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, R.string.expense_loading_error, Toast.LENGTH_LONG).show());
            Intent intent = new Intent(this, ViewGroup.class);
            intent.putExtra("group", group);
            startActivity(intent);

        }
        });
    }

    //Calls to ViewDebt with the debts of the user
    private void viewDebt() {
        Intent intent = new Intent(this, ViewDebt.class);

        intent.putExtra("debts", debts);
        intent.putExtra("group", group);
        startActivity(intent);
    }
    //Calls to ExpenseFormManagement to create a new expense
    public void newExpense() {
        Expense newExpense = new Expense();
        Intent intent = new Intent(this, ExpenseFormManagement.class);
        intent.putExtra("group", group);
        intent.putExtra("expense", newExpense);
        formLauncher.launch(intent);
    }
    //Calls to DeleteConfirmation
    public void deleteGroup() {
        DeleteConfirmation dialog = DeleteConfirmation.newInstance(group.getName());
        dialog.show(getSupportFragmentManager(), "confirmDelete");
    }
    //If the user is the only participant, deletes the group, else deletes the user from the list of participants
    @Override
    public void onConfirmDelete() {
        //Removes the user from the list of participants
        setContentView(R.layout.loading);
        ArrayList<Expense> expenses = new ArrayList<>();
        executor.submit(() -> {
            try {
                for(Expense expense : group.getExpenses()){
                    Expense loadedExpense = apiController.loadExpense(group, expense.getName());
                    while (loadedExpense == null);
                    expenses.add(loadedExpense);
                }
            }catch (Exception e){
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.group_loading_error, Toast.LENGTH_LONG).show();
                    recreate();
                });
            }

        });
        group.setExpenses(expenses);
        ArrayList<User> participants = group.getParticipants();
        participants.remove(apiController.getUser());
        final ArrayList<Group>[] groups = new ArrayList[]{null};
        group.setParticipants(participants);
        if (group.getParticipants().isEmpty()) {

            executor.submit(() -> {
                try {
                    //Deletes the group
                    apiController.deleteGroup(group);
                    groups[0] = apiController.loadGroupNames();
                    runOnUiThread(() -> {
                        while (groups[0] == null) ;
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.group_deleting_title)
                                .setMessage(getString(R.string.group) + " " + group.getName() + " " + getString(R.string.deleted_message))
                                .setPositiveButton("OK", (dialog, which) -> {
                                    Intent intent = new Intent(this, MainPage.class);
                                    intent.putExtra("groups", groups[0]);
                                    startActivity(intent);
                                })
                                .setCancelable(false)
                                .show();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.group_deleting_error, Toast.LENGTH_LONG).show();
                    recreate();
                }
            });
        }else{
            executor.submit(() -> {
                try {
                    //Deletes the user from the list of participants
                    apiController.setParticipants(group.getId(),group);
                    groups[0] = apiController.loadGroupNames();
                    runOnUiThread(() -> {
                        while (groups[0] == null) ;
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.group_deleting_title)
                                .setMessage(getString(R.string.group) + " " + group.getName() + " " + getString(R.string.deleted_message))
                                .setPositiveButton("OK", (dialog, which) -> {
                                    Intent intent = new Intent(this, MainPage.class);
                                    intent.putExtra("groups", groups[0]);
                                    startActivity(intent);
                                })
                                .setCancelable(false)
                                .show();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.group_deleting_error, Toast.LENGTH_LONG).show();
                    recreate();
                }
            });
        }
    }

    @Override
    public void onCancelDelete() {

    }
    //Calls to GroupFormManagement to edit the group
    public void editGroup(){
        Intent intent = new Intent(this, GroupFormManagement.class);
        intent.putExtra("group", group);
        formLauncher.launch(intent);
    }
    //Receives the new group and saves the changes
    public void groupEdited(String groupName, Group newGroup) {
        executor.submit(() -> {
            try {
                apiController.setGroup(newGroup);

                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.group_edited_title)
                            .setMessage(getString(R.string.group) +" "+ groupName +" "+ getString(R.string.correctly_edited))
                            .setPositiveButton("OK", (dialog, which) -> {
                                Intent intent = new Intent(this, ViewGroup.class);
                                intent.putExtra("group", newGroup);
                                startActivity(intent);
                            })
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent(this, App.class);
                startActivity(intent);
            }
        });
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
