package com.example.paySplitter.Model;

import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ExpenseTest {

    private Expense expense;
    private User user1;
    private User user2;

    @Before
    public void setUp() {
        expense = new Expense();

        user1 = new User();
        user1.setName("Alice");
        user1.setGmail("alice@example.com");

        user2 = new User();
        user2.setName("Bob");
        user2.setGmail("bob@example.com");

        expense.setName("Dinner");
        expense.setAmount(100.0);

        Map<User, Double> creditors = new HashMap<>();
        creditors.put(user1, 70.0);
        expense.setCreditors(creditors);

        Map<User, Double> debtors = new HashMap<>();
        debtors.put(user2, 100.0);
        expense.setDebtors(debtors);

        ArrayList<User> participants = new ArrayList<>();
        participants.add(user1);
        participants.add(user2);
        expense.setParticipants(participants);
    }

    @Test
    public void testGettersAndSetters() {
        assertEquals("Dinner", expense.getName());
        assertEquals(100.0, expense.getAmount(), 0.001);
        assertEquals(1, expense.getCreditors().size());
        assertEquals(1, expense.getDebtors().size());
        assertEquals(2, expense.getParticipants().size());
    }

    @Test
    public void testEqualsAndHashCode() {
        Expense same = new Expense();
        same.setName("Dinner");
        same.setAmount(100.0);
        same.setCreditors(new HashMap<>(expense.getCreditors()));
        same.setDebtors(new HashMap<>(expense.getDebtors()));
        same.setParticipants(new ArrayList<>(expense.getParticipants()));

        assertEquals(expense, same);
        assertEquals(expense.hashCode(), same.hashCode());
    }

    @Test
    public void testFlipExpense() {
        Map<User, Double> oldCreditors = new HashMap<>(expense.getCreditors());
        Map<User, Double> oldDebtors = new HashMap<>(expense.getDebtors());

        expense.flipExpense();

        assertEquals(oldCreditors, expense.getDebtors());
        assertEquals(oldDebtors, expense.getCreditors());
    }
}
