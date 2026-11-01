package com.yukira.backend.universe;

/**
 * The five initial core equity categories authorized for YUKIRA V1 reference universe.
 *
 * M1 Baseline Scope:
 * 1. Flexi Cap
 * 2. Large Cap
 * 3. Large & Mid Cap
 * 4. Mid Cap
 * 5. Small Cap
 */
public enum EquityUniverseCategory {

    FLEXI_CAP("Flexi Cap", "Flexi Cap Fund"),
    LARGE_CAP("Large Cap", "Large Cap Fund"),
    LARGE_AND_MID_CAP("Large & Mid Cap", "Large & Mid Cap Fund"),
    MID_CAP("Mid Cap", "Mid Cap Fund"),
    SMALL_CAP("Small Cap", "Small Cap Fund");

    private final String displayName;
    private final String canonicalSubcategoryName;

    EquityUniverseCategory(String displayName, String canonicalSubcategoryName) {
        this.displayName = displayName;
        this.canonicalSubcategoryName = canonicalSubcategoryName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCanonicalSubcategoryName() {
        return canonicalSubcategoryName;
    }

    /**
     * Deterministically maps source category and subcategory strings from AMFI metadata
     * to one of the five authorized core equity categories.
     *
     * Strict Precedence Rules (to prevent substring collision):
     * 1. Large & Mid Cap (must match before Large Cap or Mid Cap)
     * 2. Large Cap (must not contain 'mid')
     * 3. Mid Cap (must not contain 'large' or 'small')
     * 4. Small Cap
     * 5. Flexi Cap (matches 'flexi cap' or 'flexicap')
     *
     * @param category source category (e.g., "Equity Scheme", "Open Ended Schemes")
     * @param subcategory source subcategory (e.g., "Flexi Cap Fund", "Large Cap Fund")
     * @return EquityUniverseCategory if matched, or null if outside scope / unclassified.
     */
    public static EquityUniverseCategory fromSource(String category, String subcategory) {
        String combined = "";
        if (subcategory != null && !subcategory.isBlank()) {
            combined = subcategory.trim().toLowerCase();
        } else if (category != null && !category.isBlank()) {
            combined = category.trim().toLowerCase();
        }

        if (combined.isEmpty()) {
            return null;
        }

        // Normalize delimiters and connectives
        String normalized = combined
            .replace("&", " and ")
            .replaceAll("\\s+", " ")
            .trim();

        // 1. Large & Mid Cap (evaluated before separate Large Cap and Mid Cap)
        if (normalized.contains("large and mid") || normalized.contains("large & mid") || normalized.contains("large and mid cap")) {
            return LARGE_AND_MID_CAP;
        }

        // 2. Large Cap (must not contain mid)
        if (normalized.contains("large cap") && !normalized.contains("mid")) {
            return LARGE_CAP;
        }

        // 3. Mid Cap (must not contain large or small)
        if (normalized.contains("mid cap") && !normalized.contains("large") && !normalized.contains("small")) {
            return MID_CAP;
        }

        // 4. Small Cap
        if (normalized.contains("small cap")) {
            return SMALL_CAP;
        }

        // 5. Flexi Cap
        if (normalized.contains("flexi cap") || normalized.contains("flexicap")) {
            return FLEXI_CAP;
        }

        return null;
    }

    /**
     * Categorizes scheme with fallback to scheme name when subcategory is omitted in feed.
     */
    public static EquityUniverseCategory categorize(String category, String subcategory, String schemeName) {
        EquityUniverseCategory cat = fromSource(category, subcategory);
        if (cat == null && schemeName != null && !schemeName.isBlank()) {
            cat = fromSource(null, schemeName);
        }
        return cat;
    }
}
