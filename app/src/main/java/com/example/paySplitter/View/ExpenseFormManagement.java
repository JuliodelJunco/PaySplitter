package com.example.paySplitter.View;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.Fade;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;

import com.example.paySplitter.Controller.APIController;
import com.example.paySplitter.Model.Expense;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.example.paySplitter.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
//Screen to create and edit Expenses
public class ExpenseFormManagement extends AppCompatActivity {
    private Group group;
    private Expense expense;
    private final APIController apiController = APIController.getInstance();
    private ActivityResultLauncher<Intent> settingsLauncher;

    private EditText expenseNameInput;
    private TextView creditLabel;
    private TextView debtLabel;
    private LinearLayout creditorsContainer;
    private LinearLayout debtorsContainer;
    private Spinner distributionTypeSpinner;
    private Button saveButton;
    private Button cancelButton;

    private Map<User, Double> creditors = new HashMap<>();
    private Map<User, Double> debtors = new HashMap<>();
    private String previousDistributionType = "unequal";
    private String distributionType;
    private double totalCredit = 0;
    private double totalDebt = 0;

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
        setContentView(R.layout.expense_form);

        group = (Group) getIntent().getSerializableExtra("group");
        expense = (Expense) getIntent().getSerializableExtra("expense");

        expenseNameInput = findViewById(R.id.expense_name_input);
        creditLabel = findViewById(R.id.credit_label);
        creditorsContainer = findViewById(R.id.creditors_container);
        debtLabel = findViewById(R.id.debt_label);
        debtorsContainer = findViewById(R.id.debtors_container);
        distributionTypeSpinner = findViewById(R.id.distribution_type_spinner);
        saveButton = findViewById(R.id.save_expense_button);
        cancelButton = findViewById(R.id.cancel_expense_button);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Typeface typeface = ResourcesCompat.getFont(this, R.font.comfortaa_bold);
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTypeface(typeface);
            }
        }
        //This spinner sets the way of distribution of the money, equal parts, in percentage or unequal
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.distribution_options)
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        distributionTypeSpinner.setAdapter(adapter);
        distributionTypeSpinner.setSelection(2);

        distributionTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        distributionType = "equal";
                        break;
                    case 1:
                        distributionType = "percentage";
                        break;
                    case 2:
                        distributionType = "unequal";
                        break;
                }
                updateScreen();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        expenseNameInput.setHint(R.string.expense_name_hint);
        expenseNameInput.setText(expense.getName());

        User user = apiController.getUser();
        //Puts the participants of the group as choosable for being creditors or debtors
        for (User participant : group.getParticipants()) {
            CheckBox payerCheckbox = new CheckBox(this);
            if(!participant.equals(user)) {
                payerCheckbox.setText(participant.getName());
            }else{
                payerCheckbox.setText(participant.getName() + getText(R.string.me));
            }
            payerCheckbox.setChecked(expense.getCreditors().containsKey(participant));
            payerCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateScreen());
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(payerCheckbox);
            if (payerCheckbox.isChecked()) {
                EditText amountInput = new EditText(this);
                amountInput.setHint(R.string.creditor_hint);

                amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                if (expense.getCreditors().containsKey(participant)) {
                    amountInput.setText(String.valueOf(expense.getCreditors().get(participant)));
                }
                amountInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        String input = s.toString();

                        checkNumber(input,amountInput,this);
                        getTotalCredit();
                        creditLabel.setText(String.format(getText(R.string.creditors_label) + "%.2f%s", totalCredit, group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.creditors_hint)));
                    }
                });
                row.addView(amountInput);
            }
            creditorsContainer.addView(row);
        }
        //Gets the total amount of the creditors checked
        getTotalCredit();
        creditLabel.setText(String.format(getText(R.string.creditors_label) + "%.2f%s", totalCredit, group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.creditors_hint)));

        for (User participant : group.getParticipants()) {
            CheckBox participantCheckbox = new CheckBox(this);
            if(!participant.equals(user)) {
                participantCheckbox.setText(participant.getName());
            }else{
                participantCheckbox.setText(participant.getName() + getText(R.string.me));
            }
            participantCheckbox.setChecked(expense.getDebtors().containsKey(participant));
            participantCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updateScreen());

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(participantCheckbox);

            if (participantCheckbox.isChecked()) {
                EditText input = new EditText(this);
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                input.setEnabled(true);
                input.setText(String.valueOf(expense.getDebtors().get(participant)));
                input.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        String auxInput = s.toString();

                        checkNumber(auxInput,input,this);
                        getTotalDebt();
                        debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalDebt , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.unequal_hint)));
                    }
                });
                row.addView(input);
            }

            debtorsContainer.addView(row);
        }
        //Gets the total amount of the debtors checked
        getTotalDebt();
        debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalDebt , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.unequal_hint)));
        //Goes back to the previous screen
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        saveButton.setOnClickListener(v -> saveExpense());
    }
