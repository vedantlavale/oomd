package com.vsms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Revenue collected on one day. */
public record DailyTotal(LocalDate date, long count, BigDecimal total) {
}
