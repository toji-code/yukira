package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SourceArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, Long> {
    Optional<SourceArtifact> findBySha256Hash(String sha256Hash);
    Optional<SourceArtifact> findTopByOrderByRetrievalTimestampDesc();
    Optional<SourceArtifact> findTopByArtifactTypeOrderByRetrievalTimestampDesc(String artifactType);
}
