package com.example.paySplitter.Model;
// This enum shows the different currencies available
public enum Currency {
    EURO,DOLLAR,POUND,PESO,YUAN;
// This method returns the symbol of the currency
    public String getSymbol(Currency currency) {
        switch (currency){
            case EURO: return "€";
            case YUAN: return "¥";
            case POUND: return "£";
            default: return "$";
        }
    }
}
