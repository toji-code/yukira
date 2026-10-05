package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityRepository extends JpaRepository<Security, Long> {

    Optional<Security> findByCanonicalName(String canonicalName);
}
