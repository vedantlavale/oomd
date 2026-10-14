package com.vsms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One checklist item on a job card, e.g. "Replace oil filter". */
@Entity
@Table(name = "service_tasks")
public class ServiceTask extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_task_job"))
    private ServiceJob serviceJob;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public ServiceJob getServiceJob() { return serviceJob; }
    public void setServiceJob(ServiceJob serviceJob) { this.serviceJob = serviceJob; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
