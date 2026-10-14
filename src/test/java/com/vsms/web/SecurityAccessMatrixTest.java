package com.vsms.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsms.AbstractIntegrationTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Which role may open which page - the rules of the specification, checked one by one. */
@Transactional
class SecurityAccessMatrixTest extends AbstractIntegrationTest {

    private static final List<String> ADMIN_ONLY = List.of("/users", "/users/new", "/mechanics/new", "/catalog/new");
    private static final List<String> STAFF = List.of("/customers", "/customers/new", "/vehicles", "/vehicles/new",
            "/appointments", "/appointments/new", "/mechanics", "/catalog", "/invoices", "/payments", "/reports",
            "/reports/appointments", "/reports/jobs", "/reports/invoices", "/reports/payments", "/parts/new");
    private static final List<String> EVERYONE = List.of("/", "/jobs", "/parts", "/account/password");

    @Autowired
    MockMvc mvc;

    static Stream<Arguments> pageAccess() {
        Stream.Builder<Arguments> rows = Stream.builder();
        for (String path : ADMIN_ONLY) {
            rows.add(Arguments.of(path, "ADMIN", 200));
            rows.add(Arguments.of(path, "SERVICE_ADVISOR", 403));
            rows.add(Arguments.of(path, "MECHANIC", 403));
        }
        for (String path : STAFF) {
            rows.add(Arguments.of(path, "ADMIN", 200));
            rows.add(Arguments.of(path, "SERVICE_ADVISOR", 200));
            rows.add(Arguments.of(path, "MECHANIC", 403));
        }
        for (String path : EVERYONE) {
            rows.add(Arguments.of(path, "ADMIN", 200));
            rows.add(Arguments.of(path, "SERVICE_ADVISOR", 200));
            rows.add(Arguments.of(path, "MECHANIC", 200));
        }
        return rows.build();
    }

    @ParameterizedTest(name = "{1} opening {0} -> {2}")
    @MethodSource("pageAccess")
    void pagesAreRestrictedByRole(String path, String role, int expectedStatus) throws Exception {
        mvc.perform(get(path).with(user("someone").roles(role))).andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest(name = "anonymous opening {0} is sent to the login page")
    @MethodSource("everyPath")
    void anonymousUsersAreSentToLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    static Stream<String> everyPath() {
        return Stream.of(ADMIN_ONLY, STAFF, EVERYONE).flatMap(List::stream);
    }

    static Stream<Arguments> destructiveActions() {
        return Stream.of(
                // admin only: advisors and mechanics are refused
                Arguments.of("/customers/1/delete", "SERVICE_ADVISOR"), Arguments.of("/customers/1/delete", "MECHANIC"),
                Arguments.of("/vehicles/1/delete", "SERVICE_ADVISOR"),
                Arguments.of("/mechanics/1/deactivate", "SERVICE_ADVISOR"),
                Arguments.of("/catalog/1/delete", "SERVICE_ADVISOR"),
                Arguments.of("/catalog/1/active", "SERVICE_ADVISOR"),
                Arguments.of("/parts/1/delete", "SERVICE_ADVISOR"), Arguments.of("/parts/1/delete", "MECHANIC"),
                Arguments.of("/payments/1/reverse", "SERVICE_ADVISOR"),
                Arguments.of("/users", "SERVICE_ADVISOR"), Arguments.of("/users/1/password", "SERVICE_ADVISOR"),
                // staff only: mechanics are refused
                Arguments.of("/customers", "MECHANIC"), Arguments.of("/vehicles", "MECHANIC"),
                Arguments.of("/appointments", "MECHANIC"), Arguments.of("/appointments/1/cancel", "MECHANIC"),
                Arguments.of("/jobs", "MECHANIC"), Arguments.of("/jobs/1/assign", "MECHANIC"),
                Arguments.of("/jobs/1", "MECHANIC"), Arguments.of("/invoices", "MECHANIC"),
                Arguments.of("/payments", "MECHANIC"), Arguments.of("/parts", "MECHANIC"),
                Arguments.of("/parts/1/stock", "MECHANIC"));
    }

    @ParameterizedTest(name = "{1} posting to {0} is refused")
    @MethodSource("destructiveActions")
    void changingActionsAreRefusedForTheWrongRole(String path, String role) throws Exception {
        // POST /jobs/1 is the staff-only "edit job details" route; the others are POSTs on the same pattern
        mvc.perform(post(path).with(csrf()).with(user("someone").roles(role))
                        .param("quantity", "1").param("name", "x").param("phone", "9999999999"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postsWithoutACsrfTokenAreRefusedForEveryone() throws Exception {
        mvc.perform(post("/customers").with(user("a").roles("ADMIN")).param("name", "No token").param("phone", "9999999999"))
                .andExpect(status().isForbidden());
    }
}
