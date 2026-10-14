package com.vsms.dto;

import com.vsms.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class PaymentForm {

    @NotNull(message = "Choose the invoice.")
    private Long invoiceId;

    @NotNull(message = "Enter the amount received.")
    @DecimalMin(value = "0.01", message = "The amount must be greater than zero.")
    @DecimalMax(value = "9999999.99", message = "The amount is too large.")
    @Digits(integer = 7, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Choose how the customer paid.")
    private PaymentMethod method = PaymentMethod.CASH;

    @Size(max = 100, message = "The reference can have at most 100 characters.")
    private String reference;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
