package com.vsms.dto;

import com.vsms.enums.PaymentMethod;
import java.math.BigDecimal;

/** Revenue collected through one payment method. */
public record MethodTotal(PaymentMethod method, long count, BigDecimal total) {
}
