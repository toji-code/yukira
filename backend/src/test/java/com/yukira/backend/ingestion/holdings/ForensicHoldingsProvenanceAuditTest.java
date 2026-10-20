package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SecurityIdentifierRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@SpringBootTest
public class ForensicHoldingsProvenanceAuditTest {

    private static final Logger log = LoggerFactory.getLogger(ForensicHoldingsProvenanceAuditTest.class);

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Test
    @Transactional(readOnly = true)
    public void forensicInspectDatabaseState() throws Exception {
        log.info("=== FORENSIC DATABASE INSPECTION ===");

        List<PortfolioSnapshot> snapshots = snapshotRepository.findAll();
        log.info("Total PortfolioSnapshots in DB: {}", snapshots.size());

        for (PortfolioSnapshot snap : snapshots) {
            SchemeOption option = snap.getSchemeOption();
            SourceArtifact artifact = snap.getSourceArtifact();

            String amfiCode = option != null ? option.getAmfiCode() : "N/A";
            String schemeName = option != null && option.getPlan() != null && option.getPlan().getScheme() != null
                    ? option.getPlan().getScheme().getName() : "N/A";
            String optionIsin = option != null ? option.getIsin() : "N/A";

            String isin = "N/A";
            if (option != null) {
                var isinOpt = securityIdentifierRepository.findTopBySecurityIdAndIdType(option.getId(), "ISIN");
                if (isinOpt.isPresent()) {
                    isin = isinOpt.get().getIdValue();
                }
            }

            Long artifactId = artifact != null ? artifact.getId() : null;
            String sha256 = artifact != null ? artifact.getSha256Hash() : "N/A";
            String storageUri = artifact != null ? artifact.getStorageUri() : "N/A";
            Long byteSize = artifact != null ? artifact.getByteSize() : 0L;
            byte[] blob = artifact != null ? artifact.getPayloadBlob() : null;

            String blobSha = "NO_BLOB";
            if (blob != null) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                blobSha = HexFormat.of().formatHex(md.digest(blob));
            }

            log.info("SNAPSHOT #{}: OptionID={} AMFI={} OptionISIN={} SecurityISIN='{}' Scheme='{}' Date={} HoldingsCount={} SumWeights={} | ArtifactID={} SHA256={} StorageURI='{}' ByteSize={} BlobPresent={} BlobSHA={}",
                    snap.getId(),
                    option != null ? option.getId() : "null",
                    amfiCode,
                    optionIsin,
                    isin,
                    schemeName,
                    snap.getPortfolioDate(),
                    snap.getReportedHoldingsCount(),
                    snap.getSumReportedWeights(),
                    artifactId,
                    sha256,
                    storageUri,
                    byteSize,
                    blob != null ? blob.length : 0,
                    blobSha
            );
        }

        log.info("=== SCHEME OPTION ISIN MAPPINGS ===");
        List<String> targetAmfis = List.of("118955", "119062", "122639", "148924", "120503");
        for (String amfi : targetAmfis) {
            var opt = schemeOptionRepository.findByAmfiCode(amfi);
            if (opt.isPresent()) {
                SchemeOption o = opt.get();
                var isinOpt = securityIdentifierRepository.findTopBySecurityIdAndIdType(o.getId(), "ISIN");
                log.info("AMFI {}: OptionID={} OptionISIN={} Name='{}' SecurityISIN='{}'",
                        amfi, o.getId(), o.getIsin(),
                        o.getPlan() != null && o.getPlan().getScheme() != null ? o.getPlan().getScheme().getName() : "N/A",
                        isinOpt.isPresent() ? isinOpt.get().getIdValue() : "NONE"
                );
            } else {
                log.info("AMFI {}: NOT FOUND", amfi);
            }
        }

        log.info("=== SEARCHING ALL PPFAS SCHEMES IN DB ===");
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        for (SchemeOption o : allOptions) {
            String name = o.getPlan() != null && o.getPlan().getScheme() != null ? o.getPlan().getScheme().getName() : "";
            if (name.toLowerCase().contains("parag") || name.toLowerCase().contains("ppfas")) {
                log.info("PPFAS SCHEME: OptionID={} AMFI={} OptionISIN={} Name='{}' Plan='{}' OptionType={}",
                        o.getId(), o.getAmfiCode(), o.getIsin(), name,
                        o.getPlan() != null ? o.getPlan().getPlanType() : "N/A",
                        o.getOptionType()
                );
            }
        }
    }
}
