package com.vsms.security;

import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import com.vsms.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds out who is signed in. Works for real logins and for the simple test users created by
 * {@code @WithMockUser}, by falling back to a database lookup by user name.
 */
@Component
public class ActorResolver {

    private final UserAccountRepository users;

    public ActorResolver(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Actor resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication.getPrincipal() instanceof VsmsUserDetails details) {
            return details.toActor();
        }
        String username = authentication.getName().toLowerCase(Locale.ROOT);
        return users.findByUsername(username)
                .map(this::toActor)
                .orElseGet(() -> fromAuthorities(authentication, username));
    }

    private Actor toActor(UserAccount account) {
        return new Actor(account.getId(), account.getUsername(), account.getFullName(), account.getRole(),
                account.getMechanic() == null ? null : account.getMechanic().getId());
    }

    private Actor fromAuthorities(Authentication authentication, String username) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (Role role : Role.values()) {
                if (role.getAuthority().equals(authority.getAuthority())) {
                    return new Actor(null, username, username, role, null);
                }
            }
        }
        return null;
    }
}
