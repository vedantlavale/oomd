package com.vsms.util;

import com.vsms.exception.BusinessRuleException;
import org.springframework.validation.BindingResult;

public final class FormErrors {

    private FormErrors() {
    }

    /** The first validation message, for small forms that report errors as a banner after a redirect. */
    public static String firstMessage(BindingResult result) {
        return result.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("Please check what you entered.");
    }

    /**
     * Shows a business-rule message next to the form field it belongs to, or as a general form
     * error when it has no field.
     */
    public static void apply(BindingResult result, BusinessRuleException ex) {
        if (ex.getField() != null) {
            try {
                result.rejectValue(ex.getField(), "business.rule", ex.getMessage());
                return;
            } catch (RuntimeException unknownField) {
                // fall back to a general error below
            }
        }
        result.reject("business.rule", ex.getMessage());
    }
}
