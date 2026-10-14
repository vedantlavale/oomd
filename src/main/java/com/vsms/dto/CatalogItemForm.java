package com.vsms.dto;

import com.vsms.entity.ServiceCatalogItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CatalogItemForm {

    @NotBlank(message = "Enter the service name.")
    @Size(max = 100, message = "Name can have at most 100 characters.")
    private String name;

    @Size(max = 255, message = "Description can have at most 255 characters.")
    private String description;

    @NotNull(message = "Enter the standard price.")
    @DecimalMin(value = "0.00", message = "Price cannot be negative.")
    @DecimalMax(value = "9999999.99", message = "Price is too large.")
    @Digits(integer = 7, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal standardPrice;

    private boolean active = true;

    public static CatalogItemForm from(ServiceCatalogItem item) {
        CatalogItemForm form = new CatalogItemForm();
        form.name = item.getName();
        form.description = item.getDescription();
        form.standardPrice = item.getStandardPrice();
        form.active = item.isActive();
        return form;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getStandardPrice() { return standardPrice; }
    public void setStandardPrice(BigDecimal standardPrice) { this.standardPrice = standardPrice; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
