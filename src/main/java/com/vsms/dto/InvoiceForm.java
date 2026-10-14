package com.vsms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class InvoiceForm {

    @NotNull(message = "Choose the job to invoice.")
    private Long jobId;

    /** Tax rate in percent; 0 means no tax. */
    @NotNull(message = "Enter the tax rate (0 for no tax).")
    @DecimalMin(value = "0.00", message = "The tax rate cannot be negative.")
    @DecimalMax(value = "100.00", message = "The tax rate cannot be more than 100%.")
    @Digits(integer = 3, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal taxPercent;

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public BigDecimal getTaxPercent() { return taxPercent; }
    public void setTaxPercent(BigDecimal taxPercent) { this.taxPercent = taxPercent; }
}
