package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MethodologyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MethodologyVersionRepository extends JpaRepository<MethodologyVersion, Long> {
    Optional<MethodologyVersion> findByMethodologyCodeAndVersionTag(String methodologyCode, String versionTag);
}
