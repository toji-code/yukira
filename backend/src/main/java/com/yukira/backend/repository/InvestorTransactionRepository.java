package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorTransactionRepository extends JpaRepository<InvestorTransaction, Long> {

    List<InvestorTransaction> findByInvestorIdOrderByTransactionDateDesc(Long investorId);

    List<InvestorTransaction> findByInvestorIdOrderByTransactionDateAsc(Long investorId);

    List<InvestorTransaction> findByInvestorIdAndSchemeOptionIdOrderByTransactionDateAsc(Long investorId, Long schemeOptionId);

    List<InvestorTransaction> findByInvestorIdAndIsReversedFalseOrderByTransactionDateAsc(Long investorId);

    default List<InvestorTransaction> findByInvestorAndIsReversedFalseOrderByTransactionDateAsc(com.yukira.backend.domain.entity.Investor investor) {
        return (investor != null && investor.getId() != null)
            ? findByInvestorIdAndIsReversedFalseOrderByTransactionDateAsc(investor.getId())
            : List.of();
    }

    List<InvestorTransaction> findByInvestorIdAndSchemeOptionIdAndIsReversedFalseOrderByTransactionDateAsc(Long investorId, Long schemeOptionId);

    Optional<InvestorTransaction> findByInvestorIdAndDedupHash(Long investorId, String dedupHash);

    boolean existsByInvestorIdAndDedupHash(Long investorId, String dedupHash);

    @Query("""
        SELECT t FROM InvestorTransaction t
        WHERE t.investor.id = :investorId
        AND (:schemeOptionId IS NULL OR t.schemeOption.id = :schemeOptionId)
        AND (:txType IS NULL OR t.transactionType = :txType)
        AND (:startDate IS NULL OR t.transactionDate >= :startDate)
        AND (:endDate IS NULL OR t.transactionDate <= :endDate)
        ORDER BY t.transactionDate DESC, t.id DESC
    """)
    List<InvestorTransaction> findFilteredTransactions(
        @Param("investorId") Long investorId,
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("txType") String txType,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}

