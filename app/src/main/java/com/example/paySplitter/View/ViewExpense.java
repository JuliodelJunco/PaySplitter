package com.example.paySplitter.View;

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
import com.example.paySplitter.Model.Currency;
import com.example.paySplitter.Model.Expense;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.example.paySplitter.R;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
//This class show the expense and permits the user to edit and delete it
public class ViewExpense extends AppCompatActivity implements DeleteConfirmation.DeleteConfirmationListener{
    private Expense expense;
    private Group group;
    private ActivityResultLauncher<Intent> expenseFormLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;
    private final APIController apiController = APIController.getInstance();
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
        setContentView(R.layout.view_expense);

        expense = (Expense) getIntent().getSerializableExtra("expense");
        group = (Group) getIntent().getSerializableExtra("group");

        TextView expenseName = findViewById(R.id.expense_name);
        TextView expenseAmount = findViewById(R.id.expense_amount);
        LinearLayout participantList = findViewById(R.id.participant_list);
        Button deleteBtn = findViewById(R.id.delete_expense_button);
        Button editBtn = findViewById(R.id.edit_expense_button);
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

        expenseName.setText(expenseName.getText() + ": " + expense.getName());

        Currency currency = group.getCurrency();
        String symbol = currency.getSymbol(currency);
        expenseAmount.setText(expenseAmount.getText() + ": " + expense.getAmount() + symbol);


        TextView payedBy = new TextView(this);
        payedBy.setText(R.string.payed_by);
        payedBy.setTextSize(20);
        participantList.addView(payedBy);
        User user = apiController.getUser();
        //List of creditors
        for (Map.Entry<User, Double> entry : expense.getCreditors().entrySet()) {
            User participant = entry.getKey();
            double price = entry.getValue();

            TextView row = new TextView(this);
            String text;
            if(!Objects.equals(participant.getGmail(), user.getGmail())) {
                text = participant.getName() + ": " + price + symbol;
            }else{
                text = participant.getName()+ getString(R.string.me) + ": " + price + symbol;
            }
            row.setText(text);
            row.setTextColor(ContextCompat.getColor(this, R.color.green));
            row.setTextSize(16);
            participantList.addView(row);
        }

        TextView participants = new TextView(this);
        participants.setText(R.string.debtors);
        participants.setTextSize(20);
        participantList.addView(participants);
        //List of debtors
        for (Map.Entry<User, Double> entry : expense.getDebtors().entrySet()) {
            User participant = entry.getKey();
            double price = entry.getValue();

            TextView row = new TextView(this);
            String text;
            if(!Objects.equals(participant.getGmail(), user.getGmail())) {
                text = participant.getName() + ": " + price + symbol;
            }else{
                text = participant.getName()+ getString(R.string.me) + ": " + price + symbol;
            }
            row.setText(text);
            row.setTextColor(ContextCompat.getColor(this, R.color.red));
            row.setTextSize(16);
            participantList.addView(row);
        }
        // Delete and edit buttons
        deleteBtn.setOnClickListener(v -> {
            deleteExpense();
        });

        editBtn.setOnClickListener(v -> {
            editExpense();
        });
        //Back button
        backButton.setOnClickListener(v -> {
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
        });
        // Catches and saves the result of the form when editing the expense
        expenseFormLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        setContentView(R.layout.loading);
                        Expense expense = (Expense) result.getData().getSerializableExtra("expense");
                        if(!Objects.equals(expense.getName(), "")){
                            expenseEdited(expense);
                        }else{
                            recreate();
                        }

                    }else{
                        recreate();
                    }
                }
        );
    }
    //Calls to DeleteConfirmation
    private void deleteExpense(){
        DeleteConfirmation dialog = DeleteConfirmation.newInstance(expense.getName());
        dialog.show(getSupportFragmentManager(), "confirmDelete");
    }
    //Delete expense and saves the changes
    @Override
    public void onConfirmDelete() {
        ArrayList<Expense> expenses = group.getExpenses();
        boolean done = false;
        for(int i = 0; i < expenses.size(); i++){
            if(expenses.get(i).equals(expense)){
                expenses.remove(i);
                done = true;
                break;
            }
        }
        group.setExpenses(expenses);
        if(done) {
            updateReverseExpense(expense);
        }
        Future<?> future = executor.submit(() -> {
            apiController.deleteExpense(group.getId(),group.getDebts(),group.getBalances(),expense);
        });
        executor.submit(() -> {
            try {
                future.get(); // Wait for it to stop
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent(this, App.class);
                startActivity(intent);
            }
        });
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

    @Override
    public void onCancelDelete() {
        recreate();
    }
    //Calls to ExpenseFormManagement
    public void editExpense(){
        Intent intent = new Intent(this, ExpenseFormManagement.class);
        intent.putExtra("group", group);
        intent.putExtra("expense", expense);
        expenseFormLauncher.launch(intent);
    }
    //Edit expense and saves the changes
    public void expenseEdited(Expense newExpense) {
        ArrayList<Expense> expenses = group.getExpenses();
        ArrayList<Expense> expensesAux = new ArrayList<>();
        for(Expense expenseAux: expenses){
            if(expenseAux.equals(expense)){
                expensesAux.add(newExpense);
            }else{
                expensesAux.add(expenseAux);
            }
        }
        group.setExpenses(expensesAux);
        updateReverseExpense(newExpense);

        group.updateDebts(newExpense);
        Future<?> future = executor.submit(() -> {
            apiController.deleteExpense(group.getId(),group.getDebts(),group.getBalances(),expense);
            apiController.setExpense(group.getId(),group.getDebts(),group.getBalances(),newExpense);
        });
        executor.submit(() -> {
            try {
                future.get(); // Waits for it to stop
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.edited_expense)
                            .setMessage(getString(R.string.expense) +" "+ expense.getName() +" "+ getString(R.string.correctly_edited))
                            .setPositiveButton("OK", (dialog, which) -> {
                                Intent intent = new Intent(this, ViewExpense.class);
                                intent.putExtra("group", group);
                                intent.putExtra("expense", newExpense);
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
    //Flips the expense so when updating the debts, it is equivalent to deleting it
    private void updateReverseExpense(Expense expense) {
        expense.flipExpense();
        group.updateDebts(expense);
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
