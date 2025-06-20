package com.example.paySplitter.Model;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DebtTest {

    private User debtor;
    private User creditor;
    private Debt debt;

    @Before
    public void setUp() {
        debtor = new User();
        debtor.setName("Alice");
        debtor.setGmail("alice@example.com");

        creditor = new User();
        creditor.setName("Bob");
        creditor.setGmail("bob@example.com");

        debt = new Debt(creditor, debtor, 50.0);
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals(creditor, debt.getCreditor());
        assertEquals(debtor, debt.getDebtor());
        assertEquals(50.0, debt.getAmount(), 0.001);
    }

    @Test
    public void testSetters() {
        User newDebtor = new User();
        newDebtor.setName("Charlie");
        newDebtor.setGmail("charlie@example.com");
        debt.setDebtor(newDebtor);
        assertEquals(newDebtor, debt.getDebtor());

        User newCreditor = new User();
        newCreditor.setName("Dana");
        newCreditor.setGmail("dana@example.com");
        debt.setCreditor(newCreditor);
        assertEquals(newCreditor, debt.getCreditor());

        debt.setAmount(75.5);
        assertEquals(75.5, debt.getAmount(), 0.001);
    }

}