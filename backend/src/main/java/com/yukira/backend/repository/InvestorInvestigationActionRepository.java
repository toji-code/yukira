package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorInvestigationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorInvestigationActionRepository extends JpaRepository<InvestorInvestigationAction, Long> {

    List<InvestorInvestigationAction> findByInvestorOrderByCreatedAtDesc(Investor investor);

    List<InvestorInvestigationAction> findByInvestorAndStatusOrderByCreatedAtDesc(Investor investor, String status);

    List<InvestorInvestigationAction> findByInvestorAndStatusInOrderByPriorityDescCreatedAtDesc(Investor investor, List<String> statuses);

    Optional<InvestorInvestigationAction> findByIdAndInvestor(Long id, Investor investor);
}

