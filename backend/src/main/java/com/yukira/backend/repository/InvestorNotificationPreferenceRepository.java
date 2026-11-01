package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestorNotificationPreferenceRepository extends JpaRepository<InvestorNotificationPreference, Long> {

    Optional<InvestorNotificationPreference> findByInvestor(Investor investor);
}

