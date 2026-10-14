package com.vsms.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import com.vsms.repository.UserAccountRepository;
import com.vsms.service.UserAccountService;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** The documented ways of getting the first administrator, and of recovering a forgotten password. */
@Transactional
class AdminBootstrapTest extends AbstractIntegrationTest {

    @Autowired
    UserAccountRepository users;
    @Autowired
    UserAccountService userService;
    @Autowired
    PasswordEncoder encoder;

    private VsmsProperties properties(String username, String password, boolean reset) {
        return new VsmsProperties(new VsmsProperties.Admin(username, password, reset),
                new VsmsProperties.SampleData(false),
                new VsmsProperties.Billing(new BigDecimal("18.00"), "₹"),
                new VsmsProperties.Business("Test", "", "", "", ""),
                new VsmsProperties.Appointments(LocalTime.of(9, 0), LocalTime.of(18, 0), 30, 180));
    }

    private void run(VsmsProperties props, AdminBootstrap.CredentialSource credentials) {
        new AdminBootstrap(props, users, userService, credentials).run(new DefaultApplicationArguments());
    }

    private void removeAllAdministrators() {
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).forEach(users::delete);
        users.flush();
        assertFalse(users.existsByRole(Role.ADMIN));
    }

    @Test
    void withNoAdministratorOneIsCreatedFromTheConfiguredPassword() {
        removeAllAdministrators();
        VsmsProperties props = properties("first.admin", "Chosen-Pass-77", false);
        run(props, new AdminBootstrap.CredentialSource(props));

        UserAccount admin = users.findByUsername("first.admin").orElseThrow();
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertTrue(encoder.matches("Chosen-Pass-77", admin.getPasswordHash()));
        assertFalse(admin.getPasswordHash().contains("Chosen-Pass-77"));
    }

    @Test
    void withoutAConfiguredPasswordARandomOneIsGenerated() {
        removeAllAdministrators();
        VsmsProperties props = properties("random.admin", "", false);
        AdminBootstrap.CredentialSource credentials = new AdminBootstrap.CredentialSource(props);
        run(props, credentials);

        String generated = credentials.password("test");   // the same one-time password that was printed
        assertTrue(generated.length() >= 16);
        assertTrue(encoder.matches(generated, users.findByUsername("random.admin").orElseThrow().getPasswordHash()));
        // a second start-up must not create another account or change anything
        run(props, credentials);
        assertEquals(1, users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count());
    }

    @Test
    void anExistingAdministratorIsLeftAloneOnNormalStartUp() {
        String before = users.findByUsername("admin").orElseThrow().getPasswordHash();
        VsmsProperties props = properties("admin", "Another-Pass-55", false);
        run(props, new AdminBootstrap.CredentialSource(props));
        assertEquals(before, users.findByUsername("admin").orElseThrow().getPasswordHash());
    }

    @Test
    void theResetOptionRecoversAForgottenPasswordAndReenablesTheAccount() {
        UserAccount admin = users.findByUsername("admin").orElseThrow();
        admin.setEnabled(false);
        VsmsProperties props = properties("admin", "Recovered-Pass-99", true);
        run(props, new AdminBootstrap.CredentialSource(props));

        UserAccount recovered = users.findByUsername("admin").orElseThrow();
        assertTrue(recovered.isEnabled());
        assertTrue(encoder.matches("Recovered-Pass-99", recovered.getPasswordHash()));
    }

    @Test
    void theResetOptionNeverTouchesAnAccountThatIsNotAnAdministrator() {
        String before = users.findByUsername("advisor").orElseThrow().getPasswordHash();
        VsmsProperties props = properties("advisor", "Takeover-Pass-1", true);
        run(props, new AdminBootstrap.CredentialSource(props));
        assertEquals(before, users.findByUsername("advisor").orElseThrow().getPasswordHash());
        assertEquals(Role.SERVICE_ADVISOR, users.findByUsername("advisor").orElseThrow().getRole());
    }

    @Test
    void aWeakConfiguredPasswordIsRefusedWithAClearMessage() {
        removeAllAdministrators();
        VsmsProperties props = properties("weak.admin", "password", false);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> run(props, new AdminBootstrap.CredentialSource(props)));
        assertTrue(ex.getMessage().contains("VSMS_ADMIN_PASSWORD"));
    }
}