//Gets the total amount of the creditors checked
    private void getTotalCredit() {
        totalCredit = 0;
        for (int i = 0; i < creditorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) creditorsContainer.getChildAt(i);
            CheckBox check = (CheckBox) row.getChildAt(0);
            if (check.isChecked()){
                EditText input = (EditText) row.getChildAt(1);
                String value = input.getText().toString();
                if (!value.isEmpty()) {
                    totalCredit += Double.parseDouble(value);
                }
            }
        }
    }
    //Gets the total amount of the debtors checked
    private void getTotalDebt() {
        totalDebt = 0;

        for (int i = 0; i < debtorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) debtorsContainer.getChildAt(i);

            if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                EditText input = (EditText) row.getChildAt(1);
                String value = input.getText().toString();

                try {
                    double amount;

                    if (Objects.equals(previousDistributionType, "percentage")) {
                        double percentage = Double.parseDouble(value);
                        amount = (percentage / 100.0) * totalCredit;
                    } else {
                        amount = Double.parseDouble(value);
                    }

                    totalDebt += amount;

                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

// When a new participant is checked or some value is changed, the screen is updated to reflect the changes
    private void updateScreen() {
        //Checks for new creditors added to add an input for their amount
        for (int i = 0; i < creditorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) creditorsContainer.getChildAt(i);
            CheckBox check = (CheckBox) row.getChildAt(0);

            if (check.isChecked()) {
                EditText previous = null;
                int selection = 0;
                boolean hadFocus = false;

                if (row.getChildCount() > 1 && row.getChildAt(1) instanceof EditText) {
                    previous = (EditText) row.getChildAt(1);
                    hadFocus = previous.hasFocus();
                    selection = previous.getSelectionEnd();
                    row.removeViewAt(1);
                }

                EditText amountInput = new EditText(this);
                amountInput.setHint(R.string.creditor_hint);
                amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

                if (previous != null && !previous.getText().toString().isEmpty()) {
                    amountInput.setText(previous.getText().toString());
                }

                if (hadFocus) {
                    amountInput.requestFocus();
                    amountInput.setSelection(Math.min(selection, amountInput.getText().length()));
                }

                amountInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        String input = s.toString();
                        checkNumber(input,amountInput,this);

                        updateScreen();
                        getTotalCredit();
                        creditLabel.setText(String.format(getText(R.string.creditors_label) + "%.2f%s", totalCredit, group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.creditors_hint)));
                    }
                });

                if (row.getChildCount() > 1) row.removeViewAt(1);
                row.addView(amountInput);
            } else {
                if (row.getChildCount() > 1) row.removeViewAt(1);
            }

        }

        //Updates the total credit
        getTotalCredit();
        creditLabel.setText(String.format(getText(R.string.creditors_label) + "%.2f%s", totalCredit, group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.creditors_hint)));

        //Checks for new debtors added to add an input for their amount
        for (int i = 0; i < debtorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) debtorsContainer.getChildAt(i);
            CheckBox check = (CheckBox) row.getChildAt(0);

            if (check.isChecked()) {
                EditText input = new EditText(this);
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                //Makes all the debtors have the same amount
                if (distributionType == "equal") {
                    input.setEnabled(false);
                    int selected = countDebtors();
                    double share = selected > 0 ? totalCredit / selected : 0;
                    share = Math.round(share*100)/100.0;
                    input.setText(String.valueOf(share));
                } else {
                    //Makes it count percentage or amount depending on the distribution type
                    if (row.getChildAt(1) != null) {
                        String value = ((EditText) row.getChildAt(1)).getText().toString();
                        if (!value.isEmpty()) {
                            Double debt = Double.parseDouble(value);
                            if (Objects.equals(previousDistributionType, "percentage")) {
                                debt = (debt / 100.0) * totalCredit;
                            }
                            input.setText((Objects.equals(distributionType, "percentage")) ? (debt > 0 ? String.valueOf((double) Math.round((debt / totalCredit) * 10000) / 100) : "") : (debt > 0 ? String.valueOf((double) Math.round(debt * 100) / 100) : ""));
                        }
                    }
                    input.setHint((!Objects.equals(distributionType, "percentage")) ? getString(R.string.amount_hint) : getString(R.string.amount_hint)+" %");
                    input.setEnabled(true);
                    input.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {}

                        @Override
                        public void afterTextChanged(Editable s) {
                            String auxInput = s.toString();

                            checkNumber(auxInput,input,this);
                            //Sets the total debt
                            getTotalDebt();
                            switch (distributionType) {
                                case "equal":
                                    debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalCredit , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.equal_hint)));
                                    break;
                                case "percentage":
                                    debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalDebt , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.percentage_hint)));
                                    break;
                                case "unequal":
                                    debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalDebt , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.unequal_hint)));
                                    break;
                            }
                        }
                    });
                }
                //Resets the debtors input with the new info
                if (row.getChildCount() > 1) row.removeViewAt(1);
                row.addView(input);
            }else{
                if (row.getChildCount() > 1) row.removeViewAt(1);
            }
        }
        //Set the previous distribution type for adding correctly the amounts in getTotalDebt
        previousDistributionType = distributionType;
        //Updates the screen depending on the distribution type
        switch (distributionType) {
            case "equal":
                debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalCredit , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.equal_hint)));
                break;
            case "percentage":
                debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalDebt , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.percentage_hint)));
                break;
            case "unequal":
                debtLabel.setText(String.format(getText(R.string.debtors_label) + "%.2f%s", totalDebt , group.getCurrency().getSymbol(group.getCurrency())+" " + getText(R.string.unequal_hint)));
                break;
        }
    }
    //Counts the debtors checked to know how to divide the debt
    private int countDebtors() {
        int count = 0;
        for (int i = 0; i < debtorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) debtorsContainer.getChildAt(i);
            CheckBox check = (CheckBox) row.getChildAt(0);
            if (check.isChecked()) count++;
        }
        return count;
    }
    // Gets the inputs and adds it to a expense
    private void saveExpense() {
        String expenseName = expenseNameInput.getText().toString();
        if (expenseName.isEmpty()) {
            Toast.makeText(this, R.string.expense_name_warning, Toast.LENGTH_SHORT).show();
            return;
        }

        creditors.clear();
        for (int i = 0; i < creditorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) creditorsContainer.getChildAt(i);
            CheckBox check = (CheckBox) row.getChildAt(0);
            EditText input = (EditText) row.getChildAt(1);

            if (check.isChecked()) {
                String value = input.getText().toString().replace(",", ".");
                if (!value.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(value);
                        User user = group.getParticipants().get(i);
                        creditors.put(user, amount);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        debtors.clear();
        int receiverCount = 0;
        for (int i = 0; i < debtorsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) debtorsContainer.getChildAt(i);
            CheckBox check = (CheckBox) row.getChildAt(0);
            if (check.isChecked() && row.getChildCount() > 1) {
                EditText input = (EditText) row.getChildAt(1);
                String value = input.getText().toString();
                value = value.replace(",", ".");
                if (!value.isEmpty()) {
                    double amount = Double.parseDouble(value);
                    if (Objects.equals(distributionType, "percentage")) {
                        amount = (amount / 100.0) * totalCredit;
                    }

                    User user = group.getParticipants().get(i);
                    debtors.put(user, amount);
                    receiverCount++;
                }
            }
        }
        //Checks there is at least one creditor and one debtor
        if (creditors.isEmpty() || receiverCount == 0) {
            Toast.makeText(this, R.string.creditors_debtors_warning, Toast.LENGTH_LONG).show();
            return;
        }
        // Checks if the amounts add up correctly
        boolean valid =
                (Objects.equals(distributionType, "equal") && totalCredit != 0) ||
                (Math.abs(totalCredit - totalDebt) < 0.01 && totalCredit != 0);

        if (!valid) {
            if (Objects.equals(distributionType, "equal")){
                Toast.makeText(this, R.string.equal_valid, Toast.LENGTH_LONG).show();
                return;
            } else if (Objects.equals(distributionType, "percentage")) {
                Toast.makeText(this, R.string.unequal_valid, Toast.LENGTH_LONG).show();
                return;
            } else if (Objects.equals(distributionType, "unequal")) {
                Toast.makeText(this, R.string.percentage_valid, Toast.LENGTH_LONG).show();
                return;
            }
        }


        expense.setAmount(totalCredit);
        expense.setCreditors(creditors);
        expense.setDebtors(debtors);

        Set<User> participantsSet = new HashSet<>(debtors.keySet());
        participantsSet.addAll(creditors.keySet());
        ArrayList<User> participants = new ArrayList<>(participantsSet);
        expense.setParticipants(participants);
        //Checks if the name is already in use
        Boolean validName = true;
        for(Expense e : group.getExpenses()){
            validName = !e.getName().equals(expenseName)|| expense.getName().equals(e.getName());
            if(!validName) break;
        }
        expense.setName(expenseName);
        if(!expense.getName().isEmpty()) {
            if(validName) {
                Intent result = new Intent();
                result.putExtra("result_type", "EXPENSE_RESULT");
                result.putExtra("expense", expense);
                setResult(RESULT_OK, result);
                finish();
            }else{
                Toast.makeText(this, R.string.expense_name_in_use, Toast.LENGTH_LONG).show();
            }
        }else{
            Toast.makeText(this, R.string.expense_name_missing, Toast.LENGTH_LONG).show();
        }
    }
    // Checks if the string provided is a valid number
    private void checkNumber(String input, EditText amountInput, TextWatcher textWatcher) {
        if (input.matches("\\.\\d{0,2}")) {
            amountInput.removeTextChangedListener(textWatcher);
            amountInput.setText(String.format("0%s", input.substring(0)));
            amountInput.setSelection(input.length());
            amountInput.addTextChangedListener(textWatcher);
        }else if (!input.matches("^\\d{0,5}+(\\.\\d{0,2})?$")) {
            amountInput.removeTextChangedListener(textWatcher);

            if (input.contains(".")) {
                int index = input.indexOf(".");
                if (index + 3 <= input.length()) {
                    input = input.substring(0, index + 3);
                }
                if (index > 6) {
                    input = input.substring(0, 5)+input.substring(index);
                }
            }else if (input.length() > 5) {
                input = input.substring(0, 5);
            }

            amountInput.setText(input);
            amountInput.setSelection(input.length());
            amountInput.addTextChangedListener(textWatcher);
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
