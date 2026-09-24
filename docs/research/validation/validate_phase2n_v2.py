"""
YUKIRA Phase 2N V2 Independent Methodology Validation Script
Validates candidates M2N-01, M2N-02, M2N-05, M2N-06, M2N-07 against deterministic test vectors.
"""

import math
import numpy as np
from decimal import Decimal, getcontext
getcontext().prec = 50

def validate_m2n01_annualization():
    print("=== VALIDATING M2N-01 (ANNUALIZATION) ===")
    results = {}
    
    # Test Vector 1: Exactly 365 calendar days
    # Start NAV = 100.0, End NAV = 110.0 (10% simple return)
    # Elapsed = 365 days
    start_nav = 100.0
    end_nav = 110.0
    d_365 = 365
    cagr_365 = (end_nav / start_nav) ** (365.25 / d_365) - 1.0
    expected_365 = 1.10 ** (365.25 / 365.0) - 1.0
    results["cagr_365"] = {
        "days": d_365,
        "cagr": cagr_365,
        "expected": expected_365,
        "diff": abs(cagr_365 - expected_365),
        "pass": math.isclose(cagr_365, expected_365, abs_tol=1e-15)
    }
    
    # Test Vector 2: Leap year 366 calendar days
    d_366 = 366
    cagr_366 = (end_nav / start_nav) ** (365.25 / d_366) - 1.0
    expected_366 = 1.10 ** (365.25 / 366.0) - 1.0
    results["cagr_366"] = {
        "days": d_366,
        "cagr": cagr_366,
        "expected": expected_366,
        "diff": abs(cagr_366 - expected_366),
        "pass": math.isclose(cagr_366, expected_366, abs_tol=1e-15)
    }
    
    # Test Vector 3: 3-Year Interval (1096 calendar days, 1 leap year)
    # Start NAV = 100.0, End NAV = 150.0 (50% simple return over 3Y)
    end_nav_3y = 150.0
    d_3y = 1096
    cagr_3y = (end_nav_3y / start_nav) ** (365.25 / d_3y) - 1.0
    expected_3y = 1.50 ** (365.25 / 1096.0) - 1.0
    results["cagr_3y"] = {
        "days": d_3y,
        "cagr": cagr_3y,
        "expected": expected_3y,
        "diff": abs(cagr_3y - expected_3y),
        "pass": math.isclose(cagr_3y, expected_3y, abs_tol=1e-15)
    }
    
    # Test Vector 4: Constant NAV series
    cagr_const = (100.0 / 100.0) ** (365.25 / 1095) - 1.0
    results["cagr_const"] = {
        "cagr": cagr_const,
        "expected": 0.0,
        "pass": cagr_const == 0.0
    }
    
    # Test Vector 5: Daily Volatility (sqrt(252))
    # Sample returns: [0.01, -0.005, 0.008, -0.002, 0.004, -0.003]
    returns = [0.01, -0.005, 0.008, -0.002, 0.004, -0.003]
    n = len(returns)
    mean_r = sum(returns) / n
    var_sample = sum((r - mean_r)**2 for r in returns) / (n - 1)
    std_sample = math.sqrt(var_sample)
    vol_annual = std_sample * math.sqrt(252)
    # Independent calculation using numpy
    np_std = np.std(returns, ddof=1)
    np_vol = np_std * np.sqrt(252)
    results["volatility"] = {
        "pure_python": vol_annual,
        "numpy": float(np_vol),
        "diff": abs(vol_annual - float(np_vol)),
        "pass": math.isclose(vol_annual, float(np_vol), abs_tol=1e-15)
    }
    
    # Test Vector 6: Tracking Error (sqrt(252) on active returns)
    p_returns = [0.012, -0.004, 0.009, -0.001, 0.005, -0.002]
    b_returns = [0.010, -0.005, 0.008, -0.002, 0.004, -0.003]
    active_returns = [p - b for p, b in zip(p_returns, b_returns)]
    te_sample = math.sqrt(sum((a - sum(active_returns)/len(active_returns))**2 for a in active_returns) / (len(active_returns) - 1))
    te_annual = te_sample * math.sqrt(252)
    np_te = float(np.std(active_returns, ddof=1) * np.sqrt(252))
    results["tracking_error"] = {
        "pure_python": te_annual,
        "numpy": np_te,
        "diff": abs(te_annual - np_te),
        "pass": math.isclose(te_annual, np_te, abs_tol=1e-15)
    }
    
    for k, v in results.items():
        print(f"  {k}: {v}")
    return results

