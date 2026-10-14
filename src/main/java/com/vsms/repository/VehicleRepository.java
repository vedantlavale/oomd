package com.vsms.repository;

import com.vsms.entity.Vehicle;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    boolean existsByRegistrationNumber(String registrationNumber);

    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);

    long countByCustomerId(Long customerId);

    @EntityGraph(attributePaths = "customer")
    List<Vehicle> findByCustomerIdOrderByRegistrationNumber(Long customerId);

    @EntityGraph(attributePaths = "customer")
    List<Vehicle> findAllByOrderByRegistrationNumber();

    /**
     * {@code q} is a lower-case "%text%" pattern and {@code reg} the same text without spaces and
     * hyphens (to match registration numbers); both are null for no filter.
     */
    @EntityGraph(attributePaths = "customer")
    @Query("""
            select v from Vehicle v join v.customer c
            where :q is null
               or lower(v.registrationNumber) like :reg
               or lower(c.name) like :q
               or lower(v.manufacturer) like :q
               or lower(v.model) like :q
            """)
    Page<Vehicle> search(@Param("q") String q, @Param("reg") String reg, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);
}
