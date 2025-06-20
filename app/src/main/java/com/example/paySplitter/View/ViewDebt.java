package com.example.paySplitter.View;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import android.content.Intent;
import android.transition.Fade;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.paySplitter.App;
import com.example.paySplitter.Controller.APIController;
import com.example.paySplitter.Model.Debt;
import com.example.paySplitter.Model.Expense;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.example.paySplitter.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//Screen to view the debts of a group and pay them
public class ViewDebt extends AppCompatActivity {
    private ArrayList<Debt> debts;
    private Group group;
    private final APIController apiController = APIController.getInstance();
    private ActivityResultLauncher<Intent> settingsLauncher;

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
    //Shows the debts of the user
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
        setContentView(R.layout.view_debt);

        debts = (ArrayList<Debt>) getIntent().getSerializableExtra("debts");
        group = (Group) getIntent().getSerializableExtra("group");

        LinearLayout debtList = findViewById(R.id.debt_list);
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

        //Each debt is shown with a button to pay it if the user is the debtor
        for (Debt debt : debts) {
            LinearLayout debtItem = new LinearLayout(this);
            debtItem.setOrientation(LinearLayout.VERTICAL);

            TextView debtText = new TextView(this);
            if (debt.getDebtor().equals(apiController.getUser())) {
                debtText.setText(debt.getDebtor().getName() + getText(R.string.me) +" "+ getText(R.string.owes_to) +" "+ debt.getCreditor().getName() + ": " + debt.getAmount() + " " + group.getCurrency().getSymbol(group.getCurrency()));
            } else {
                debtText.setText(debt.getDebtor().getName() +" "+ getText(R.string.owes_to) +" "+ debt.getCreditor().getName() + getText(R.string.me) + ": " + debt.getAmount() + " " + group.getCurrency().getSymbol(group.getCurrency()));
            }
            debtItem.addView(debtText);
            if (debt.getDebtor().equals(apiController.getUser())){
                MaterialButton payBtn = new MaterialButton(this);
                payBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_grey40));
                payBtn.setText(R.string.pay_debt);
                payBtn.setOnClickListener(v -> payDebt(debt));
                debtItem.addView(payBtn);
            }

            debtList.addView(debtItem);
        }
        // Goes back to the group screen
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
    }
    //Pay the debt doing an equivalent expense and update the group
    private void payDebt(Debt debt) {
        debts.remove(debt);
        Expense payment = new Expense();
        payment.setName(getString(R.string.debt_paid));
        payment.setAmount(debt.getAmount());
        ArrayList<User> participants = new ArrayList<>();

        participants.add(debt.getCreditor());
        Map<User, Double> debtors =new HashMap<>();
        debtors.put(debt.getCreditor(),debt.getAmount());

        participants.add(debt.getDebtor());
        Map<User, Double> creditors = new HashMap<>();
        creditors.put(debt.getDebtor(),debt.getAmount());

        payment.setDebtors(debtors);
        payment.setCreditors(creditors);
        payment.setParticipants(participants);

        group.updateDebts(payment);
        ArrayList<Expense> expenses = group.getExpenses();
        expenses.add(payment);
        group.setExpenses(expenses);

        executor.submit(() -> {
            try {
                apiController.setExpense(group.getId(), group.getDebts(), group.getBalances(), payment);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.debt_paid_error, Toast.LENGTH_SHORT).show();
                    recreate();
                });
            }
        });
        new AlertDialog.Builder(this)
                .setTitle(R.string.debt_paid)
                .setMessage(getText(R.string.debt_message) +" "+ debt.getDebtor().getName()+getText(R.string.me) +" "+ getString(R.string.and) +" "+ debt.getCreditor().getName() +" "+ getString(R.string.has_been_paid))
                .setPositiveButton("OK", (dialog, which) -> {
                    if (!debts.isEmpty()) {
                        Intent intent = new Intent(this, ViewDebt.class);
                        intent.putExtra("group", group);
                        intent.putExtra("debts", debts);
                        startActivity(intent);
                        finish();
                    } else {
                        Intent intent = new Intent(this, ViewGroup.class);
                        intent.putExtra("group", group);
                        startActivity(intent);
                        finish();
                    }
                })
                .setCancelable(false)
                .show();

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
