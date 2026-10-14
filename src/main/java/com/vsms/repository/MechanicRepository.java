package com.vsms.repository;

import com.vsms.entity.Mechanic;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MechanicRepository extends JpaRepository<Mechanic, Long> {

    List<Mechanic> findByActiveTrueOrderByName();

    List<Mechanic> findAllByOrderByName();

    @Query("""
            select m from Mechanic m
            where (:q is null or lower(m.name) like :q or lower(m.specialization) like :q)
              and (:active is null or m.active = :active)
            """)
    Page<Mechanic> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
