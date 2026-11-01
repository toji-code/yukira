package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorInAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorInAppNotificationRepository extends JpaRepository<InvestorInAppNotification, Long> {

    List<InvestorInAppNotification> findByInvestorAndIsArchivedFalseOrderByCreatedAtDesc(Investor investor);

    List<InvestorInAppNotification> findByInvestorAndIsReadFalseAndIsArchivedFalseOrderByCreatedAtDesc(Investor investor);

    long countByInvestorAndIsReadFalseAndIsArchivedFalse(Investor investor);

    Optional<InvestorInAppNotification> findByIdAndInvestor(Long id, Investor investor);
}

