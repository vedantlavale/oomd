package com.vsms.repository;

import com.vsms.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** {@code q} is a lower-case "%text%" pattern, or null for no filter. */
    @Query("""
            select c from Customer c
            where :q is null
               or lower(c.name) like :q
               or lower(c.phone) like :q
               or lower(coalesce(c.email, '')) like :q
            """)
    Page<Customer> search(@Param("q") String q, Pageable pageable);
}
