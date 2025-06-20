package com.example.paySplitter.Model;

import static java.lang.Math.abs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
// This class has all the users tha participate in the group, the expenses and the debts
public class Group implements Serializable {

    private String id;
    private String name = "";
    private Currency currency = Currency.valueOf("EURO");
    private ArrayList<Debt> debts = new ArrayList<>();
    private ArrayList<User> participants = new ArrayList<>();
    private ArrayList<Expense> expenses = new ArrayList<>();
    private Map<User, Balance> balances = new HashMap<User,Balance>();

    public Group(){
    }
    public Group(String name, Currency currency, ArrayList<User> participants){
        this.name = name;
        this.currency = currency;
        this.participants = participants;
        this.debts = new ArrayList<>();
        this.expenses = new ArrayList<>();
        initializeBalances();
    }
    //When an expense is added, the debts are updated
    public void updateDebts(Expense expense){
        //Updates the balances with the new expense
        initializeBalances();
        ArrayList<User> expenseParticipants = expense.getParticipants();
        Map<User,Double> creditors = expense.getCreditors();
        Map<User,Double> debtors = expense.getDebtors();
        Set<User> payers = new HashSet<>(expenseParticipants);
        for(User participant : expenseParticipants ){
            Balance updateBalance = balances.get(participant);
            double creditorAmount = creditors.getOrDefault(participant,0.00);
            double debtorAmount = debtors.getOrDefault(participant,0.00);
            double balanceAmount = updateBalance.getAmount();
            //If the balance is in debt, the amount is negative
            if (updateBalance.isInDebt()){
                balanceAmount = balanceAmount * -1;
            }
            //Set the new amount and if it is in debt
            double newAmount = balanceAmount + creditorAmount - debtorAmount;
            updateBalance.setInDebt(newAmount < 0.00);
            updateBalance.setAmount(abs(newAmount));
            balances.put(participant, updateBalance);
            if (newAmount == 0.00 ){
                payers.remove(participant);
            }
        }
        ArrayList<Balance> balancesArray = new ArrayList<>(balances.values());
        ArrayList<Debt> debtsList = new ArrayList<>();
        while(!payers.isEmpty()){
            //Gets the opposing balances and creates a debt
            ArrayList<Balance> pair = getOpposingBalances(balancesArray,payers);
            Balance balance1 = pair.get(0);
            Balance balance2 = pair.get(1);
            Debt debt = new Debt();
            //Creates the debt with the minimum amount and deletes the user from the missing debts
            if (balance2.getAmount()<balance1.getAmount()){
                if (balance2.isInDebt()){
                    debt.setCreditor(balance1.getUser());
                    debt.setDebtor(balance2.getUser());
                    debt.setAmount(balance2.getAmount());
                }else{
                    debt.setCreditor(balance2.getUser());
                    debt.setDebtor(balance1.getUser());
                    debt.setAmount(balance2.getAmount());
                }
                balance1.setAmount(balance1.getAmount()- balance2.getAmount());
                debtsList.add(debt);
                payers.remove(balance2.getUser());
            }else if (balance2.getAmount()>balance1.getAmount()){
                if (balance2.isInDebt()){
                    debt.setCreditor(balance1.getUser());
                    debt.setDebtor(balance2.getUser());
                    debt.setAmount(balance1.getAmount());
                }else{
                    debt.setCreditor(balance2.getUser());
                    debt.setDebtor(balance1.getUser());
                    debt.setAmount(balance1.getAmount());
                }
                balance2.setAmount(balance2.getAmount()- balance1.getAmount());
                debtsList.add(debt);
                payers.remove(balance1.getUser());
            } else{
                if (balance2.isInDebt()){
                    debt.setCreditor(balance1.getUser());
                    debt.setDebtor(balance2.getUser());
                    debt.setAmount(balance1.getAmount());
                }else{
                    debt.setCreditor(balance2.getUser());
                    debt.setDebtor(balance1.getUser());
                    debt.setAmount(balance1.getAmount());
                }
                debtsList.add(debt);
                payers.remove(balance1.getUser());
                payers.remove(balance2.getUser());
            }
        }
        debts = debtsList;
    }
    //Gets two users with opposing balances
    private ArrayList<Balance> getOpposingBalances(ArrayList<Balance> balancesArray, Set<User> payers){
        ArrayList<Balance> pair = new ArrayList<>();
        int i = 0;
        while (pair.size()<2){
            Balance balance = balancesArray.get(i);
            if (payers.contains(balance.getUser())){
                pair.add(balance);
                boolean inDebt = balance.isInDebt();
                int j = i+1;
                while(pair.size()<2){
                    Balance balance2 = balancesArray.get(j);
                    if (payers.contains(balance2.getUser()) && balance2.isInDebt()!=inDebt) {
                        pair.add(balance2);
                        break;
                    }
                    j = j+1;
                }

            }
            i = i + 1;
        }
        return pair;
    }
    //Adds new balances if new
    private void initializeBalances(){
        for (User participant : participants){
            if (!balances.containsKey(participant)){
                Balance balance = new Balance();
                balance.setUser(participant);
                balances.put(participant, balance);
            }
        }
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public ArrayList<Debt> getDebts() {
        return debts;
    }

    public void setDebts(ArrayList<Debt> debts) {
        this.debts = debts;
    }

    public ArrayList<User> getParticipants() {
        return participants;
    }
    // Checks the participants size and if less deletes the balances, debts, and expenses of the deleted users
    public void setParticipants(ArrayList<User> participants) {
        if (participants.size()<this.participants.size()){
            Set<User> newParticipants = new HashSet<>(participants);
            Set<User> oldParticipants = new HashSet<>(this.participants);
            oldParticipants.removeAll(newParticipants);
            for(User participant : oldParticipants) {
                Iterator<Expense> expenseIterator = expenses.iterator();
                while (expenseIterator.hasNext()) {
                    Expense expense = expenseIterator.next();
                    if (expense.getParticipants().contains(participant)) {
                        expense.flipExpense();
                        updateDebts(expense);
                        expenseIterator.remove();
                    }
                }

                Iterator<Debt> debtIterator = debts.iterator();
                while (debtIterator.hasNext()) {
                    Debt debt = debtIterator.next();
                    if (debt.getDebtor().equals(participant) || debt.getCreditor().equals(participant)) {
                        debtIterator.remove();
                    }
                }
                balances.remove(participant);
            }
            this.participants = participants;
        }else {
            this.participants = participants;
        }
    }


    public ArrayList<Expense> getExpenses() {
        return expenses;
    }

    public void setExpenses(ArrayList<Expense> expenses) {
        this.expenses = expenses;
    }

    public Map<User, Balance> getBalances() {
        return balances;
    }

    public void setBalances(Map<User, Balance> balances) {
        this.balances = balances;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
}
