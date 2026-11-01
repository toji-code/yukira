package com.yukira.backend.service.discovery;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FundDiscoveryFilterService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;

    public FundDiscoveryFilterService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
    }

    public record DiscoveryFilterCriteria(
        List<String> amcCodes,
        List<String> categories,
        String planType,
        String optionType,
        Integer minHistoryYears,
        String dataReadiness,
        String searchTerm
    ) {}

    public record FundDiscoveryItemDto(
        Long schemeOptionId,
        Long schemeId,
        String schemeName,
        String optionName,
        String amcName,
        String amcCode,
        String category,
        String subCategory,
        String planType,
        String optionType,
        String amfiSchemeCode,
        String isin,
        LocalDate earliestNavDate,
        LocalDate latestNavDate,
        long observationCount,
        String dataReadinessStatus, // COMPLETE, PARTIAL, INSUFFICIENT
        boolean isEligibleFor1Y,
        boolean isEligibleFor3Y,
        boolean isEligibleFor5Y
    ) {}

    public List<FundDiscoveryItemDto> searchFunds(DiscoveryFilterCriteria criteria, int limit) {
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        List<FundDiscoveryItemDto> results = new ArrayList<>();

        LocalDate today = LocalDate.now();

        for (SchemeOption opt : allOptions) {
            Scheme scheme = opt.getScheme();
            SchemePlan plan = opt.getSchemePlan();
            Amc amc = scheme != null ? scheme.getAmc() : null;

            if (scheme == null) continue;

            // AMC filter
            if (criteria.amcCodes() != null && !criteria.amcCodes().isEmpty()) {
                if (amc == null || !criteria.amcCodes().contains(amc.getCode())) {
                    continue;
                }
            }

            // Category filter
            if (criteria.categories() != null && !criteria.categories().isEmpty()) {
                if (scheme.getCategory() == null || !criteria.categories().contains(scheme.getCategory())) {
                    continue;
                }
            }

            // Plan Type filter
            if (criteria.planType() != null && !criteria.planType().isBlank()) {
                String pt = plan != null ? plan.getPlanType() : "DIRECT";
                if (!criteria.planType().equalsIgnoreCase(pt)) {
                    continue;
                }
            }

            // Option Type filter
            if (criteria.optionType() != null && !criteria.optionType().isBlank()) {
                String ot = opt.getOptionType() != null ? opt.getOptionType() : "GROWTH";
                if (!criteria.optionType().equalsIgnoreCase(ot)) {
                    continue;
                }
            }

            // Search Term filter
            if (criteria.searchTerm() != null && !criteria.searchTerm().isBlank()) {
                String term = criteria.searchTerm().trim().toLowerCase();
                boolean matches = false;
                if (scheme.getName() != null && scheme.getName().toLowerCase().contains(term)) matches = true;
                if (opt.getOptionName() != null && opt.getOptionName().toLowerCase().contains(term)) matches = true;
                if (opt.getAmfiSchemeCode() != null && opt.getAmfiSchemeCode().toLowerCase().contains(term)) matches = true;
                if (opt.getIsin() != null && opt.getIsin().toLowerCase().contains(term)) matches = true;
                if (amc != null && amc.getName() != null && amc.getName().toLowerCase().contains(term)) matches = true;

                if (!matches) continue;
            }

            // Observation summary
            long obsCount = navObservationRepository.countBySchemeOptionId(opt.getId());
            var latestObs = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(opt.getId());
            LocalDate latestDate = latestObs.map(NavObservation::getEffectiveDate).orElse(null);

            // Compute readiness
            String readiness;
            boolean eligible1Y = obsCount >= 200;
            boolean eligible3Y = obsCount >= 600;
            boolean eligible5Y = obsCount >= 1000;

            if (eligible5Y) {
                readiness = "COMPLETE";
            } else if (eligible1Y) {
                readiness = "PARTIAL";
            } else {
                readiness = "INSUFFICIENT";
            }

            if (criteria.minHistoryYears() != null) {
                if (criteria.minHistoryYears() >= 5 && !eligible5Y) continue;
                if (criteria.minHistoryYears() >= 3 && !eligible3Y) continue;
                if (criteria.minHistoryYears() >= 1 && !eligible1Y) continue;
            }

            if (criteria.dataReadiness() != null && !criteria.dataReadiness().isBlank()) {
                if (!criteria.dataReadiness().equalsIgnoreCase(readiness)) {
                    continue;
                }
            }

            results.add(new FundDiscoveryItemDto(
                opt.getId(),
                scheme.getId(),
                scheme.getName(),
                opt.getOptionName(),
                amc != null ? amc.getName() : "Unknown",
                amc != null ? amc.getCode() : "Unknown",
                scheme.getCategory(),
                scheme.getSubCategory(),
                plan != null ? plan.getPlanType() : "DIRECT",
                opt.getOptionType() != null ? opt.getOptionType() : "GROWTH",
                opt.getAmfiSchemeCode(),
                opt.getIsin(),
                null,
                latestDate,
                obsCount,
                readiness,
                eligible1Y,
                eligible3Y,
                eligible5Y
            ));

            if (results.size() >= limit) break;
        }

        return results;
    }
}

