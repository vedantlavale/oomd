package com.vsms.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Small helpers shared by the web tests. */
public final class WebTestSupport {

    /** The password given to every account created at start-up in the test profile (see application-test.yml). */
    public static final String PASSWORD = "Test-Admin-Pass-1";

    private WebTestSupport() {
    }

    /** Signs in through the real login form and returns the session. */
    public static MockHttpSession login(MockMvc mvc, String username, String password) throws Exception {
        MvcResult result = mvc.perform(formLogin().user(username).password(password))
                .andExpect(authenticated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public static MockHttpSession login(MockMvc mvc, String username) throws Exception {
        return login(mvc, username, PASSWORD);
    }

    /** The last path segment of a redirect such as /customers/42. */
    public static Long idFromRedirect(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        if (location == null) {
            throw new AssertionError("Expected a redirect but got status " + result.getResponse().getStatus());
        }
        String path = location.contains("?") ? location.substring(0, location.indexOf('?')) : location;
        return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
    }
}
