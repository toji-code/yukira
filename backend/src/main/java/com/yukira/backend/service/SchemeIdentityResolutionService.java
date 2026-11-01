package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SchemeIdentityResolutionService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemeRepository schemeRepository;
    private final SchemeIdentifierAliasRepository aliasRepository;

    public SchemeIdentityResolutionService(
        SchemeOptionRepository schemeOptionRepository,
        SchemeRepository schemeRepository,
        SchemeIdentifierAliasRepository aliasRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.schemeRepository = schemeRepository;
        this.aliasRepository = aliasRepository;
    }

    public record SchemeAliasDto(
        String identifierType,
        String identifierValue,
        Boolean isPrimary
    ) {}

    public record ResolvedSchemeIdentityDto(
        Long schemeOptionId,
        Long schemeId,
        String schemeName,
        String amcName,
        String amcCode,
        String category,
        String subCategory,
        String planType,
        String optionType,
        String amfiSchemeCode,
        String isin,
        List<SchemeAliasDto> aliases,
        String resolutionStatus // RESOLVED, AMBIGUOUS, UNRESOLVED
    ) {}

    public ResolvedSchemeIdentityDto resolve(String query) {
        if (query == null || query.trim().isEmpty()) {
            return unresolved(query);
        }
        String clean = query.trim();

        // 1. Direct Alias Lookup
        List<SchemeIdentifierAlias> aliases = aliasRepository.resolveByValue(clean);
        if (!aliases.isEmpty()) {
            SchemeIdentifierAlias alias = aliases.get(0);
            if (alias.getSchemeOption() != null) {
                return toDto(alias.getSchemeOption(), "RESOLVED");
            }
        }

        // 2. AMFI Scheme Code lookup on SchemeOption
        Optional<SchemeOption> optByAmfi = schemeOptionRepository.findByAmfiSchemeCode(clean);
        if (optByAmfi.isPresent()) {
            return toDto(optByAmfi.get(), "RESOLVED");
        }

        // 3. ISIN lookup on SchemeOption
        Optional<SchemeOption> optByIsin = schemeOptionRepository.findByIsin(clean);
        if (optByIsin.isPresent()) {
            return toDto(optByIsin.get(), "RESOLVED");
        }

        // 4. Numeric ID lookup on SchemeOption
        try {
            Long optId = Long.parseLong(clean);
            Optional<SchemeOption> optById = schemeOptionRepository.findById(optId);
            if (optById.isPresent()) {
                return toDto(optById.get(), "RESOLVED");
            }
        } catch (NumberFormatException ignored) {}

        // 5. Scheme Code on Scheme
        Optional<Scheme> schemeByCode = schemeRepository.findByCode(clean);
        if (schemeByCode.isPresent()) {
            Scheme s = schemeByCode.get();
            List<SchemeOption> options = schemeOptionRepository.findBySchemeId(s.getId());
            if (!options.isEmpty()) {
                // Return primary direct growth option if available, otherwise first option
                SchemeOption preferred = options.stream()
                    .filter(o -> "DIRECT".equalsIgnoreCase(o.getSchemePlan().getPlanType()) &&
                                 "GROWTH".equalsIgnoreCase(o.getOptionType()))
                    .findFirst()
                    .orElse(options.get(0));
                return toDto(preferred, "RESOLVED");
            }
        }

        return unresolved(clean);
    }

    private ResolvedSchemeIdentityDto toDto(SchemeOption opt, String status) {
        Scheme scheme = opt.getScheme();
        SchemePlan plan = opt.getSchemePlan();
        Amc amc = scheme != null ? scheme.getAmc() : null;

        List<SchemeIdentifierAlias> aliasList = aliasRepository.findBySchemeOptionIdAndIsActiveTrue(opt.getId());
        List<SchemeAliasDto> aliasDtos = new ArrayList<>();
        for (SchemeIdentifierAlias a : aliasList) {
            aliasDtos.add(new SchemeAliasDto(a.getIdentifierType(), a.getIdentifierValue(), a.getIsPrimary()));
        }

        return new ResolvedSchemeIdentityDto(
            opt.getId(),
            scheme != null ? scheme.getId() : null,
            opt.getOptionName() != null ? opt.getOptionName() : (scheme != null ? scheme.getName() : "Unknown"),
            amc != null ? amc.getName() : "Unknown",
            amc != null ? amc.getCode() : "Unknown",
            scheme != null ? scheme.getCategory() : "Unknown",
            scheme != null ? scheme.getSubCategory() : null,
            plan != null ? plan.getPlanType() : "DIRECT",
            opt.getOptionType() != null ? opt.getOptionType() : "GROWTH",
            opt.getAmfiSchemeCode(),
            opt.getIsin(),
            aliasDtos,
            status
        );
    }

    private ResolvedSchemeIdentityDto unresolved(String query) {
        return new ResolvedSchemeIdentityDto(
            null, null, "Unresolved: " + query, null, null, null, null, null, null, null, null, List.of(), "UNRESOLVED"
        );
    }
}

