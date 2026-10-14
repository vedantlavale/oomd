package com.vsms.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.vsms.AbstractIntegrationTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Signs in as each role and follows every link, starting at the dashboard. For every page it checks:
 * the page renders (200), none of its links lead to 403/404/500 (so a role never sees a link it cannot
 * use), every form posts to a route that exists and carries a CSRF token, every input has a label and
 * every icon-only button has an accessible name.
 */
@Transactional
class PageCrawlTest extends AbstractIntegrationTest {

    private static final int MAX_PAGES = 600;

    @Autowired
    MockMvc mvc;
    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    record Crawl(Set<String> pages, List<String> problems) {
    }

    @Test
    void administratorCanOpenEverythingTheyAreShown() throws Exception {
        Crawl crawl = crawl("admin");
        assertTrue(crawl.problems().isEmpty(), String.join("\n", crawl.problems()));
        assertTrue(crawl.pages().size() > 60, "the crawl only reached " + crawl.pages().size() + " pages");
        assertCovers(crawl, "/", "/customers", "/vehicles", "/appointments", "/jobs", "/mechanics", "/catalog", "/parts",
                "/invoices", "/payments", "/reports", "/reports/appointments", "/reports/jobs", "/reports/invoices",
                "/reports/payments", "/users", "/customers/new", "/vehicles/new", "/appointments/new", "/mechanics/new",
                "/catalog/new", "/parts/new", "/users/new", "/account/password");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.matches("/jobs/\\d+")), "no job card was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.matches("/invoices/\\d+")), "no invoice was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.matches("/payments/\\d+/reverse")), "no reverse page was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.matches("/customers/\\d+/edit")), "no edit page was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.matches("/appointments/\\d+/reschedule")), "no reschedule page was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.startsWith("/invoices/new?jobId=")), "no invoice form was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.startsWith("/payments/new?invoiceId=")), "no payment form was reached");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.startsWith("/jobs/new?appointmentId=")), "no job-card form was reached");
    }

    @Test
    void serviceAdvisorNeverSeesALinkTheyCannotUse() throws Exception {
        Crawl crawl = crawl("advisor");
        assertTrue(crawl.problems().isEmpty(), String.join("\n", crawl.problems()));
        assertCovers(crawl, "/", "/customers", "/jobs", "/invoices", "/reports");
        assertTrue(crawl.pages().stream().noneMatch(p -> p.startsWith("/users")), "advisor was shown a Users link");
        assertTrue(crawl.pages().stream().noneMatch(p -> p.endsWith("/reverse")), "advisor was shown a reverse-payment link");
        assertTrue(crawl.pages().stream().noneMatch(p -> p.equals("/mechanics/new") || p.equals("/catalog/new")),
                "advisor was shown an admin-only form");
    }

    @Test
    void mechanicNeverSeesALinkTheyCannotUse() throws Exception {
        Crawl crawl = crawl("ravi.k");
        assertTrue(crawl.problems().isEmpty(), String.join("\n", crawl.problems()));
        assertCovers(crawl, "/", "/jobs", "/parts", "/account/password");
        assertTrue(crawl.pages().stream().noneMatch(p -> p.startsWith("/customers") || p.startsWith("/invoices")
                || p.startsWith("/payments") || p.startsWith("/reports") || p.startsWith("/users")
                || p.startsWith("/appointments") || p.startsWith("/vehicles")), "mechanic was shown a staff page link");
        assertTrue(crawl.pages().stream().anyMatch(p -> p.matches("/jobs/\\d+")), "mechanic could not open a job card");
    }

    private void assertCovers(Crawl crawl, String... expected) {
        for (String path : expected) {
            assertTrue(crawl.pages().contains(path), "the crawl never reached " + path + "; reached " + crawl.pages());
        }
    }

    // ------------------------------------------------------------------------ the crawler

    private Crawl crawl(String username) throws Exception {
        MockHttpSession session = WebTestSupport.login(mvc, username);
        Deque<String> queue = new ArrayDeque<>(List.of("/"));
        Set<String> seen = new HashSet<>();
        Set<String> pages = new HashSet<>();
        List<String> problems = new ArrayList<>();

        while (!queue.isEmpty() && seen.size() < MAX_PAGES) {
            String url = queue.poll();
            if (!seen.add(url)) {
                continue;
            }
            MvcResult result;
            try {
                result = mvc.perform(get(url).session(session)).andReturn();
            } catch (Exception ex) {
                problems.add("EXCEPTION on " + url + ": " + rootMessage(ex));
                continue;
            }
            int status = result.getResponse().getStatus();
            if (status >= 300 && status < 400) {
                String target = normalise(result.getResponse().getRedirectedUrl());
                if (target != null) {
                    queue.add(target);
                }
                continue;
            }
            if (status != 200) {
                problems.add(username + ": " + status + " for " + url);
                continue;
            }
            pages.add(url);
            Document document = Jsoup.parse(result.getResponse().getContentAsString(), "http://localhost");
            for (Element link : document.select("a[href]")) {
                String target = normalise(link.attr("href"));
                if (target != null) {
                    queue.add(target);
                }
            }
            checkForms(url, document, problems);
            checkAccessibility(url, document, problems);
        }
        assertTrue(seen.size() < MAX_PAGES, "crawl did not finish within " + MAX_PAGES + " pages (link loop?). Sample: "
                + seen.stream().skip(Math.max(0, seen.size() - 25)).toList());
        return new Crawl(pages, problems);
    }

    /** Internal, navigable links only; fragments are dropped and the logout form is not a link. */
    private String normalise(String href) {
        if (href == null || href.isBlank() || href.startsWith("#") || href.startsWith("javascript:")
                || href.startsWith("mailto:") || href.startsWith("tel:")) {
            return null;
        }
        String path = href.replace("http://localhost", "");
        if (!path.startsWith("/") || path.startsWith("//")) {
            return null;
        }
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        if (path.startsWith("/logout") || path.startsWith("/webjars") || path.startsWith("/css") || path.startsWith("/js")
                || path.startsWith("/images")) {
            return null;
        }
        return path;
    }

    /** Every form must post to an existing route and (for POST) carry a CSRF token. */
    private void checkForms(String page, Document document, List<String> problems) {
        for (Element form : document.select("form[action]")) {
            String method = form.attr("method").isBlank() ? "GET" : form.attr("method").toUpperCase();
            String action = form.attr("action");
            if (action.startsWith("#") || action.startsWith("javascript:")) {
                continue;
            }
            String path = action.contains("?") ? action.substring(0, action.indexOf('?')) : action;
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            ServletRequestPathUtils.parseAndCache(request);
            try {
                if (handlerMapping.getHandler(request) == null && !path.equals("/login") && !path.equals("/logout")) {
                    problems.add("FORM without a route: " + method + " " + action + " on " + page);
                }
            } catch (Exception ex) {
                problems.add("FORM lookup failed: " + method + " " + action + " on " + page + ": " + ex.getMessage());
            }
            if (method.equals("POST") && form.select("input[name=_csrf]").isEmpty()) {
                problems.add("POST form without a CSRF token: " + action + " on " + page);
            }
        }
    }

    /** Inputs need labels; buttons and links that only contain an icon need an accessible name. */
    private void checkAccessibility(String page, Document document, List<String> problems) {
        for (Element control : document.select("input:not([type=hidden]):not([type=submit]):not([type=button]), select, textarea")) {
            boolean labelled = !control.attr("aria-label").isBlank() || !control.attr("aria-labelledby").isBlank()
                    || (!control.id().isBlank() && !document.select("label[for=" + control.id() + "]").isEmpty())
                    || control.parents().stream().anyMatch(p -> p.tagName().equals("label"));
            if (!labelled) {
                problems.add("CONTROL without a label: " + control.tagName() + "[name=" + control.attr("name") + "] on " + page);
            }
        }
        for (Element button : document.select("button, a.btn")) {
            boolean hasName = !button.text().isBlank() || !button.attr("aria-label").isBlank()
                    || !button.select(".visually-hidden").text().isBlank();
            if (!hasName) {
                problems.add("BUTTON without an accessible name: " + button.outerHtml() + " on " + page);
            }
        }
        for (Element panel : document.select(".panel")) {
            if (!panel.select(".empty-state").isEmpty() && !panel.select("table tbody tr").isEmpty()) {
                problems.add("EMPTY STATE shown together with table rows on " + page);
            }
        }
        for (Element image : document.select("img:not([alt])")) {
            problems.add("IMAGE without alt text on " + page + ": " + image.outerHtml());
        }
        assertEquals(1, document.select("h1").size(), "page should have exactly one <h1>: " + page);
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
