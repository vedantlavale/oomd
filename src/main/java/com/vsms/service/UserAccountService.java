package com.vsms.service;

import com.vsms.dto.ChangePasswordForm;
import com.vsms.dto.PasswordResetForm;
import com.vsms.dto.UserCreateForm;
import com.vsms.dto.UserEditForm;
import com.vsms.entity.Mechanic;
import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import com.vsms.exception.BusinessRuleException;
import com.vsms.exception.ResourceNotFoundException;
import com.vsms.repository.MechanicRepository;
import com.vsms.repository.UserAccountRepository;
import com.vsms.security.Actor;
import com.vsms.util.PasswordPolicy;
import com.vsms.util.Texts;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserAccountService {

    private final UserAccountRepository users;
    private final MechanicRepository mechanics;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository users, MechanicRepository mechanics,
                              PasswordEncoder passwordEncoder) {
        this.users = users;
        this.mechanics = mechanics;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> findAll() {
        return users.findAllByOrderByUsername();
    }

    @Transactional(readOnly = true)
    public UserAccount get(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public UserAccount create(UserCreateForm form) {
        String username = normalizeUsername(form.getUsername());
        requirePasswordPolicy("password", form.getPassword());
        if (users.existsByUsername(username)) {
            throw new BusinessRuleException("username", "That username is already taken.");
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setFullName(Texts.clean(form.getFullName()));
        account.setRole(form.getRole());
        account.setMechanic(resolveMechanic(form.getRole(), form.getMechanicId(), null));
        account.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        return save(account);
    }

    /** Used by start-up code that already knows the values are valid. */
    public UserAccount createDirect(String username, String fullName, Role role, Mechanic mechanic,
                                    String rawPassword) {
        String normalized = normalizeUsername(username);
        requirePasswordPolicy("password", rawPassword);
        if (users.existsByUsername(normalized)) {
            throw new BusinessRuleException("username", "That username is already taken.");
        }
        UserAccount account = new UserAccount();
        account.setUsername(normalized);
        account.setFullName(fullName);
        account.setRole(role);
        account.setMechanic(mechanic);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        return save(account);
    }

    public UserAccount update(Long id, UserEditForm form, Actor actor) {
        UserAccount account = get(id);
        boolean editingSelf = actor != null && id.equals(actor.userId());
        if (editingSelf && (form.getRole() != account.getRole() || !form.isEnabled())) {
            throw new BusinessRuleException("You cannot change your own role or disable your own account.");
        }
        boolean losesAdmin = account.getRole() == Role.ADMIN && account.isEnabled()
                && (form.getRole() != Role.ADMIN || !form.isEnabled());
        if (losesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("There must always be at least one active administrator.");
        }
        account.setFullName(Texts.clean(form.getFullName()));
        account.setRole(form.getRole());
        account.setEnabled(form.isEnabled());
        account.setMechanic(resolveMechanic(form.getRole(), form.getMechanicId(), account));
        return save(account);
    }

    public void resetPassword(Long id, PasswordResetForm form) {
        UserAccount account = get(id);
        requirePasswordPolicy("newPassword", form.getNewPassword());
        account.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
    }

    /** Sets a password without a form (used by the administrator recovery option at start-up). */
    public void setPassword(UserAccount account, String rawPassword) {
        requirePasswordPolicy("password", rawPassword);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        users.save(account);
    }

    public void changeOwnPassword(Actor actor, ChangePasswordForm form) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessRuleException("Your account could not be found.");
        }
        UserAccount account = get(actor.userId());
        if (!passwordEncoder.matches(form.getCurrentPassword(), account.getPasswordHash())) {
            throw new BusinessRuleException("currentPassword", "The current password is not correct.");
        }
        requirePasswordPolicy("newPassword", form.getNewPassword());
        account.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
    }

    private UserAccount save(UserAccount account) {
        try {
            return users.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("username", "That username (or mechanic link) is already in use.");
        }
    }

    private Mechanic resolveMechanic(Role role, Long mechanicId, UserAccount existing) {
        if (role != Role.MECHANIC) {
            return null;
        }
        if (mechanicId == null) {
            throw new BusinessRuleException("mechanicId", "Choose which mechanic this account belongs to.");
        }
        Mechanic mechanic = mechanics.findById(mechanicId)
                .orElseThrow(() -> new BusinessRuleException("mechanicId", "That mechanic does not exist."));
        boolean sameAsBefore = existing != null && existing.getMechanic() != null
                && existing.getMechanic().getId().equals(mechanicId);
        if (!sameAsBefore) {
            if (!mechanic.isActive()) {
                throw new BusinessRuleException("mechanicId", "That mechanic is deactivated.");
            }
            if (users.existsByMechanicId(mechanicId)) {
                throw new BusinessRuleException("mechanicId", "That mechanic already has a login account.");
            }
        }
        return mechanic;
    }

    private static void requirePasswordPolicy(String field, String password) {
        String problem = PasswordPolicy.check(password);
        if (problem != null) {
            throw new BusinessRuleException(field, problem);
        }
    }

    private static String normalizeUsername(String username) {
        String cleaned = Texts.trimToNull(username);
        if (cleaned == null || !cleaned.matches("^[A-Za-z0-9._-]{3,30}$")) {
            throw new BusinessRuleException("username", "Use 3 to 30 letters, digits, dots, hyphens or underscores.");
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }
}
