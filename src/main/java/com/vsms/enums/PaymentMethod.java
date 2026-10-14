package com.vsms.enums;

public enum PaymentMethod {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card"),
    OTHER("Other");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
