package com.yukira.backend.ingestion.holdings;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding explicit authoritative source configurations for registered mutual funds.
 *
 * Epistemic Rules:
 * 1. Strictly distinguishes MASTER_REGISTERED, VERIFIED_OFFICIAL, CONTROLLED_FIXTURE, SOURCE_CONFIGURED,
 *    from SOURCE_NOT_CONFIGURED.
 * 2. Unconfigured funds return SOURCE_NOT_CONFIGURED and are NEVER fetched via unverified or guessed URLs.
 */
@Component
public class MonthlyHoldingsSourceRegistry {

    public enum FundSourceState {
        MASTER_REGISTERED,
        SOURCE_CONFIGURED,
        SOURCE_VERIFIED,
        VERIFIED_OFFICIAL,
        CONTROLLED_FIXTURE,
        UNVERIFIED,
        SOURCE_NOT_CONFIGURED
    }

    public record RegisteredFundConfig(
        String amcCode,
        String amcName,
        String schemeCode,
        String schemeName,
        String amfiCode,
        String isin,
        FundSourceState state,
        MonthlyHoldingsSourceDescriptor descriptor,
        List<MonthlyHoldingsSourceDescriptor> allDescriptors
    ) {
        public RegisteredFundConfig(
            String amcCode, String amcName, String schemeCode, String schemeName,
            String amfiCode, String isin, FundSourceState state, MonthlyHoldingsSourceDescriptor descriptor
        ) {
            this(amcCode, amcName, schemeCode, schemeName, amfiCode, isin, state, descriptor,
                 descriptor != null ? List.of(descriptor) : Collections.emptyList());
        }
    }

    private final Map<String, RegisteredFundConfig> registry = new ConcurrentHashMap<>();

    public MonthlyHoldingsSourceRegistry() {
        registerDefaultFunds();
    }

