package com.vsms.dto;

import com.vsms.entity.SparePart;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class SparePartForm {

    @NotBlank(message = "Enter the SKU (part code).")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._\\-]{1,39}$",
            message = "SKU can contain letters, digits, dots, hyphens and underscores (2 to 40 characters).")
    private String sku;

    @NotBlank(message = "Enter the part name.")
    @Size(max = 120, message = "Name can have at most 120 characters.")
    private String name;

    @NotNull(message = "Enter the unit price.")
    @DecimalMin(value = "0.00", message = "Price cannot be negative.")
    @DecimalMax(value = "9999999.99", message = "Price is too large.")
    @Digits(integer = 7, fraction = 2, message = "Use at most 2 decimal places.")
    private BigDecimal unitPrice;

    @NotNull(message = "Enter the stock quantity.")
    @Min(value = 0, message = "Stock cannot be negative.")
    @Max(value = 1000000, message = "Stock quantity is too large.")
    private Integer stockQuantity;

    @NotNull(message = "Enter the low-stock level.")
    @Min(value = 0, message = "The low-stock level cannot be negative.")
    @Max(value = 100000, message = "The low-stock level is too large.")
    private Integer reorderLevel = 5;

    public static SparePartForm from(SparePart part) {
        SparePartForm form = new SparePartForm();
        form.sku = part.getSku();
        form.name = part.getName();
        form.unitPrice = part.getUnitPrice();
        form.stockQuantity = part.getStockQuantity();
        form.reorderLevel = part.getReorderLevel();
        return form;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
    public Integer getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(Integer reorderLevel) { this.reorderLevel = reorderLevel; }
}
