-- Flyway Migration V27: Update Investor Watchlist Option Uniqueness
-- Drop legacy (investor_id, scheme_id) uniqueness constraint so investors can save distinct scheme options of the same scheme.
-- Replace with canonical uniqueness on (investor_id, scheme_option_id) when option is present, and (investor_id, scheme_id) when option is null.

ALTER TABLE investor_watchlist DROP CONSTRAINT IF EXISTS uq_investor_watchlist;

CREATE UNIQUE INDEX IF NOT EXISTS uq_investor_watchlist_option
ON investor_watchlist(investor_id, scheme_option_id)
WHERE scheme_option_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_investor_watchlist_scheme_null_opt
ON investor_watchlist(investor_id, scheme_id)
WHERE scheme_option_id IS NULL;
