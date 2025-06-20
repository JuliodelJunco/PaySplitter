package com.example.paySplitter.Model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
// Saves the payers, the debtors and the amounts of the expense
public class Expense implements Serializable {
    private String name = "";
    private double amount= 0.00;
    private Map<User, Double> creditors = new HashMap<>();
    private Map<User, Double> debtors = new HashMap<>();
    private ArrayList<User> participants = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Expense expense = (Expense) o;

        return Double.compare(expense.amount, amount) == 0 &&
                Objects.equals(name, expense.name) &&
                Objects.equals(participants, expense.participants) &&
                Objects.equals(creditors, expense.creditors) &&
                Objects.equals(debtors, expense.debtors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, amount, participants, creditors, debtors);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<User, Double> getCreditors() {
        return creditors;
    }

    public void setCreditors(Map<User, Double> creditors) {
        this.creditors = creditors;
    }

    public Map<User, Double> getDebtors() {
        return debtors;
    }

    public void setDebtors(Map<User, Double> debtors) {
        this.debtors = debtors;
    }

    public ArrayList<User> getParticipants() {
        return participants;
    }

    public void setParticipants(ArrayList<User> participants) {
        this.participants = participants;
    }
    //Flips the expense for when it is deleted
    public void flipExpense() {
        Map<User, Double> debtors = this.getDebtors();
        this.setDebtors(this.getCreditors());
        this.setCreditors(debtors);
    }

    public void setAmount(Double amount){
        this.amount = amount;
    }
    public Double getAmount() {
        return  amount;
    }
}
