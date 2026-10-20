package com.yukira.backend.ingestion.holdings;

/**
 * Trigger source for a monthly holdings refresh run.
 */
public enum TriggerType {
    SCHEDULED,
    MANUAL,
    TEST
}
