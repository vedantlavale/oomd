package com.vsms.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used by a signed-in user to change their own password. */
public class ChangePasswordForm {

    @NotBlank(message = "Enter your current password.")
    private String currentPassword;

    @NotBlank(message = "Enter a new password.")
    @Size(max = 72, message = "Password can have at most 72 characters.")
    private String newPassword;

    @NotBlank(message = "Repeat the new password.")
    private String confirmPassword;

    @AssertTrue(message = "The two passwords do not match.")
    public boolean isPasswordsMatch() {
        return newPassword == null || newPassword.equals(confirmPassword);
    }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
