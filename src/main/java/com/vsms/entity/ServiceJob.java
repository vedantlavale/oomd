package com.vsms.entity;

import com.vsms.enums.JobStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** The job card: everything done to one vehicle for one appointment. */
@Entity
@Table(name = "service_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_appointment", columnNames = "appointment_id"),
        indexes = {
        @Index(name = "idx_job_status", columnList = "status"),
        @Index(name = "idx_job_mechanic", columnList = "mechanic_id")
})
@Check(constraints = "labor_charge >= 0")
public class ServiceJob extends AuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_appointment"))
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mechanic_id", foreignKey = @ForeignKey(name = "fk_job_mechanic"))
    private Mechanic mechanic;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.CREATED;

    @Column(name = "customer_complaint", nullable = false, length = 1000)
    private String customerComplaint;

    @Column(length = 2000)
    private String diagnosis;

    @Column(name = "repair_notes", length = 2000)
    private String repairNotes;

    @Column(name = "labor_charge", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCharge = BigDecimal.ZERO;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "serviceJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ServiceTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "serviceJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<PartUsage> partUsages = new ArrayList<>();

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Mechanic getMechanic() { return mechanic; }
    public void setMechanic(Mechanic mechanic) { this.mechanic = mechanic; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getCustomerComplaint() { return customerComplaint; }
    public void setCustomerComplaint(String customerComplaint) { this.customerComplaint = customerComplaint; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getRepairNotes() { return repairNotes; }
    public void setRepairNotes(String repairNotes) { this.repairNotes = repairNotes; }
    public BigDecimal getLaborCharge() { return laborCharge; }
    public void setLaborCharge(BigDecimal laborCharge) { this.laborCharge = laborCharge; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public List<ServiceTask> getTasks() { return tasks; }
    public List<PartUsage> getPartUsages() { return partUsages; }

    public void addTask(ServiceTask task) {
        task.setServiceJob(this);
        tasks.add(task);
    }

    public void addPartUsage(PartUsage usage) {
        usage.setServiceJob(this);
        partUsages.add(usage);
    }

    /** Human friendly job card number, e.g. JC-000042. */
    public String getJobNumber() {
        return getId() == null ? "JC-NEW" : String.format("JC-%06d", getId());
    }

    public Customer getCustomer() { return appointment.getCustomer(); }
    public Vehicle getVehicle() { return appointment.getVehicle(); }
    public ServiceCatalogItem getServiceItem() { return appointment.getServiceItem(); }

    /** Sum of every part line (unit price at the time of use x quantity). */
    public BigDecimal getPartsTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PartUsage usage : partUsages) {
            total = total.add(usage.getLineTotal());
        }
        return total;
    }

    public long getCompletedTaskCount() {
        return tasks.stream().filter(ServiceTask::isCompleted).count();
    }
}