def validate_m2n02_risk_free():
    print("\n=== VALIDATING M2N-02 (RISK-FREE RATE CONVERSIONS & ALIGNMENT) ===")
    # Evaluating competing yield-to-return formulas
    quoted_yield = 0.0692  # 6.92% annual yield
    
    # Formula A: Linear 365
    rf_linear_365 = quoted_yield / 365.0
    # Formula B: Linear 365.25
    rf_linear_36525 = quoted_yield / 365.25
    # Formula C: Linear 252 (trading day)
    rf_linear_252 = quoted_yield / 252.0
    # Formula D: Geometric compound 365.25
    rf_geom_36525 = (1.0 + quoted_yield) ** (1.0 / 365.25) - 1.0
    # Formula E: Geometric compound 252
    rf_geom_252 = (1.0 + quoted_yield) ** (1.0 / 252.0) - 1.0
    
    print(f"Quoted Yield: {quoted_yield:.6f}")
    print(f"  Formula A (Linear 365):       {rf_linear_365:.10f} ({rf_linear_365*100:.6f}%)")
    print(f"  Formula B (Linear 365.25):    {rf_linear_36525:.10f} ({rf_linear_36525*100:.6f}%)")
    print(f"  Formula C (Linear 252):       {rf_linear_252:.10f} ({rf_linear_252*100:.6f}%)")
    print(f"  Formula D (Geometric 365.25): {rf_geom_36525:.10f} ({rf_geom_36525*100:.6f}%)")
    print(f"  Formula E (Geometric 252):    {rf_geom_252:.10f} ({rf_geom_252*100:.6f}%)")
    
    diff_linear_geom = abs(rf_linear_36525 - rf_geom_36525)
    diff_365_252 = abs(rf_linear_36525 - rf_linear_252)
    print(f"  Linear vs Geometric divergence: {diff_linear_geom:.10f}")
    print(f"  365.25 vs 252 divisor divergence: {diff_365_252:.10f} ({diff_365_252*10000:.2f} bps/day)")
    print("  -> V2 does NOT specify which formula to use! This is an unresolved methodology gap.")

def validate_m2n05_treynor_numerator():
    print("\n=== VALIDATING M2N-05 (TREYNOR NUMERATOR) ===")
    # Test cases for Treynor numerator Rp - Rf
    # Case 1: Positive excess return
    rp_pos = 0.152  # 15.2%
    rf_pos = 0.065  # 6.5%
    beta_1 = 1.05
    treynor_pos = (rp_pos - rf_pos) / beta_1
    
    # Case 2: Zero excess return
    rp_zero = 0.065
    treynor_zero = (rp_zero - rf_pos) / beta_1
    
    # Case 3: Negative excess return
    rp_neg = 0.035
    treynor_neg = (rp_neg - rf_pos) / beta_1
    
    # Case 4: Negative Beta with negative excess return
    beta_neg = -0.5
    treynor_neg_beta = (rp_neg - rf_pos) / beta_neg
    
    print(f"  Positive excess Treynor: {treynor_pos:.6f}")
    print(f"  Zero excess Treynor:     {treynor_zero:.6f}")
    print(f"  Negative excess Treynor: {treynor_neg:.6f}")
    print(f"  Negative excess with negative beta Treynor: {treynor_neg_beta:.6f} (Epistemic paradox: appears positive!)")

