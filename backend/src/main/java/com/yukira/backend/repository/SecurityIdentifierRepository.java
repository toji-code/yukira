package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SecurityIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityIdentifierRepository extends JpaRepository<SecurityIdentifier, Long> {

    List<SecurityIdentifier> findBySecurityId(Long securityId);

    Optional<SecurityIdentifier> findTopBySecurityIdAndIdType(Long securityId, String idType);

    List<SecurityIdentifier> findBySecurityIdInAndIdType(List<Long> securityIds, String idType);
}
