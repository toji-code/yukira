ALTER TABLE investor_portfolio_holding 
ADD COLUMN IF NOT EXISTS cost_basis_amount NUMERIC(19, 4);