def validate_m2n06_beta_ols():
    print("\n=== VALIDATING M2N-06 (EXCESS-RETURN OLS BETA) ===")
    # Test Vector: 10 paired observations
    # Portfolio returns, Benchmark returns, Risk-free daily returns
    rp = [0.012, -0.008, 0.015, -0.003, 0.007, -0.011, 0.009, 0.004, -0.006, 0.014]
    rb = [0.010, -0.007, 0.012, -0.002, 0.006, -0.009, 0.008, 0.003, -0.005, 0.011]
    rf = [0.0002] * 10
    
    # Calculate excess returns
    y = [p - f for p, f in zip(rp, rf)]
    x = [b - f for b, f in zip(rb, rf)]
    
    n = len(y)
    mean_y = sum(y) / n
    mean_x = sum(x) / n
    
    cov_xy = sum((xi - mean_x) * (yi - mean_y) for xi, yi in zip(x, y)) / (n - 1)
    var_x = sum((xi - mean_x) ** 2 for xi in x) / (n - 1)
    
    beta_manual = cov_xy / var_x
    alpha_manual = mean_y - beta_manual * mean_x
    
    # Independent Calculation 2: numpy polyfit / lstsq
    poly_beta, poly_alpha = np.polyfit(x, y, 1)
    
    # Independent Calculation 3: exact decimal
    x_dec = [Decimal(str(xi)) for xi in x]
    y_dec = [Decimal(str(yi)) for yi in y]
    mean_x_dec = sum(x_dec) / Decimal(n)
    mean_y_dec = sum(y_dec) / Decimal(n)
    cov_dec = sum((xi - mean_x_dec) * (yi - mean_y_dec) for xi, yi in zip(x_dec, y_dec)) / Decimal(n - 1)
    var_dec = sum((xi - mean_x_dec)**2 for xi in x_dec) / Decimal(n - 1)
    beta_dec = float(cov_dec / var_dec)
    alpha_dec = float(mean_y_dec - (cov_dec / var_dec) * mean_x_dec)
    
    print(f"  Manual Beta:    {beta_manual:.15f}, Alpha: {alpha_manual:.15f}")
    print(f"  NumPy Polyfit:  {poly_beta:.15f}, Alpha: {poly_alpha:.15f}")
    print(f"  Decimal Exact:  {beta_dec:.15f}, Alpha: {alpha_dec:.15f}")
    print(f"  Beta Discrepancy (Manual vs NumPy): {abs(beta_manual - poly_beta):.2e}")
    print(f"  Beta Discrepancy (Manual vs Decimal): {abs(beta_manual - beta_dec):.2e}")
    
    # Test Edge Case: Constant benchmark return (zero variance)
    rb_const = [0.005] * 10
    x_const = [b - f for b, f in zip(rb_const, rf)]
    var_x_const = sum((xi - sum(x_const)/len(x_const))**2 for xi in x_const) / (len(x_const) - 1)
    print(f"  Constant Benchmark Variance: {var_x_const} (Division by zero / Singular matrix)")

def validate_m2n07_downside_beta():
    print("\n=== VALIDATING M2N-07 (DOWNSIDE BETA CONDITIONED ON Rb < 0) ===")
    rp = [0.012, -0.008, 0.015, -0.003, 0.007, -0.011, 0.009, 0.004, -0.006, 0.000, 0.014]
    rb = [0.010, -0.007, 0.012, -0.002, 0.006, -0.009, 0.008, 0.003, -0.005, 0.000, 0.011]
    
    # Subsetting where rb < 0
    downside_pairs = [(p, b) for p, b in zip(rp, rb) if b < 0]
    print(f"  Total observations: {len(rb)}, Downside observations (Rb < 0): {len(downside_pairs)}")
    print(f"  Note: Rb = 0.0 is strictly EXCLUDED: {[b for p, b in zip(rp, rb) if b == 0]}")
    
    down_p = [p for p, b in downside_pairs]
    down_b = [b for p, b in downside_pairs]
    
    n_down = len(down_b)
    mean_p = sum(down_p) / n_down
    mean_b = sum(down_b) / n_down
    
    cov_down = sum((p - mean_p) * (b - mean_b) for p, b in zip(down_p, down_b)) / (n_down - 1)
    var_down = sum((b - mean_b)**2 for b in down_b) / (n_down - 1)
    downside_beta_calc = cov_down / var_down
    
    # Independent calculation with NumPy
    np_beta_down = float(np.cov(down_p, down_b, ddof=1)[0, 1] / np.var(down_b, ddof=1))
    
    print(f"  Calculated Downside Beta: {downside_beta_calc:.15f}")
    print(f"  NumPy Downside Beta:      {np_beta_down:.15f}")
    print(f"  Discrepancy:             {abs(downside_beta_calc - np_beta_down):.2e}")
    
    # Edge case: Constant downside return
    down_b_const = [-0.005, -0.005, -0.005]
    down_p_const = [-0.006, -0.004, -0.005]
    var_const = sum((b - sum(down_b_const)/3)**2 for b in down_b_const) / 2
    print(f"  Constant Downside Benchmark Variance: {var_const} (Zero variance division error)")

if __name__ == "__main__":
    validate_m2n01_annualization()
    validate_m2n02_risk_free()
    validate_m2n05_treynor_numerator()
    validate_m2n06_beta_ols()
    validate_m2n07_downside_beta()
