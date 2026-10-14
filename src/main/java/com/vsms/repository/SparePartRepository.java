package com.vsms.repository;

import com.vsms.entity.SparePart;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SparePartRepository extends JpaRepository<SparePart, Long> {

    boolean existsBySku(String sku);

    Optional<SparePart> findBySku(String sku);

    List<SparePart> findAllByOrderByName();

    @Query("""
            select p from SparePart p
            where (:q is null or lower(p.name) like :q or lower(p.sku) like :q)
              and (:lowOnly = false or p.stockQuantity <= p.reorderLevel)
            """)
    Page<SparePart> search(@Param("q") String q, @Param("lowOnly") boolean lowOnly, Pageable pageable);

    @Query("select count(p) from SparePart p where p.stockQuantity <= p.reorderLevel")
    long countLowStock();

    /** Locks the row so two people cannot take the same stock at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SparePart p where p.id = :id")
    Optional<SparePart> findByIdForUpdate(@Param("id") Long id);
}
