package com.vsms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

/**
 * A spare part used on a job. The unit price is copied from the catalogue when the part is
 * added, so later price changes never alter what the customer was charged.
 */
@Entity
@Table(name = "part_usages", indexes = {
        @Index(name = "idx_usage_job", columnList = "service_job_id"),
        @Index(name = "idx_usage_part", columnList = "spare_part_id")
})
@Check(constraints = "quantity > 0 AND unit_price_at_use >= 0")
public class PartUsage extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_usage_job"))
    private ServiceJob serviceJob;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spare_part_id", nullable = false, foreignKey = @ForeignKey(name = "fk_usage_part"))
    private SparePart sparePart;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_at_use", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceAtUse;

    /** True once this line has taken its quantity out of stock (guards against double deduction). */
    @Column(name = "stock_deducted", nullable = false)
    private boolean stockDeducted;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public ServiceJob getServiceJob() { return serviceJob; }
    public void setServiceJob(ServiceJob serviceJob) { this.serviceJob = serviceJob; }
    public SparePart getSparePart() { return sparePart; }
    public void setSparePart(SparePart sparePart) { this.sparePart = sparePart; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPriceAtUse() { return unitPriceAtUse; }
    public void setUnitPriceAtUse(BigDecimal unitPriceAtUse) { this.unitPriceAtUse = unitPriceAtUse; }
    public boolean isStockDeducted() { return stockDeducted; }
    public void setStockDeducted(boolean stockDeducted) { this.stockDeducted = stockDeducted; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public BigDecimal getLineTotal() {
        return unitPriceAtUse.multiply(BigDecimal.valueOf(quantity));
    }
}
