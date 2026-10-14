package com.vsms.repository;

import com.vsms.entity.Appointment;
import com.vsms.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"customer", "vehicle", "serviceItem"})
    @Query("""
            select a from Appointment a
            where (:status is null or a.status = :status)
              and (:from is null or a.appointmentDate >= :from)
              and (:to is null or a.appointmentDate <= :to)
              and (:serviceId is null or a.serviceItem.id = :serviceId)
              and (:q is null
                   or lower(a.customer.name) like :q
                   or lower(a.vehicle.registrationNumber) like :reg
                   or a.customer.phone like :q)
            """)
    Page<Appointment> search(@Param("q") String q,
                             @Param("reg") String reg,
                             @Param("status") AppointmentStatus status,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("serviceId") Long serviceId,
                             Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "vehicle", "serviceItem"})
    List<Appointment> findByAppointmentDateAndStatusInOrderByAppointmentTime(
            LocalDate date, Collection<AppointmentStatus> statuses);

    long countByAppointmentDateAndStatusIn(LocalDate date, Collection<AppointmentStatus> statuses);

    long countByCustomerId(Long customerId);

    long countByVehicleId(Long vehicleId);

    long countByServiceItemId(Long serviceItemId);

    /** Number of active appointments for the same service in the same slot (excluding one id). */
    @Query("""
            select count(a) from Appointment a
            where a.serviceItem.id = :serviceId
              and a.appointmentDate = :date
              and a.appointmentTime = :time
              and a.status in :statuses
              and (:excludeId is null or a.id <> :excludeId)
            """)
    long countServiceSlotConflicts(@Param("serviceId") Long serviceId,
                                   @Param("date") LocalDate date,
                                   @Param("time") LocalTime time,
                                   @Param("statuses") Collection<AppointmentStatus> statuses,
                                   @Param("excludeId") Long excludeId);

    /** Number of active appointments for the same vehicle at the same date and time. */
    @Query("""
            select count(a) from Appointment a
            where a.vehicle.id = :vehicleId
              and a.appointmentDate = :date
              and a.appointmentTime = :time
              and a.status in :statuses
              and (:excludeId is null or a.id <> :excludeId)
            """)
    long countVehicleSlotConflicts(@Param("vehicleId") Long vehicleId,
                                   @Param("date") LocalDate date,
                                   @Param("time") LocalTime time,
                                   @Param("statuses") Collection<AppointmentStatus> statuses,
                                   @Param("excludeId") Long excludeId);

    /** Times already taken for a service on a day, used to grey out booked slots. */
    @Query("""
            select a.appointmentTime from Appointment a
            where a.serviceItem.id = :serviceId
              and a.appointmentDate = :date
              and a.status in :statuses
            """)
    List<LocalTime> findTakenTimes(@Param("serviceId") Long serviceId,
                                   @Param("date") LocalDate date,
                                   @Param("statuses") Collection<AppointmentStatus> statuses);
}
