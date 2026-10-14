package com.vsms.enums;

/** State of a single payment record. Only SUCCESSFUL payments count towards revenue. */
public enum PaymentRecordStatus {
    SUCCESSFUL("Successful", "status-paid"),
    REVERSED("Reversed", "status-cancelled");

    private final String label;
    private final String badgeClass;

    PaymentRecordStatus(String label, String badgeClass) {
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
