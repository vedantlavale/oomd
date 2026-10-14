package com.vsms.repository;

import com.vsms.entity.Invoice;
import com.vsms.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    boolean existsByServiceJobId(Long serviceJobId);

    Optional<Invoice> findByServiceJobId(Long serviceJobId);

    @EntityGraph(attributePaths = {"serviceJob", "serviceJob.appointment", "serviceJob.appointment.customer",
            "serviceJob.appointment.vehicle"})
    @Query("""
            select i from Invoice i
            where (:status is null or i.paymentStatus = :status)
              and (:from is null or i.issuedAt >= :from)
              and (:to is null or i.issuedAt < :to)
              and (:q is null
                   or lower(i.serviceJob.appointment.customer.name) like :q
                   or lower(i.serviceJob.appointment.vehicle.registrationNumber) like :reg
                   or i.id = :qId)
            """)
    Page<Invoice> search(@Param("q") String q,
                         @Param("reg") String reg,
                         @Param("qId") Long qId,
                         @Param("status") PaymentStatus status,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(sum(i.grandTotal), 0) from Invoice i where i.issuedAt >= :from and i.issuedAt < :to")
    BigDecimal sumInvoicedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(i) from Invoice i where i.issuedAt >= :from and i.issuedAt < :to")
    long countIssuedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(i.grandTotal - i.amountPaid), 0) from Invoice i where i.paymentStatus <> :paid")
    BigDecimal sumOutstanding(@Param("paid") PaymentStatus paid);

    long countByPaymentStatusNot(PaymentStatus status);
}
