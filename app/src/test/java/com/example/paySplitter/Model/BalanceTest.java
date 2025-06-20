package com.example.paySplitter.Model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BalanceTest {

    private Balance balance;

    @Before
    public void setUp() {
        balance = new Balance();
    }

    @Test
    public void testInitialState() {
        assertEquals(0.0, balance.getAmount(), 0.001);
        assertFalse(balance.isInDebt());
        assertNull(balance.getUser());
    }

    @Test
    public void testSetAndGetUser() {
        User user = new User();
        user.setName("John");
        user.setGmail("john@example.com");

        balance.setUser(user);

        assertNotNull(balance.getUser());
        assertEquals("John", balance.getUser().getName());
        assertEquals("john@example.com", balance.getUser().getGmail());
    }

    @Test
    public void testSetAndGetAmount() {
        balance.setAmount(25.75);
        assertEquals(25.75, balance.getAmount(), 0.001);
    }

    @Test
    public void testSetAndGetInDebt() {
        balance.setInDebt(true);
        assertTrue(balance.isInDebt());

        balance.setInDebt(false);
        assertFalse(balance.isInDebt());
    }
}

