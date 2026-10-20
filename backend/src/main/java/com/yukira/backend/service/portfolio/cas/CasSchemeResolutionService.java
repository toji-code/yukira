package com.yukira.backend.service.portfolio.cas;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SecurityIdentifier;
import com.yukira.backend.dto.portfolio.CasImportParsedRecordDto;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CasSchemeResolutionService {

    public record ResolutionResult(
        SchemeOption schemeOption,
        String status, // RESOLVED, AMBIGUOUS, UNRESOLVED, INVALID
        String method, // ISIN_EXACT, AMFI_EXACT, NAME_EXACT, UNRESOLVED
        String notes
    ) {}

    private final SchemeOptionRepository schemeOptionRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;

    public CasSchemeResolutionService(
        SchemeOptionRepository schemeOptionRepository,
        SecurityIdentifierRepository securityIdentifierRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
    }

    public ResolutionResult resolve(CasImportParsedRecordDto record) {
        if (record == null) {
            return new ResolutionResult(null, "INVALID", "UNRESOLVED", "Parsed record is null.");
        }

        // 1. ISIN Exact Resolution
        if (record.isin() != null && !record.isin().isBlank()) {
            String isin = record.isin().trim().toUpperCase(Locale.ROOT);
            Optional<SchemeOption> byIsin = schemeOptionRepository.findByIsin(isin);
            if (byIsin.isPresent()) {
                return new ResolutionResult(byIsin.get(), "RESOLVED", "ISIN_EXACT", "Resolved by exact ISIN match: " + isin);
            }

            Optional<SecurityIdentifier> secIdOpt = securityIdentifierRepository.findByIdTypeAndIdValue("ISIN", isin);
            if (secIdOpt.isPresent()) {
                Long securityId = secIdOpt.get().getSecurity().getId();
                List<SchemeOption> allOptions = schemeOptionRepository.findAll();
                for (SchemeOption option : allOptions) {
                    if (option.getIsin() != null && option.getIsin().equalsIgnoreCase(isin)) {
                        return new ResolutionResult(option, "RESOLVED", "ISIN_EXACT", "Resolved by Security ISIN match: " + isin);
                    }
                }
            }
        }

        // 2. AMFI Scheme Code Resolution
        if (record.amfiCode() != null && !record.amfiCode().isBlank()) {
            String amfi = record.amfiCode().trim();
            Optional<SchemeOption> byAmfi = schemeOptionRepository.findByAmfiCode(amfi);
            if (byAmfi.isPresent()) {
                return new ResolutionResult(byAmfi.get(), "RESOLVED", "AMFI_EXACT", "Resolved by exact AMFI code match: " + amfi);
            }
        }

        // 3. Name Exact Resolution
        if (record.rawSchemeName() != null && !record.rawSchemeName().isBlank()) {
            String targetName = record.rawSchemeName().trim();
            List<SchemeOption> options = schemeOptionRepository.findAll();
            SchemeOption exactMatch = null;
            int matchCount = 0;

            for (SchemeOption opt : options) {
                String fullOptName = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                    ? opt.getPlan().getScheme().getName() + " " + opt.getPlan().getPlanType() + " " + opt.getOptionType()
                    : "";
                String schemeName = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                    ? opt.getPlan().getScheme().getName()
                    : "";

                if (fullOptName.equalsIgnoreCase(targetName) || schemeName.equalsIgnoreCase(targetName)) {
                    exactMatch = opt;
                    matchCount++;
                }
            }

            if (matchCount == 1 && exactMatch != null) {
                return new ResolutionResult(exactMatch, "RESOLVED", "NAME_EXACT", "Resolved by exact scheme name match: " + targetName);
            } else if (matchCount > 1) {
                return new ResolutionResult(null, "AMBIGUOUS", "UNRESOLVED", "Multiple candidate schemes matched raw name: " + targetName);
            }
        }

        return new ResolutionResult(null, "UNRESOLVED", "UNRESOLVED", "Unable to resolve fund scheme identity. Requires manual verification.");
    }
}
