package com.vsms.repository;

import com.vsms.entity.PartUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartUsageRepository extends JpaRepository<PartUsage, Long> {

    boolean existsBySparePartId(Long sparePartId);
}
