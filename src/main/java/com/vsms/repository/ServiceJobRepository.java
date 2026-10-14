package com.vsms.repository;

import com.vsms.entity.ServiceJob;
import com.vsms.enums.JobStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceJobRepository extends JpaRepository<ServiceJob, Long> {

    @EntityGraph(attributePaths = {"appointment", "appointment.customer", "appointment.vehicle",
            "appointment.serviceItem", "mechanic"})
    @Query("""
            select j from ServiceJob j
            where (:status is null or j.status = :status)
              and (:mechanicId is null or j.mechanic.id = :mechanicId)
              and (:from is null or j.createdAt >= :from)
              and (:to is null or j.createdAt < :to)
              and (:q is null
                   or lower(j.appointment.customer.name) like :q
                   or lower(j.appointment.vehicle.registrationNumber) like :reg
                   or j.id = :qId)
            """)
    Page<ServiceJob> search(@Param("q") String q,
                            @Param("reg") String reg,
                            @Param("qId") Long qId,
                            @Param("status") JobStatus status,
                            @Param("mechanicId") Long mechanicId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            Pageable pageable);

    @EntityGraph(attributePaths = {"appointment", "appointment.customer", "appointment.vehicle",
            "appointment.serviceItem", "mechanic"})
    List<ServiceJob> findTop8ByOrderByIdDesc();

    @EntityGraph(attributePaths = {"appointment", "appointment.customer", "appointment.vehicle",
            "appointment.serviceItem", "mechanic"})
    List<ServiceJob> findTop8ByMechanicIdAndStatusInOrderByIdDesc(Long mechanicId, Collection<JobStatus> statuses);

    @EntityGraph(attributePaths = {"appointment", "appointment.customer", "appointment.vehicle",
            "appointment.serviceItem", "mechanic"})
    List<ServiceJob> findByStatusOrderByCompletedAtDesc(JobStatus status);

    @EntityGraph(attributePaths = {"appointment", "appointment.customer", "appointment.vehicle",
            "appointment.serviceItem", "mechanic"})
    List<ServiceJob> findTop20ByMechanicIdOrderByIdDesc(Long mechanicId);

    @EntityGraph(attributePaths = {"appointment", "appointment.customer", "appointment.vehicle",
            "appointment.serviceItem", "mechanic"})
    List<ServiceJob> findTop20ByAppointmentVehicleIdOrderByIdDesc(Long vehicleId);

    long countByStatusIn(Collection<JobStatus> statuses);

    long countByMechanicIdAndStatusIn(Long mechanicId, Collection<JobStatus> statuses);

    boolean existsByMechanicIdAndStatusIn(Long mechanicId, Collection<JobStatus> statuses);

    boolean existsByAppointmentId(Long appointmentId);

    Optional<ServiceJob> findByAppointmentId(Long appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ServiceJob j where j.id = :id")
    Optional<ServiceJob> findByIdForUpdate(@Param("id") Long id);
}
