package com.vsms.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used by an administrator to set a new password for someone else. */
public class PasswordResetForm {

    @NotBlank(message = "Enter a new password.")
    @Size(max = 72, message = "Password can have at most 72 characters.")
    private String newPassword;

    @NotBlank(message = "Repeat the new password.")
    private String confirmPassword;

    @AssertTrue(message = "The two passwords do not match.")
    public boolean isPasswordsMatch() {
        return newPassword == null || newPassword.equals(confirmPassword);
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
