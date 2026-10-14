package com.vsms.repository;

import com.vsms.entity.UserAccount;
import com.vsms.enums.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "mechanic")
    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByMechanicId(Long mechanicId);

    boolean existsByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    @EntityGraph(attributePaths = "mechanic")
    List<UserAccount> findAllByOrderByUsername();
}
