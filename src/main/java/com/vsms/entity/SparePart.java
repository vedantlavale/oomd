package com.vsms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "spare_parts",
        uniqueConstraints = @UniqueConstraint(name = "uk_part_sku", columnNames = "sku"))
@Check(constraints = "stock_quantity >= 0 AND unit_price >= 0 AND reorder_level >= 0")
public class SparePart extends AuditedEntity {

    /** Stock keeping unit, stored upper-case. */
    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    /** Stock at or below this level is shown as "low stock". */
    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel = 5;

    @Version
    private Long version;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public int getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }

    public boolean isLowStock() {
        return stockQuantity <= reorderLevel;
    }

    public boolean isOutOfStock() {
        return stockQuantity <= 0;
    }
}
