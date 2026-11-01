package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorResearchThesisVersion;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.InvestorRepository;
import com.yukira.backend.repository.InvestorResearchThesisVersionRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing versioned investor research theses, empirical hypothesis evolution,
 * and evidence linkage (Task 308).
 */
@Service
@Transactional
public class InvestorResearchThesisService {

    private static final Logger log = LoggerFactory.getLogger(InvestorResearchThesisService.class);

    private final InvestorResearchThesisVersionRepository thesisVersionRepository;
    private final InvestorRepository investorRepository;
    private final SchemeRepository schemeRepository;
    private final SchemeOptionRepository schemeOptionRepository;

    public record ResearchThesisVersionDto(
        Long id,
        Long investorId,
        Long schemeId,
        Long schemeOptionId,
        String thesisId,
        int versionNumber,
        String author,
        String thesisTitle,
        String thesisBody,
        String linkedCondition,
        String linkedEvidence,
        String revisionReason,
        OffsetDateTime createdAt
    ) {}

    public InvestorResearchThesisService(
        InvestorResearchThesisVersionRepository thesisVersionRepository,
        InvestorRepository investorRepository,
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository
    ) {
        this.thesisVersionRepository = thesisVersionRepository;
        this.investorRepository = investorRepository;
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    public ResearchThesisVersionDto recordThesisVersion(
        Long investorId,
        Long schemeId,
        Long schemeOptionId,
        String thesisId,
        String author,
        String title,
        String body,
        String linkedCondition,
        String linkedEvidence,
        String revisionReason
    ) {
        Investor investor = investorRepository.findById(investorId)
            .orElseThrow(() -> new IllegalArgumentException("Investor not found: " + investorId));

        String actualThesisId = (thesisId != null && !thesisId.isBlank())
            ? thesisId
            : "THESIS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<InvestorResearchThesisVersion> existingVersions =
            thesisVersionRepository.findByInvestorIdAndThesisIdOrderByVersionNumberDesc(investorId, actualThesisId);

        int nextVersion = existingVersions.isEmpty() ? 1 : existingVersions.get(0).getVersionNumber() + 1;

        InvestorResearchThesisVersion version = new InvestorResearchThesisVersion();
        version.setInvestor(investor);
        version.setThesisId(actualThesisId);
        version.setVersionNumber(nextVersion);
        version.setAuthor(author != null ? author : "Investor");
        version.setThesisTitle(title != null ? title : "Untitled Investment Thesis");
        version.setThesisBody(body != null ? body : "");
        version.setLinkedCondition(linkedCondition);
        version.setLinkedEvidence(linkedEvidence);
        version.setRevisionReason(revisionReason);

        if (schemeId != null) {
            schemeRepository.findById(schemeId).ifPresent(version::setScheme);
        }
        if (schemeOptionId != null) {
            schemeOptionRepository.findById(schemeOptionId).ifPresent(opt -> {
                version.setSchemeOption(opt);
                if (version.getScheme() == null) {
                    version.setScheme(opt.getScheme());
                }
            });
        }

        InvestorResearchThesisVersion saved = thesisVersionRepository.save(version);
        log.info("Recorded research thesis version {} (thesisId={}, version={}) for investor {}",
            saved.getId(), saved.getThesisId(), saved.getVersionNumber(), investorId);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ResearchThesisVersionDto> getThesisHistory(Long investorId, String thesisId) {
        return thesisVersionRepository.findByInvestorIdAndThesisIdOrderByVersionNumberDesc(investorId, thesisId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ResearchThesisVersionDto> getAllInvestorTheses(Long investorId) {
        return thesisVersionRepository.findByInvestorIdOrderByCreatedAtDesc(investorId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * Masks Permanent Account Number (PAN) according to statutory PII safeguards (Task 346).
     */
    public static String maskPan(String rawPan) {
        if (rawPan == null || rawPan.trim().length() < 10) {
            return "XXXXX*****";
        }
        String clean = rawPan.trim().toUpperCase();
        return clean.substring(0, 2) + "XXXXX" + clean.substring(7);
    }

    private ResearchThesisVersionDto toDto(InvestorResearchThesisVersion v) {
        return new ResearchThesisVersionDto(
            v.getId(),
            v.getInvestor() != null ? v.getInvestor().getId() : null,
            v.getScheme() != null ? v.getScheme().getId() : null,
            v.getSchemeOption() != null ? v.getSchemeOption().getId() : null,
            v.getThesisId(),
            v.getVersionNumber(),
            v.getAuthor(),
            v.getThesisTitle(),
            v.getThesisBody(),
            v.getLinkedCondition(),
            v.getLinkedEvidence(),
            v.getRevisionReason(),
            v.getCreatedAt()
        );
    }
}

