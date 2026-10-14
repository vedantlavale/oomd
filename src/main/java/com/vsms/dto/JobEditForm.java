package com.vsms.dto;

import com.vsms.entity.ServiceJob;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Service-advisor view of a job: the customer's complaint and the labour charge. */
public class JobEditForm {

    @NotBlank(message = "Describe the customer's complaint.")
    @Size(max = 1000, message = "The complaint can have at most 1000 characters.")
    private String customerComplaint;

    @NotNull(message = "Enter the labour charge.")
    @DecimalMin(value = "0.00", message = "The labour charge cannot be negative.")
    @DecimalMax(value = "9999999.99", message = "The labour charge is too large.")
    @Digits(integer = 7, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal laborCharge;

    public static JobEditForm from(ServiceJob job) {
        JobEditForm form = new JobEditForm();
        form.customerComplaint = job.getCustomerComplaint();
        form.laborCharge = job.getLaborCharge();
        return form;
    }

    public String getCustomerComplaint() { return customerComplaint; }
    public void setCustomerComplaint(String customerComplaint) { this.customerComplaint = customerComplaint; }
    public BigDecimal getLaborCharge() { return laborCharge; }
    public void setLaborCharge(BigDecimal laborCharge) { this.laborCharge = laborCharge; }
}
