package com.vsms.enums;

/** Payment state of an invoice. */
public enum PaymentStatus {
    UNPAID("Unpaid", "status-unpaid"),
    PARTIALLY_PAID("Partially paid", "status-partial"),
    PAID("Paid", "status-paid");

    private final String label;
    private final String badgeClass;

    PaymentStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
