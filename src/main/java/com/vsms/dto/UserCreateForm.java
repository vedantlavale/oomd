package com.vsms.dto;

import com.vsms.enums.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserCreateForm {

    @NotBlank(message = "Enter a username.")
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,30}$",
            message = "Use 3 to 30 letters, digits, dots, hyphens or underscores.")
    private String username;

    @NotBlank(message = "Enter the person's full name.")
    @Size(max = 100, message = "Name can have at most 100 characters.")
    private String fullName;

    @NotNull(message = "Choose a role.")
    private Role role = Role.SERVICE_ADVISOR;

    /** Required when the role is MECHANIC. */
    private Long mechanicId;

    @NotBlank(message = "Enter a password.")
    @Size(max = 72, message = "Password can have at most 72 characters.")
    private String password;

    @NotBlank(message = "Repeat the password.")
    private String confirmPassword;

    @AssertTrue(message = "The two passwords do not match.")
    public boolean isPasswordsMatch() {
        return password == null || password.equals(confirmPassword);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getMechanicId() { return mechanicId; }
    public void setMechanicId(Long mechanicId) { this.mechanicId = mechanicId; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
