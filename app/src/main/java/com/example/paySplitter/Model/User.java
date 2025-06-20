package com.example.paySplitter.Model;

import java.io.Serializable;
import java.util.Objects;
// Saves the users name and gmail from the API
public class User implements Serializable {
    private String name;
    private String gmail;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGmail() {
        return gmail;
    }

    public void setGmail(String gmail) {
        this.gmail = gmail;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User other = (User) obj;
        return gmail != null && gmail.equals(other.gmail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gmail);
    }
}
