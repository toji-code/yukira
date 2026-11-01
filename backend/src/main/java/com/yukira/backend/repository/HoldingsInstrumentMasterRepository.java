package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.HoldingsInstrumentMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingsInstrumentMasterRepository extends JpaRepository<HoldingsInstrumentMaster, Long> {
    Optional<HoldingsInstrumentMaster> findByIsin(String isin);
    List<HoldingsInstrumentMaster> findByAssetClass(String assetClass);
    List<HoldingsInstrumentMaster> findBySectorName(String sectorName);
}

