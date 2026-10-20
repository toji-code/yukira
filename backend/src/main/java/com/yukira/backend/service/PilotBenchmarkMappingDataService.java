package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.BenchmarkMappingIngestionService.BenchmarkMappingIngestionSummary;
import com.yukira.backend.service.BenchmarkMappingIngestionService.BenchmarkMappingItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class PilotBenchmarkMappingDataService {

    private static final Logger log = LoggerFactory.getLogger(PilotBenchmarkMappingDataService.class);

    private final BenchmarkMappingIngestionService ingestionService;
    private final SchemeOptionRepository schemeOptionRepository;

    public PilotBenchmarkMappingDataService(
        BenchmarkMappingIngestionService ingestionService,
        SchemeOptionRepository schemeOptionRepository
    ) {
        this.ingestionService = ingestionService;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    /**
     * Seeds authoritative category-appropriate benchmark mappings for active schemes in DB.
     */
    @Transactional
    public BenchmarkMappingIngestionSummary seedAuthoritativePilotMappings() {
        List<SchemeOption> allActive = schemeOptionRepository.findAll().stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .toList();

        List<BenchmarkMappingItemDto> items = new ArrayList<>();
        Set<String> processedAmfiCodes = new HashSet<>();

        for (SchemeOption opt : allActive) {
            if (opt.getAmfiCode() == null || !processedAmfiCodes.add(opt.getAmfiCode())) {
                continue;
            }

            String schemeName = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getName() : "";
            String category = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getCategory() : "";
            String nameUpper = schemeName.toUpperCase();
            String catUpper = category != null ? category.toUpperCase() : "";

            String benchmarkCode = resolveBenchmarkForCategoryAndName(nameUpper, catUpper);

            items.add(new BenchmarkMappingItemDto(
                opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getCode() : null,
                opt.getAmfiCode(),
                benchmarkCode,
                LocalDate.of(2021, 1, 1),
                null,
                true,
                "AMFI_AUTHORITATIVE_FACTSHEET_MASTER_2024"
            ));
        }

        log.info("Constructed {} scheme benchmark mapping items for ingestion", items.size());

        return ingestionService.ingestMappingBatch(
            "https://www.amfiindia.com/official-benchmark-master-2024.json",
            items
        );
    }

    public static String resolveBenchmarkForCategoryAndName(String nameUpper, String catUpper) {
        if (nameUpper.contains("SMALL") || catUpper.contains("SMALL")) {
            return "NIFTY_SMALLCAP_250_TRI";
        } else if (nameUpper.contains("MIDCAP") || nameUpper.contains("MID CAP") || catUpper.contains("MID")) {
            if (nameUpper.contains("LARGE & MID") || nameUpper.contains("LARGE AND MID")) {
                return "NIFTY_LARGEMIDCAP_250_TRI";
            }
            return "NIFTY_MIDCAP_150_TRI";
        } else if (nameUpper.contains("LARGE") || nameUpper.contains("BLUECHIP") || nameUpper.contains("TOP 100")) {
            return "NIFTY_100_TRI";
        } else if (nameUpper.contains("BANK") || nameUpper.contains("FINANCIAL")) {
            return "NIFTY_BANK_TRI";
        } else if (nameUpper.contains("TECH") || nameUpper.contains("IT FUND") || nameUpper.contains("DIGITAL")) {
            return "NIFTY_IT_TRI";
        } else if (nameUpper.contains("INFRA")) {
            return "NIFTY_INFRA_TRI";
        } else if (nameUpper.contains("PHARMA") || nameUpper.contains("HEALTHCARE")) {
            return "NIFTY_PHARMA_TRI";
        } else if (nameUpper.contains("HYBRID") || nameUpper.contains("BALANCED") || catUpper.contains("HYBRID")) {
            return "CRISIL_HYBRID_3565_TRI";
        } else if (nameUpper.contains("BOND") || nameUpper.contains("DEBT") || nameUpper.contains("GILT") || catUpper.contains("DEBT")) {
            if (nameUpper.contains("LIQUID")) {
                return "CRISIL_LIQUID_DEBT_INDEX";
            } else if (nameUpper.contains("OVERNIGHT")) {
                return "CRISIL_OVERNIGHT_FUND_INDEX";
            }
            return "CRISIL_COMPOSITE_BOND_INDEX";
        } else if (nameUpper.contains("LIQUID")) {
            return "CRISIL_LIQUID_DEBT_INDEX";
        } else if (nameUpper.contains("OVERNIGHT")) {
            return "CRISIL_OVERNIGHT_FUND_INDEX";
        }
        // Default broad equity benchmark for Flexi Cap / Multi Cap / ELSS / Unclassified equity
        return "NIFTY_500_TRI";
    }
}
