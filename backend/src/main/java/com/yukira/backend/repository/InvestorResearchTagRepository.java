package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorResearchTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorResearchTagRepository extends JpaRepository<InvestorResearchTag, Long> {

    List<InvestorResearchTag> findByInvestorOrderByCreatedAtDesc(Investor investor);

    default List<InvestorResearchTag> findByInvestor(Investor investor) {
        return findByInvestorOrderByCreatedAtDesc(investor);
    }

    default java.util.Optional<InvestorResearchTag> findByInvestorAndTagNameAndTargetTypeAndTargetId(Investor investor, String tagName, String targetType, String targetId) {
        return findByInvestorAndTargetTypeAndTargetId(investor, targetType, targetId).stream()
            .filter(t -> t.getTagName().equalsIgnoreCase(tagName))
            .findFirst();
    }

    List<InvestorResearchTag> findByInvestorAndTargetTypeAndTargetId(Investor investor, String targetType, String targetId);

    void deleteByInvestorAndTargetTypeAndTargetIdAndTagName(Investor investor, String targetType, String targetId, String tagName);
}

