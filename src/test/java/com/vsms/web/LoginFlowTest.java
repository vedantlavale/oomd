package com.vsms.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.vsms.AbstractIntegrationTest;
import com.vsms.dto.UserCreateForm;
import com.vsms.entity.Mechanic;
import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import com.vsms.service.UserAccountService;
import com.vsms.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LoginFlowTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserAccountService userService;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    TestData data;

    private UserAccount createUser(String username, Role role, Mechanic mechanic) {
        UserCreateForm form = new UserCreateForm();
        form.setUsername(username);
        form.setFullName("Login " + username);
        form.setRole(role);
        form.setMechanicId(mechanic == null ? null : mechanic.getId());
        form.setPassword("Workshop-2026");
        form.setConfirmPassword("Workshop-2026");
        return userService.create(form);
    }

    @Test
    void theBootstrapAdministratorCanSignInAndOut() throws Exception {
        mvc.perform(formLogin().user("admin").password(WebTestSupport.PASSWORD))
                .andExpect(authenticated()).andExpect(redirectedUrl("/"));
        mvc.perform(logout()).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void wrongPasswordsAndUnknownUsersAreRejectedWithTheSameMessage() throws Exception {
        mvc.perform(formLogin().user("admin").password("not-the-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin().user("nobody").password("whatever1"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void passwordsAreStoredAsBCryptHashesAndUsernamesIgnoreCase() throws Exception {
        UserAccount created = createUser("Case.Tester", Role.SERVICE_ADVISOR, null);
        assertTrue(created.getPasswordHash().startsWith("$2"), "expected a BCrypt hash");
        assertFalse(created.getPasswordHash().contains("Workshop-2026"));
        assertTrue(encoder.matches("Workshop-2026", created.getPasswordHash()));
        mvc.perform(formLogin().user("CASE.tester").password("Workshop-2026")).andExpect(authenticated());
    }

    @Test
    void disabledAccountsCannotSignIn() throws Exception {
        UserAccount created = createUser("disabled.user", Role.SERVICE_ADVISOR, null);
        created.setEnabled(false);
        mvc.perform(formLogin().user("disabled.user").password("Workshop-2026"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void aDeactivatedMechanicLosesTheirLogin() throws Exception {
        Mechanic mechanic = data.mechanic();
        createUser("mech.login", Role.MECHANIC, mechanic);
        mvc.perform(formLogin().user("mech.login").password("Workshop-2026")).andExpect(authenticated());
        mechanic.setActive(false);
        mvc.perform(formLogin().user("mech.login").password("Workshop-2026")).andExpect(unauthenticated());
    }

    @Test
    void sampleAccountsShareTheConfiguredPassword() throws Exception {
        for (String user : new String[]{"advisor", "ravi.k", "meena.j"}) {
            mvc.perform(formLogin().user(user).password(WebTestSupport.PASSWORD)).andExpect(authenticated());
        }
    }
}
