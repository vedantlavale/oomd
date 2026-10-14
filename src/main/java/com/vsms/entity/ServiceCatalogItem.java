package com.vsms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;

/** A service the workshop offers (e.g. "Full Service"), with its standard labour price. */
@Entity
@Table(name = "service_catalog_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_catalog_name", columnNames = "name"))
@Check(constraints = "standard_price >= 0")
public class ServiceCatalogItem extends AuditedEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "standard_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal standardPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getStandardPrice() { return standardPrice; }
    public void setStandardPrice(BigDecimal standardPrice) { this.standardPrice = standardPrice; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
