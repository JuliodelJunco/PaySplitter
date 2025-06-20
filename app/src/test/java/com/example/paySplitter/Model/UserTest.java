package com.example.paySplitter.Model;

import static org.junit.Assert.*;


import org.junit.Test;

public class UserTest {

    @Test
    public void testEquals_sameGmail_returnsTrue() {
        User u1 = new User();
        u1.setGmail("user@example.com");

        User u2 = new User();
        u2.setGmail("user@example.com");

        assertEquals(u1, u2);
    }

    @Test
    public void testEquals_differentGmail_returnsFalse() {
        User u1 = new User();
        u1.setGmail("user1@example.com");

        User u2 = new User();
        u2.setGmail("user2@example.com");

        assertNotEquals(u1, u2);
    }

    @Test
    public void testHashCode_equalObjects_sameHash() {
        User u1 = new User();
        u1.setGmail("user@example.com");

        User u2 = new User();
        u2.setGmail("user@example.com");

        assertEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    public void testHashCode_differentObjects_differentHash() {
        User u1 = new User();
        u1.setGmail("user1@example.com");

        User u2 = new User();
        u2.setGmail("user2@example.com");

        assertNotEquals(u1.hashCode(), u2.hashCode());
    }
}
