-- Flyway Migration V26: Create Investor Research Workspace Model & Investigation Context
-- Schema authority for extending investor_watchlist with scheme_option_id and research note attributes.

ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS scheme_option_id BIGINT REFERENCES scheme_option(id);
ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS reason_saved TEXT;
ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS investigation_question TEXT;
ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS next_step TEXT;
ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Index for fast lookup by investor_id and scheme_option_id
CREATE INDEX IF NOT EXISTS idx_investor_watchlist_option ON investor_watchlist(investor_id, scheme_option_id);
