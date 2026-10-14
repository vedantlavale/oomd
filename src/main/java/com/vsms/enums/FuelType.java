package com.vsms.enums;

public enum FuelType {
    PETROL("Petrol"),
    DIESEL("Diesel"),
    CNG("CNG"),
    ELECTRIC("Electric"),
    HYBRID("Hybrid");

    private final String label;

    FuelType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
