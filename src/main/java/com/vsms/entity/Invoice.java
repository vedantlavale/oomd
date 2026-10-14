package com.vsms.entity;

import com.vsms.enums.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bill for one completed job. All amounts are stored when the invoice is generated, so they
 * stay the same even if prices in the catalogue change later.
 */
@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_job", columnNames = "service_job_id"),
        indexes = {
        @Index(name = "idx_invoice_status", columnList = "payment_status"),
        @Index(name = "idx_invoice_issued", columnList = "issued_at")
})
@Check(constraints = "grand_total >= 0 AND amount_paid >= 0 AND amount_paid <= grand_total")
public class Invoice extends AuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_job"))
    private ServiceJob serviceJob;

    @Column(name = "labor_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCharge;

    @Column(name = "parts_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** Tax rate applied, in percent (0 means no tax). */
    @Column(name = "tax_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercent;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by", length = 50)
    private String issuedBy;

    @Version
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("paidAt ASC, id ASC")
    private List<Payment> payments = new ArrayList<>();

    public ServiceJob getServiceJob() { return serviceJob; }
    public void setServiceJob(ServiceJob serviceJob) { this.serviceJob = serviceJob; }
    public BigDecimal getLaborCharge() { return laborCharge; }
    public void setLaborCharge(BigDecimal laborCharge) { this.laborCharge = laborCharge; }
    public BigDecimal getPartsTotal() { return partsTotal; }
    public void setPartsTotal(BigDecimal partsTotal) { this.partsTotal = partsTotal; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTaxPercent() { return taxPercent; }
    public void setTaxPercent(BigDecimal taxPercent) { this.taxPercent = taxPercent; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public List<Payment> getPayments() { return payments; }

    public String getInvoiceNumber() {
        return getId() == null ? "INV-NEW" : String.format("INV-%06d", getId());
    }

    public BigDecimal getBalanceDue() {
        return grandTotal.subtract(amountPaid);
    }
}
