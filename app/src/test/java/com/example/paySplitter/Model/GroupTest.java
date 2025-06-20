package com.example.paySplitter.Model;

import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class GroupTest {

    private Group group;
    private User alice;
    private User bob;
    private User carol;

    @Before
    public void setup() {
        alice = new User();
        alice.setName("Alice");
        alice.setGmail("alice@example.com");

        bob = new User();
        bob.setName("Bob");
        bob.setGmail("bob@example.com");

        carol = new User();
        carol.setName("Carol");
        carol.setGmail("carol@example.com");

        ArrayList<User> participants = new ArrayList<>(Arrays.asList(alice, bob, carol));
        group = new Group("Trip", Currency.EURO, participants);
    }

    @Test
    public void testAddExpenseAndUpdateDebts() {
        Expense expense = new Expense();
        expense.setName("Hotel");
        expense.setAmount(90.0);
        expense.setParticipants(new ArrayList<>(Arrays.asList(alice, bob, carol)));

        Map<User, Double> creditors = new HashMap<>();
        creditors.put(alice, 90.0);
        expense.setCreditors(creditors);

        Map<User, Double> debtors = new HashMap<>();
        debtors.put(bob, 45.0);
        debtors.put(carol, 45.0);
        expense.setDebtors(debtors);

        group.setExpenses(new ArrayList<>(Collections.singletonList(expense)));
        group.updateDebts(expense);

        assertEquals(2, group.getDebts().size());

        for (Debt debt : group.getDebts()) {
            assertEquals(alice, debt.getCreditor());
            assertEquals(45.0, debt.getAmount(), 0.0);
        }
    }

    @Test
    public void testSetParticipantsRemoveUser() {
        Expense expense = new Expense();
        expense.setName("Dinner");
        expense.setAmount(60.0);
        expense.setParticipants(new ArrayList<>(Arrays.asList(alice, bob)));

        Map<User, Double> creditors = new HashMap<>();
        creditors.put(alice, 60.0);
        expense.setCreditors(creditors);

        Map<User, Double> debtors = new HashMap<>();
        debtors.put(bob, 60.0);
        expense.setDebtors(debtors);

        group.setExpenses(new ArrayList<>(Collections.singletonList(expense)));
        group.updateDebts(expense);

        ArrayList<User> newParticipants = new ArrayList<>(Collections.singletonList(alice));
        group.setParticipants(newParticipants);

        assertFalse(group.getBalances().containsKey(bob));
        assertTrue(group.getExpenses().isEmpty());
        assertTrue(group.getDebts().isEmpty());
    }

    @Test
    public void testInitializeBalances() {
        group.setParticipants(new ArrayList<>(Arrays.asList(alice, bob)));
        group.updateDebts(new Expense());  // Should initialize balances

        assertTrue(group.getBalances().containsKey(alice));
        assertTrue(group.getBalances().containsKey(bob));
    }

    @Test
    public void testGettersSetters() {
        group.setName("Beach Trip");
        assertEquals("Beach Trip", group.getName());

        group.setCurrency(Currency.DOLLAR);
        assertEquals(Currency.DOLLAR, group.getCurrency());

        group.setId("group123");
        assertEquals("group123", group.getId());

        ArrayList<Expense> expenses = new ArrayList<>();
        group.setExpenses(expenses);
        assertSame(expenses, group.getExpenses());
    }

}
