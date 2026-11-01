package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.FifoLotLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FifoLotLedgerRepository extends JpaRepository<FifoLotLedger, Long> {
    List<FifoLotLedger> findByInvestorIdAndSchemeOptionIdAndIsDepletedFalseOrderByAcquisitionDateAsc(Long investorId, Long schemeOptionId);
    List<FifoLotLedger> findByInvestorIdAndSchemeOptionIdOrderByAcquisitionDateAsc(Long investorId, Long schemeOptionId);
    List<FifoLotLedger> findByInvestorIdAndIsDepletedFalseOrderByAcquisitionDateAsc(Long investorId);
    List<FifoLotLedger> findByInvestorIdOrderByAcquisitionDateDesc(Long investorId);
}

