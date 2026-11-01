package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorStressCalculationManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorStressCalculationManifestRepository extends JpaRepository<InvestorStressCalculationManifest, Long> {

    Optional<InvestorStressCalculationManifest> findByCalculationRunId(Long calculationRunId);

    Optional<InvestorStressCalculationManifest> findByCalculationRunCode(String calculationRunCode);

    List<InvestorStressCalculationManifest> findByInvestorIdOrderByCreatedAtDesc(Long investorId);

    List<InvestorStressCalculationManifest> findBySnapshotFingerprint(String snapshotFingerprint);
}
