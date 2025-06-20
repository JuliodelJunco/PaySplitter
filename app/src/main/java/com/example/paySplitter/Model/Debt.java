package com.example.paySplitter.Model;

import java.io.Serializable;
// This class shows which users owe money and to whom they owe money
public class Debt implements Serializable {
    private User debtor;
    private User creditor;
    private double amount;
    public Debt(){}

    public Debt(User creditor, User debtor, double amount) {
        this.creditor = creditor;
        this.debtor = debtor;
        this.amount = amount;
    }

    public User getDebtor() {
        return debtor;
    }

    public void setDebtor(User debtor) {
        this.debtor = debtor;
    }

    public User getCreditor() {
        return creditor;
    }

    public void setCreditor(User creditor) {
        this.creditor = creditor;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
