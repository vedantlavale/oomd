package com.vsms.enums;

public enum Role {
    ADMIN("Administrator"),
    SERVICE_ADVISOR("Service Advisor"),
    MECHANIC("Mechanic");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Name used by Spring Security, e.g. ROLE_ADMIN. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
