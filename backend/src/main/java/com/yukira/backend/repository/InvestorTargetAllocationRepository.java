package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorTargetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorTargetAllocationRepository extends JpaRepository<InvestorTargetAllocation, Long> {

    List<InvestorTargetAllocation> findByInvestor(Investor investor);

    void deleteByInvestor(Investor investor);
}
