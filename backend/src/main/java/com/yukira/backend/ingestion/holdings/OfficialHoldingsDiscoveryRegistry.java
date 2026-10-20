package com.yukira.backend.ingestion.holdings;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry managing official AMC holdings discovery clients.
 */
@Component
public class OfficialHoldingsDiscoveryRegistry {

    private final List<OfficialHoldingsDiscoveryClient> clients;

    public OfficialHoldingsDiscoveryRegistry(List<OfficialHoldingsDiscoveryClient> clients) {
        this.clients = clients != null ? clients : Collections.emptyList();
    }

    public Optional<OfficialHoldingsDiscoveryClient> findDiscoverer(String amcCode, String amfiCode) {
        return clients.stream()
            .filter(c -> c.supports(amcCode, amfiCode))
            .findFirst();
    }

    public List<OfficialHoldingsDiscoveryClient> getAllClients() {
        return Collections.unmodifiableList(clients);
    }
}
