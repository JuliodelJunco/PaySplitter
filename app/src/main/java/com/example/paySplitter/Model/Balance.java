package com.example.paySplitter.Model;

import java.io.Serializable;
// This class shows which users owe money and which users are in debt
public class Balance implements Serializable {

    private User user;
    private boolean inDebt = false;
    private double amount = 0.00;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
    public boolean isInDebt() {
        return inDebt;
    }

    public void setInDebt(boolean inDebt) {
        this.inDebt = inDebt;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
