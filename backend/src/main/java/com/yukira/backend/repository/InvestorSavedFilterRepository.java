package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorSavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorSavedFilterRepository extends JpaRepository<InvestorSavedFilter, Long> {

    List<InvestorSavedFilter> findByInvestorOrderByCreatedAtDesc(Investor investor);

    List<InvestorSavedFilter> findByInvestorOrderByUpdatedAtDesc(Investor investor);

    Optional<InvestorSavedFilter> findByIdAndInvestor(Long id, Investor investor);
}

