package com.vsms.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vsms.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

/** A safety net: a new controller must not be added without a role rule. */
class ControllerSecurityAnnotationTest extends AbstractIntegrationTest {

    /** Open to every signed-in person by the filter chain; they hold no restricted data of their own. */
    private static final Set<String> NO_CLASS_RULE_NEEDED = Set.of("LoginController", "DashboardController");

    @Autowired
    ApplicationContext context;

    @Test
    void everyControllerDeclaresWhoMayUseIt() {
        List<String> missing = new ArrayList<>();
        for (Object controller : context.getBeansWithAnnotation(Controller.class).values()) {
            Class<?> type = AopUtils.getTargetClass(controller);
            if (type.getName().startsWith("com.vsms.controller")
                    && !NO_CLASS_RULE_NEEDED.contains(type.getSimpleName())
                    && !AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class)) {
                missing.add(type.getSimpleName());
            }
        }
        assertTrue(missing.isEmpty(), "Controllers without @PreAuthorize: " + missing);
    }
}
