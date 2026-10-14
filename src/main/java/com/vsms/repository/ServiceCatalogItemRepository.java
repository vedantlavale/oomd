package com.vsms.repository;

import com.vsms.entity.ServiceCatalogItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceCatalogItemRepository extends JpaRepository<ServiceCatalogItem, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<ServiceCatalogItem> findByNameIgnoreCase(String name);

    List<ServiceCatalogItem> findByActiveTrueOrderByName();

    List<ServiceCatalogItem> findAllByOrderByName();

    @Query("""
            select s from ServiceCatalogItem s
            where (:q is null or lower(s.name) like :q or lower(coalesce(s.description, '')) like :q)
              and (:active is null or s.active = :active)
            """)
    Page<ServiceCatalogItem> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceCatalogItem s where s.id = :id")
    Optional<ServiceCatalogItem> findByIdForUpdate(@Param("id") Long id);
}
