package com.vsms.security;

import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security's view of a {@link UserAccount}. */
public class VsmsUserDetails extends User {

    private final Long userId;
    private final String fullName;
    private final Role role;
    private final Long mechanicId;

    public VsmsUserDetails(UserAccount account) {
        super(account.getUsername(), account.getPasswordHash(),
                // A deactivated mechanic cannot sign in even if the account itself is enabled.
                account.isEnabled() && (account.getMechanic() == null || account.getMechanic().isActive()),
                true, true, true,
                List.of(new SimpleGrantedAuthority(account.getRole().getAuthority())));
        this.userId = account.getId();
        this.fullName = account.getFullName();
        this.role = account.getRole();
        this.mechanicId = account.getMechanic() == null ? null : account.getMechanic().getId();
    }

    public Actor toActor() {
        return new Actor(userId, getUsername(), fullName, role, mechanicId);
    }
}
