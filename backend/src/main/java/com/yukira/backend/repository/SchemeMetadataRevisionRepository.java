package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeMetadataRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemeMetadataRevisionRepository extends JpaRepository<SchemeMetadataRevision, Long> {
    List<SchemeMetadataRevision> findBySchemeIdOrderByEffectiveDateDesc(Long schemeId);
    List<SchemeMetadataRevision> findBySchemeOptionIdOrderByEffectiveDateDesc(Long schemeOptionId);
}

