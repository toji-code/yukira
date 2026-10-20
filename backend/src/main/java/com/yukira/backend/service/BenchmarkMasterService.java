package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.repository.BenchmarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BenchmarkMasterService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkMasterService.class);

    private final BenchmarkRepository benchmarkRepository;

    public BenchmarkMasterService(BenchmarkRepository benchmarkRepository) {
        this.benchmarkRepository = benchmarkRepository;
    }

    public record BenchmarkDefinition(
        String code,
        String name,
        String provider,
        String returnVariant,
        String categoryType
    ) {}

    public static final List<BenchmarkDefinition> CANONICAL_BENCHMARK_MASTER = List.of(
        new BenchmarkDefinition("NIFTY_500_TRI", "Nifty 500 Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_BROAD"),
        new BenchmarkDefinition("NIFTY_50_TRI", "Nifty 50 Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_LARGE_CAP"),
        new BenchmarkDefinition("NIFTY_100_TRI", "Nifty 100 Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_LARGE_CAP"),
        new BenchmarkDefinition("NIFTY_MIDCAP_150_TRI", "Nifty Midcap 150 Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_MID_CAP"),
        new BenchmarkDefinition("NIFTY_SMALLCAP_250_TRI", "Nifty Smallcap 250 Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_SMALL_CAP"),
        new BenchmarkDefinition("NIFTY_LARGEMIDCAP_250_TRI", "Nifty LargeMidcap 250 Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_LARGE_MID"),
        new BenchmarkDefinition("NIFTY_BANK_TRI", "Nifty Bank Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_SECTORAL"),
        new BenchmarkDefinition("NIFTY_IT_TRI", "Nifty IT Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_SECTORAL"),
        new BenchmarkDefinition("NIFTY_INFRA_TRI", "Nifty Infrastructure Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_SECTORAL"),
        new BenchmarkDefinition("NIFTY_PHARMA_TRI", "Nifty Pharma Total Returns Index", "NSE Indices Limited", "TRI", "EQUITY_SECTORAL"),
        new BenchmarkDefinition("CRISIL_HYBRID_3565_TRI", "CRISIL Hybrid 35+65 Aggressive Index", "CRISIL Limited", "TRI", "HYBRID_AGGRESSIVE"),
        new BenchmarkDefinition("CRISIL_COMPOSITE_BOND_INDEX", "CRISIL Composite Bond Fund Index", "CRISIL Limited", "TRI", "DEBT_COMPOSITE"),
        new BenchmarkDefinition("CRISIL_LIQUID_DEBT_INDEX", "CRISIL Liquid Debt Index", "CRISIL Limited", "TRI", "DEBT_LIQUID"),
        new BenchmarkDefinition("CRISIL_OVERNIGHT_FUND_INDEX", "CRISIL Overnight Fund Index", "CRISIL Limited", "TRI", "DEBT_OVERNIGHT")
    );

    /**
     * Idempotently seeds the canonical benchmark master registry.
     */
    @Transactional
    public List<Benchmark> ensureCanonicalBenchmarkMaster() {
        List<Benchmark> registered = new ArrayList<>();
        for (BenchmarkDefinition def : CANONICAL_BENCHMARK_MASTER) {
            Optional<Benchmark> existing = benchmarkRepository.findByCode(def.code());
            if (existing.isPresent()) {
                registered.add(existing.get());
            } else {
                Benchmark created = benchmarkRepository.save(new Benchmark(
                    def.code(),
                    def.name(),
                    def.provider(),
                    def.returnVariant()
                ));
                registered.add(created);
                log.info("Registered canonical benchmark: code={}, name={}, provider={}", def.code(), def.name(), def.provider());
            }
        }
        return registered;
    }
}
