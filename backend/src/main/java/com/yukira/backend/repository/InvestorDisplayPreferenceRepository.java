package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorDisplayPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestorDisplayPreferenceRepository extends JpaRepository<InvestorDisplayPreference, Long> {

    Optional<InvestorDisplayPreference> findByInvestor(Investor investor);
}

