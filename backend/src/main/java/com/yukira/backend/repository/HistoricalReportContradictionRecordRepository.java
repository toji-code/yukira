package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.HistoricalReportContradictionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoricalReportContradictionRecordRepository extends JpaRepository<HistoricalReportContradictionRecord, Long> {

    Optional<HistoricalReportContradictionRecord> findByContradictionKey(String contradictionKey);

    List<HistoricalReportContradictionRecord> findAllByOrderByReconciledAtDesc();

    List<HistoricalReportContradictionRecord> findByArea(String area);
}
