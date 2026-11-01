package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SourceArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, Long> {
    Optional<SourceArtifact> findBySha256Hash(String sha256Hash);
    Optional<SourceArtifact> findTopByOrderByRetrievalTimestampDesc();
    Optional<SourceArtifact> findTopByArtifactTypeOrderByRetrievalTimestampDesc(String artifactType);
    Optional<SourceArtifact> findFirstByStorageUri(String storageUri);
    boolean existsByStorageUri(String storageUri);
    boolean existsByStorageUriContaining(String fragment);

    @Query(value = "SELECT * FROM source_artifact WHERE storage_uri LIKE %:fragment% AND byte_size > 0 AND payload_blob IS NOT NULL ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<SourceArtifact> findTopByStorageUriContaining(@Param("fragment") String fragment);
}
