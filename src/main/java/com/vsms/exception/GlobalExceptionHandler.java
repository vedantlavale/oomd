package com.vsms.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Turns exceptions from services into friendly pages or messages.
 * Access-denied errors are left to Spring Security, which shows the 403 page.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public String notFound(ResourceNotFoundException ex, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return "error/404";
    }

    /** A rule was broken in an action that has no form to redisplay: go back with a message. */
    @ExceptionHandler(BusinessRuleException.class)
    public String businessRule(BusinessRuleException ex, HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", ex.getMessage());
        return "redirect:" + backTarget(request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String staleData(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error",
                "Someone else changed this record at the same time. Please review the latest data and try again.");
        return "redirect:" + backTarget(request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String integrity(DataIntegrityViolationException ex, HttpServletRequest request,
                            RedirectAttributes redirect) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        redirect.addFlashAttribute("error",
                "That change conflicts with existing data (for example a duplicate value). Nothing was saved.");
        return "redirect:" + backTarget(request);
    }

    /** Another request is holding the same row for too long (very rare): ask the user to retry. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public String busy(PessimisticLockingFailureException ex, HttpServletRequest request, RedirectAttributes redirect) {
        log.warn("Lock wait failed: {}", ex.getMessage());
        redirect.addFlashAttribute("error", "Someone else is changing the same record right now. Please try again in a moment.");
        return "redirect:" + backTarget(request);
    }

    /**
     * Any other database refusal, e.g. a CHECK constraint (MySQL reports these differently from H2).
     * The change was not saved; nothing is half-done because every action runs in one transaction.
     */
    @ExceptionHandler(DataAccessException.class)
    public String database(DataAccessException ex, HttpServletRequest request, RedirectAttributes redirect) {
        log.error("Database error while handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        redirect.addFlashAttribute("error", "The database refused that change, so nothing was saved. Please check the values and try again.");
        return "redirect:" + backTarget(request);
    }

    /** Same-site path of the page the user came from (never an external address). */
    static String backTarget(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                if (uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName())
                        && uri.getRawPath() != null && uri.getRawPath().startsWith("/")
                        && !uri.getRawPath().startsWith("//")) {
                    return uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to the dashboard
            }
        }
        return "/";
    }
}
