package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorWatchlistRepository extends JpaRepository<InvestorWatchlist, Long> {
    List<InvestorWatchlist> findByInvestorId(Long investorId);
    Optional<InvestorWatchlist> findByInvestorIdAndSchemeId(Long investorId, Long schemeId);
}