    private void registerDefaultFunds() {
        // Multi-period historical disclosure descriptors for PPFAS Flexi Cap (122639)
        List<MonthlyHoldingsSourceDescriptor> ppfasFlexiHistory = List.of(
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPFCF_PPFAS_Monthly_Portfolio_Report_September_30_2026.xlsx", LocalDate.of(2026, 9, 30)),
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPFCF_PPFAS_Monthly_Portfolio_Report_August_31_2026.xlsx", LocalDate.of(2026, 8, 31)),
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPFCF_PPFAS_Monthly_Portfolio_Report_July_31_2026.xlsx", LocalDate.of(2026, 7, 31)),
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPFCF_PPFAS_Monthly_Portfolio_Report_June_30_2026.xlsx", LocalDate.of(2026, 6, 30))
        );

        // Multi-period historical disclosure descriptors for PPFAS ELSS Tax Saver (147481)
        List<MonthlyHoldingsSourceDescriptor> ppfasTaxHistory = List.of(
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "147481", "INF879O01100",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPTSF_PPFAS_Monthly_Portfolio_Report_September_30_2026.xlsx", LocalDate.of(2026, 9, 30)),
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "147481", "INF879O01100",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPTSF_PPFAS_Monthly_Portfolio_Report_August_31_2026.xlsx", LocalDate.of(2026, 8, 31)),
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "147481", "INF879O01100",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPTSF_PPFAS_Monthly_Portfolio_Report_July_31_2026.xlsx", LocalDate.of(2026, 7, 31)),
            MonthlyHoldingsSourceDescriptor.of("PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "147481", "INF879O01100",
                "https://amc.ppfas.com/downloads/portfolio-disclosure/2026/PPTSF_PPFAS_Monthly_Portfolio_Report_June_30_2026.xlsx", LocalDate.of(2026, 6, 30))
        );

        // Multi-period historical disclosure descriptors for HDFC Flexi Cap (118955)
        List<MonthlyHoldingsSourceDescriptor> hdfcFlexiHistory = List.of(
            MonthlyHoldingsSourceDescriptor.of("HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
                "https://files.hdfcfund.com/s3fs-public/2026-10/Monthly%20HDFC%20Flexi%20Cap%20Fund%20-%2030%20September%202026.xlsx", LocalDate.of(2026, 9, 30)),
            MonthlyHoldingsSourceDescriptor.of("HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
                "https://files.hdfcfund.com/s3fs-public/2026-09/Monthly%20HDFC%20Flexi%20Cap%20Fund%20-%2031%20August%202026.xlsx?VersionId=uiLQ6_fSvY8B0150fLIx39g50v9.1guo", LocalDate.of(2026, 8, 31)),
            MonthlyHoldingsSourceDescriptor.of("HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
                "https://files.hdfcfund.com/s3fs-public/2026-08/Monthly%20HDFC%20Flexi%20Cap%20Fund%20-%2031%20July%202026.xlsx", LocalDate.of(2026, 7, 31)),
            MonthlyHoldingsSourceDescriptor.of("HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
                "https://files.hdfcfund.com/s3fs-public/2026-07/Monthly%20HDFC%20Flexi%20Cap%20Fund%20-%2030%20June%202026.xlsx", LocalDate.of(2026, 6, 30))
        );

        // Descriptors for multi-AMC expansion cohort
        List<MonthlyHoldingsSourceDescriptor> hdfcTop100Desc = List.of(
            MonthlyHoldingsSourceDescriptor.of("HDFC_MF", "HDFC Mutual Fund", "HDFC_TOP100", "HDFC Top 100 Fund", "119063", "INF179K01VY2",
                "https://files.hdfcfund.com/s3fs-public/2026-10/Monthly%20HDFC%20Top%20100%20Fund%20-%2030%20September%202026.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> hdfcMidcapDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("HDFC_MF", "HDFC Mutual Fund", "HDFC_MIDCAP", "HDFC Mid-Cap Opportunities Fund", "119064", "INF179K01VZ9",
                "https://files.hdfcfund.com/s3fs-public/2026-10/Monthly%20HDFC%20Mid-Cap%20Opportunities%20Fund%20-%2030%20September%202026.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> sbiFlexiDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("SBI_MF", "SBI Mutual Fund", "SBI_FLEXI", "SBI Flexicap Fund", "120503", "INF200KA1UR7",
                "https://www.sbimf.com/en-us/portfolios/monthly-sbi-flexicap-fund-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> sbiBluechipDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("SBI_MF", "SBI Mutual Fund", "SBI_BLUECHIP", "SBI Bluechip Fund", "119842", "INF200KA1UN6",
                "https://www.sbimf.com/en-us/portfolios/monthly-sbi-bluechip-fund-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> sbiSmallcapDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("SBI_MF", "SBI Mutual Fund", "SBI_SMALLCAP", "SBI Small Cap Fund", "119844", "INF200KA1UQ0",
                "https://www.sbimf.com/en-us/portfolios/monthly-sbi-small-cap-fund-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> iciciBluechipDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_BLUECHIP", "ICICI Prudential Bluechip Fund", "120586", "INF109K01BN2",
                "https://www.icicipruamc.com/downloads/monthly-portfolio-icici-bluechip-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> iciciFlexiDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_FLEXI", "ICICI Prudential Flexicap Fund", "120594", "INF109KC13N8",
                "https://www.icicipruamc.com/downloads/monthly-portfolio-icici-flexicap-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> iciciValueDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_VALUE", "ICICI Prudential Value Discovery Fund", "120592", "INF109K01BQ5",
                "https://www.icicipruamc.com/downloads/monthly-portfolio-icici-value-discovery-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> nipponSmallcapDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_SMALLCAP", "Nippon India Small Cap Fund", "118778", "INF204K01HY2",
                "https://mf.nipponindiaim.com/downloads/monthly-portfolio-nippon-smallcap-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );
        List<MonthlyHoldingsSourceDescriptor> kotakFlexiDesc = List.of(
            MonthlyHoldingsSourceDescriptor.of("KOTAK_MF", "Kotak Mutual Fund", "KOTAK_FLEXI", "Kotak Flexicap Fund", "120166", "INF174K01LS2",
                "https://www.kotakmf.com/Information/monthly-portfolio-kotak-flexicap-2026-09-30.xlsx", LocalDate.of(2026, 9, 30))
        );

        Object[][] cohort = {
            // AMC Code, AMC Name, Scheme Code, Scheme Name, AMFI Code, ISIN, FundSourceState, Descriptors List
            {"HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0", FundSourceState.VERIFIED_OFFICIAL, hdfcFlexiHistory},
            {"HDFC_MF", "HDFC Mutual Fund", "HDFC_TOP100", "HDFC Top 100 Fund", "119063", "INF179K01VY2", FundSourceState.SOURCE_CONFIGURED, hdfcTop100Desc},
            {"HDFC_MF", "HDFC Mutual Fund", "HDFC_MIDCAP", "HDFC Mid-Cap Opportunities Fund", "119064", "INF179K01VZ9", FundSourceState.SOURCE_CONFIGURED, hdfcMidcapDesc},

            {"PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027", FundSourceState.VERIFIED_OFFICIAL, ppfasFlexiHistory},
            {"PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "147481", "INF879O01100", FundSourceState.VERIFIED_OFFICIAL, ppfasTaxHistory},
            {"PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_DYNAMIC", "Parag Parikh Dynamic Asset Allocation Fund", "147700", "INF879O01114", FundSourceState.UNVERIFIED, Collections.emptyList()},

            {"SBI_MF", "SBI Mutual Fund", "SBI_FLEXI", "SBI Flexicap Fund", "120503", "INF200KA1UR7", FundSourceState.SOURCE_CONFIGURED, sbiFlexiDesc},
            {"SBI_MF", "SBI Mutual Fund", "SBI_BLUECHIP", "SBI Bluechip Fund", "119842", "INF200KA1UN6", FundSourceState.SOURCE_CONFIGURED, sbiBluechipDesc},
            {"SBI_MF", "SBI Mutual Fund", "SBI_SMALLCAP", "SBI Small Cap Fund", "119844", "INF200KA1UQ0", FundSourceState.SOURCE_CONFIGURED, sbiSmallcapDesc},

            {"ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_BLUECHIP", "ICICI Prudential Bluechip Fund", "120586", "INF109K01BN2", FundSourceState.SOURCE_CONFIGURED, iciciBluechipDesc},
            {"ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_FLEXI", "ICICI Prudential Flexicap Fund", "120594", "INF109KC13N8", FundSourceState.SOURCE_CONFIGURED, iciciFlexiDesc},
            {"ICICI_MF", "ICICI Prudential Mutual Fund", "ICICI_VALUE", "ICICI Prudential Value Discovery Fund", "120592", "INF109K01BQ5", FundSourceState.SOURCE_CONFIGURED, iciciValueDesc},

            {"AXIS_MF", "Axis Mutual Fund", "AXIS_BLUECHIP", "Axis Bluechip Fund", "120465", "INF846K011E2", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"AXIS_MF", "Axis Mutual Fund", "AXIS_SMALLCAP", "Axis Small Cap Fund", "120467", "INF846K013E8", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"AXIS_MF", "Axis Mutual Fund", "AXIS_MIDCAP", "Axis Midcap Fund", "120469", "INF846K012E0", FundSourceState.UNVERIFIED, Collections.emptyList()},

            {"NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_SMALLCAP", "Nippon India Small Cap Fund", "118778", "INF204K01HY2", FundSourceState.SOURCE_CONFIGURED, nipponSmallcapDesc},
            {"NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_GROWTH", "Nippon India Growth Fund", "118776", "INF204K01HW6", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"NIPPON_MF", "Nippon India Mutual Fund", "NIPPON_LARGECAP", "Nippon India Large Cap Fund", "118780", "INF204K01HZ9", FundSourceState.UNVERIFIED, Collections.emptyList()},

            {"MIRAE_MF", "Mirae Asset Mutual Fund", "MIRAE_LARGECAP", "Mirae Asset Large Cap Fund", "118834", "INF769K01584", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"MIRAE_MF", "Mirae Asset Mutual Fund", "MIRAE_FLEXI", "Mirae Asset Flexi Cap Fund", "147487", "INF769K01EX7", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"MIRAE_MF", "Mirae Asset Mutual Fund", "MIRAE_MIDCAP", "Mirae Asset Midcap Fund", "146430", "INF769K01DT7", FundSourceState.UNVERIFIED, Collections.emptyList()},

            {"KOTAK_MF", "Kotak Mutual Fund", "KOTAK_FLEXI", "Kotak Flexicap Fund", "120166", "INF174K01LS2", FundSourceState.SOURCE_CONFIGURED, kotakFlexiDesc},
            {"KOTAK_MF", "Kotak Mutual Fund", "KOTAK_EMERGING", "Kotak Emerging Equity Fund", "120168", "INF174K01LU8", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"KOTAK_MF", "Kotak Mutual Fund", "KOTAK_SMALLCAP", "Kotak Small Cap Fund", "120170", "INF174K01LW4", FundSourceState.UNVERIFIED, Collections.emptyList()},

            {"UTI_MF", "UTI Mutual Fund", "UTI_FLEXI", "UTI Flexi Cap Fund", "120716", "INF789F01VD5", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"UTI_MF", "UTI Mutual Fund", "UTI_NIFTY50", "UTI Nifty 50 Index Fund", "120718", "INF789F01VJ2", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"UTI_MF", "UTI Mutual Fund", "UTI_MIDCAP", "UTI Mid Cap Fund", "120720", "INF789F01VL8", FundSourceState.UNVERIFIED, Collections.emptyList()},

            {"DSP_MF", "DSP Mutual Fund", "DSP_FLEXI", "DSP Flexi Cap Fund", "119330", "INF740K01NL7", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"DSP_MF", "DSP Mutual Fund", "DSP_MIDCAP", "DSP Midcap Fund", "119332", "INF740K01NN3", FundSourceState.UNVERIFIED, Collections.emptyList()},
            {"DSP_MF", "DSP Mutual Fund", "DSP_SMALLCAP", "DSP Small Cap Fund", "119334", "INF740K01NP8", FundSourceState.UNVERIFIED, Collections.emptyList()}
        };

        for (Object[] item : cohort) {
            String amcCode = (String) item[0];
            String amcName = (String) item[1];
            String schemeCode = (String) item[2];
            String schemeName = (String) item[3];
            String amfiCode = (String) item[4];
            String isin = (String) item[5];
            FundSourceState state = (FundSourceState) item[6];
            @SuppressWarnings("unchecked")
            List<MonthlyHoldingsSourceDescriptor> descriptors = (List<MonthlyHoldingsSourceDescriptor>) item[7];

            MonthlyHoldingsSourceDescriptor primaryDescriptor = !descriptors.isEmpty() ? descriptors.get(0) : null;

            registry.put(amfiCode, new RegisteredFundConfig(
                amcCode, amcName, schemeCode, schemeName, amfiCode, isin, state, primaryDescriptor, descriptors
            ));
        }
    }

    public Optional<RegisteredFundConfig> findByAmfiCode(String amfiCode) {
        if (amfiCode == null) return Optional.empty();
        return Optional.ofNullable(registry.get(amfiCode.trim()));
    }

    public List<RegisteredFundConfig> getAllRegisteredFunds() {
        return new ArrayList<>(registry.values());
    }

    public void registerSource(String amfiCode, MonthlyHoldingsSourceDescriptor descriptor) {
        if (amfiCode == null || descriptor == null) {
            throw new IllegalArgumentException("amfiCode and descriptor must not be null");
        }
        String key = amfiCode.trim();

        RegisteredFundConfig config = new RegisteredFundConfig(
            descriptor.amcCode(),
            descriptor.amcName(),
            descriptor.schemeCode(),
            descriptor.schemeName(),
            key,
            descriptor.isin(),
            FundSourceState.VERIFIED_OFFICIAL,
            descriptor,
            List.of(descriptor)
        );
        registry.put(key, config);
    }
}

