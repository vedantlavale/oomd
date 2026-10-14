package com.vsms.repository;

import com.vsms.dto.DailyTotal;
import com.vsms.dto.MethodTotal;
import com.vsms.entity.Payment;
import com.vsms.enums.PaymentMethod;
import com.vsms.enums.PaymentRecordStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"invoice", "invoice.serviceJob", "invoice.serviceJob.appointment",
            "invoice.serviceJob.appointment.customer", "invoice.serviceJob.appointment.vehicle"})
    @Query("""
            select p from Payment p
            where (:status is null or p.status = :status)
              and (:method is null or p.method = :method)
              and (:from is null or p.paidAt >= :from)
              and (:to is null or p.paidAt < :to)
              and (:q is null
                   or lower(p.invoice.serviceJob.appointment.customer.name) like :q
                   or lower(p.invoice.serviceJob.appointment.vehicle.registrationNumber) like :reg
                   or lower(coalesce(p.reference, '')) like :q
                   or p.invoice.id = :qId)
            """)
    Page<Payment> search(@Param("q") String q,
                         @Param("reg") String reg,
                         @Param("qId") Long qId,
                         @Param("status") PaymentRecordStatus status,
                         @Param("method") PaymentMethod method,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id = :invoiceId and p.status = :status")
    BigDecimal sumByInvoiceAndStatus(@Param("invoiceId") Long invoiceId, @Param("status") PaymentRecordStatus status);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    BigDecimal sumByStatus(@Param("status") PaymentRecordStatus status);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal sumByStatusBetween(@Param("status") PaymentRecordStatus status,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            select new com.vsms.dto.MethodTotal(p.method, count(p), sum(p.amount))
            from Payment p
            where p.status = :status and p.paidAt >= :from and p.paidAt < :to
            group by p.method
            order by sum(p.amount) desc
            """)
    List<MethodTotal> totalsByMethod(@Param("status") PaymentRecordStatus status,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query("""
            select new com.vsms.dto.DailyTotal(cast(p.paidAt as LocalDate), count(p), sum(p.amount))
            from Payment p
            where p.status = :status and p.paidAt >= :from and p.paidAt < :to
            group by cast(p.paidAt as LocalDate)
            order by cast(p.paidAt as LocalDate)
            """)
    List<DailyTotal> totalsByDay(@Param("status") PaymentRecordStatus status,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);
}
