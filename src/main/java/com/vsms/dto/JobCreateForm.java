package com.vsms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class JobCreateForm {

    @NotNull(message = "Choose the appointment.")
    private Long appointmentId;

    @NotBlank(message = "Describe the customer's complaint.")
    @Size(max = 1000, message = "The complaint can have at most 1000 characters.")
    private String customerComplaint;

    @NotNull(message = "Enter the labour charge.")
    @DecimalMin(value = "0.00", message = "The labour charge cannot be negative.")
    @DecimalMax(value = "9999999.99", message = "The labour charge is too large.")
    @Digits(integer = 7, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal laborCharge;

    /** Optional: assign a mechanic right away. */
    private Long mechanicId;

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public String getCustomerComplaint() { return customerComplaint; }
    public void setCustomerComplaint(String customerComplaint) { this.customerComplaint = customerComplaint; }
    public BigDecimal getLaborCharge() { return laborCharge; }
    public void setLaborCharge(BigDecimal laborCharge) { this.laborCharge = laborCharge; }
    public Long getMechanicId() { return mechanicId; }
    public void setMechanicId(Long mechanicId) { this.mechanicId = mechanicId; }
}
