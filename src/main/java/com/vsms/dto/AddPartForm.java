package com.vsms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class AddPartForm {

    @NotNull(message = "Choose a spare part.")
    private Long sparePartId;

    @NotNull(message = "Enter the quantity used.")
    @Min(value = 1, message = "The quantity must be at least 1.")
    @Max(value = 1000, message = "The quantity is too large.")
    private Integer quantity;

    public Long getSparePartId() { return sparePartId; }
    public void setSparePartId(Long sparePartId) { this.sparePartId = sparePartId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
