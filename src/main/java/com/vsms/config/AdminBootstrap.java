package com.vsms.config;

import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import com.vsms.repository.UserAccountRepository;
import com.vsms.service.UserAccountService;
import com.vsms.util.PasswordPolicy;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator account.
 *
 * <ul>
 *   <li>If no administrator exists, one is created from VSMS_ADMIN_USERNAME / VSMS_ADMIN_PASSWORD.
 *       Without a password, a random one is generated and printed once in the console.</li>
 *   <li>If the password was forgotten, start the app once with VSMS_ADMIN_RESET=true and a new
 *       VSMS_ADMIN_PASSWORD to reset it.</li>
 * </ul>
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final VsmsProperties properties;
    private final UserAccountRepository users;
    private final UserAccountService userService;
    private final CredentialSource credentials;

    public AdminBootstrap(VsmsProperties properties, UserAccountRepository users,
                          UserAccountService userService, CredentialSource credentials) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
        this.credentials = credentials;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.admin().username().trim().toLowerCase(Locale.ROOT);
        boolean reset = properties.admin().resetOnStartup();
        Optional<UserAccount> existing = users.findByUsername(username);

        if (existing.isPresent()) {
            if (reset) {
                resetExisting(existing.get());
            }
            return;
        }
        if (!reset && users.existsByRole(Role.ADMIN)) {
            return;
        }
        String password = credentials.password("the administrator account '" + username + "'");
        userService.createDirect(username, "System Administrator", Role.ADMIN, null, password);
        log.info("Created administrator account '{}'.", username);
    }

    private void resetExisting(UserAccount account) {
        if (account.getRole() != Role.ADMIN) {
            log.error("VSMS_ADMIN_RESET is set but '{}' is not an administrator; nothing was changed.",
                    account.getUsername());
            return;
        }
        String password = credentials.password("the administrator account '" + account.getUsername() + "' (reset)");
        account.setEnabled(true);
        userService.setPassword(account, password);
        log.warn("Password of administrator '{}' was reset. Remove VSMS_ADMIN_RESET before the next start.",
                account.getUsername());
    }

    /**
     * Supplies the password for accounts created at start-up: the VSMS_ADMIN_PASSWORD value if
     * given, otherwise one random password per start (printed once so it is never stored in code).
     */
    @Component
    public static class CredentialSource {

        private final VsmsProperties properties;
        private final SecureRandom random = new SecureRandom();
        private String generated;

        public CredentialSource(VsmsProperties properties) {
            this.properties = properties;
        }

        public synchronized String password(String purpose) {
            String configured = properties.admin().password();
            if (configured != null && !configured.isBlank()) {
                String problem = PasswordPolicy.check(configured);
                if (problem != null) {
                    throw new IllegalStateException("VSMS_ADMIN_PASSWORD is not acceptable: " + problem);
                }
                return configured;
            }
            if (generated == null) {
                generated = generate();
                String sample = properties.sampleData().enabled()
                        ? "  If the demo data is loaded, the demo accounts advisor, ravi.k and meena.j use it too.\n"
                        : "";
                log.warn("""

                        ==========================================================================
                          VSMS_ADMIN_PASSWORD was not set, so a random password was generated.
                          Password for {}: {}
                        {}  It is shown only once. Sign in and change it from "Change password".
                        ==========================================================================
                        """, purpose, generated, sample);
            }
            return generated;
        }

        private String generate() {
            String candidate;
            do {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < 16; i++) {
                    builder.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
                }
                candidate = builder.toString();
            } while (PasswordPolicy.check(candidate) != null);
            return candidate;
        }
    }
}
