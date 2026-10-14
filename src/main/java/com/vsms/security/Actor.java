package com.vsms.security;

import com.vsms.enums.Role;

/** The signed-in person performing an action; passed to services so they can check permissions. */
public record Actor(Long userId, String username, String fullName, Role role, Long mechanicId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isMechanic() {
        return role == Role.MECHANIC;
    }

    /** Admin or service advisor. */
    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.SERVICE_ADVISOR;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public Long getMechanicId() {
        return mechanicId;
    }

    /** For tests and background jobs. */
    public static Actor system() {
        return new Actor(null, "system", "System", Role.ADMIN, null);
    }
}
