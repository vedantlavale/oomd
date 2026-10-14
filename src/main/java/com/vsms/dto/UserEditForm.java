package com.vsms.dto;

import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserEditForm {

    @NotBlank(message = "Enter the person's full name.")
    @Size(max = 100, message = "Name can have at most 100 characters.")
    private String fullName;

    @NotNull(message = "Choose a role.")
    private Role role;

    private Long mechanicId;

    private boolean enabled = true;

    public static UserEditForm from(UserAccount account) {
        UserEditForm form = new UserEditForm();
        form.fullName = account.getFullName();
        form.role = account.getRole();
        form.enabled = account.isEnabled();
        form.mechanicId = account.getMechanic() == null ? null : account.getMechanic().getId();
        return form;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Long getMechanicId() { return mechanicId; }
    public void setMechanicId(Long mechanicId) { this.mechanicId = mechanicId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
