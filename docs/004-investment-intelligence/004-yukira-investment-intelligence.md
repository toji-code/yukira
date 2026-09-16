# YUKIRA Investment Intelligence Specification

**Document ID:** 004  
**Version:** 0.1  
**Status:** Draft  
**Purpose:** Define the analytical framework, methodology, scoring architecture, decision logic, and evidence standards used by YUKIRA to evaluate investments.

---

# 1. Purpose

The Investment Intelligence Specification defines how YUKIRA transforms investment data into structured, explainable decision support.

It establishes:

- What YUKIRA evaluates
- How investments are evaluated
- Which analytical dimensions are used
- How quantitative and qualitative evidence interact
- How scores are constructed
- How uncertainty is represented
- How investment decisions are derived
- How conclusions are validated and explained

The specification is intended to provide a consistent analytical foundation that can evolve as YUKIRA's research capability expands.

---

# 2. Core Analytical Objective

YUKIRA should not attempt to predict the future with certainty.

Its objective is to improve the probability of making a better investment decision by answering:

> **Given the available evidence, what is the quality of this investment, what opportunity exists, what risks could impair the thesis, and how confident should the investor be in the conclusion?**

The system should therefore evaluate both:

```text
Investment Quality
        +
Opportunity
        +
Investor Fit
        +
Confidence
        ↓
Decision Support
```

A strong investment is not automatically a suitable investment for every investor.

---

# 3. Analytical Principles

YUKIRA's investment intelligence should follow these principles:

### 3.1 Evidence Before Opinion

Conclusions should be supported by observable data, validated calculations, documented assumptions, or explicitly identified analytical judgments.

### 3.2 Risk Before Return

Potential returns should not be evaluated independently of the risks required to achieve them.

### 3.3 Quality Before Opportunity

The underlying quality of an investment should be evaluated before determining whether its current opportunity is attractive.

### 3.4 Probability Over Prediction

YUKIRA should reason in terms of probabilities, ranges, scenarios, and uncertainty rather than deterministic forecasts.

### 3.5 Multiple Horizons

Where data permits, analysis should distinguish between:

- Short-term
- Medium-term
- Long-term

The relevant horizon should depend on the investment and investor objective.

### 3.6 Context Matters

Metrics should not be interpreted in isolation.

Results should be evaluated relative to appropriate:

- Benchmarks
- Peer groups
- Market conditions
- Historical ranges
- Investment objectives
- Risk levels

### 3.7 No Metric Worship

No individual metric should determine an investment conclusion by itself.

Metrics should contribute to broader analytical dimensions.

### 3.8 Avoid Double Counting

Highly correlated or economically overlapping metrics should not independently dominate the decision engine.

### 3.9 Explainability

Every material conclusion should be explainable in terms understandable to an investor while retaining analytical rigor.

### 3.10 Uncertainty Must Be Visible

When evidence is weak, conflicting, stale, or incomplete, YUKIRA should reduce confidence rather than manufacture certainty.

---

# 4. Unit of Analysis

The initial YUKIRA investment intelligence system will primarily evaluate **Indian mutual funds**.

The principal unit of analysis is the individual investment product, such as:

- Mutual fund scheme
- Fund plan
- Relevant share class or variant where applicable

The system should also evaluate the investment within its broader context, including:

- Category
- Benchmark
- Portfolio
- Fund manager
- AMC
- Market regime
- Investor objective

Therefore:

```text
Fund
 │
 ├── Portfolio
 ├── Manager
 ├── Strategy
 ├── Benchmark
 ├── Category
 ├── Valuation
 ├── Risk
 └── Market Context
```

The architecture should allow future expansion to other investment instruments without requiring the analytical framework to be redesigned from scratch.

---

# 5. Analytical Framework

YUKIRA's core analysis should be organized into major analytical dimensions rather than treating every metric as an independent vote.

The initial framework consists of:

1. **Return Quality**
2. **Risk & Tail Risk**
3. **Portfolio Quality**
4. **Manager Skill**
5. **Portfolio Construction**
6. **Valuation**
7. **Future Sustainability**
8. **Liquidity & Capacity**
9. **Governance & Operations**
10. **Macro / Scenario / Stress Resilience**

Each dimension may contain multiple metrics and qualitative assessments.

Conceptually:

```text
                    INVESTMENT
                         │
        ┌────────────────┼────────────────┐
        │                │                │
      Quality        Opportunity      Investor Fit
        │                │                │
        └────────────────┼────────────────┘
                         │
                    Confidence
                         │
                         ▼
                  Decision Outcome
```

The dimensions should be hierarchical.

Individual metrics contribute evidence to analytical sub-dimensions, which contribute to major dimensions, which then contribute to the broader investment assessment.

This prevents a large number of correlated metrics from artificially inflating or reducing the final assessment.

---

# 6. Analytical Hierarchy

YUKIRA should use a hierarchical analytical structure.

The preferred hierarchy is:

```text
Metric
  ↓
Sub-Dimension
  ↓
Major Dimension
  ↓
Quality / Opportunity / Fit
  ↓
Confidence Adjustment
  ↓
Decision Outcome
```

For example:

```text
Downside Capture
        ↓
Downside Behaviour
        ↓
Risk & Tail Risk
        ↓
Investment Quality
        ↓
Decision
```

Metrics should therefore have a defined analytical purpose.

A metric should not exist merely because it is statistically interesting or commonly reported by financial platforms.

---

# 7. Metric Classification

Every quantitative metric should be classified according to its analytical role.

Possible classifications include:

- Return
- Risk
- Drawdown
- Consistency
- Benchmark-relative
- Portfolio
- Factor
- Manager
- Valuation
- Liquidity
- Capacity
- Governance
- Sustainability
- Scenario
- Macro
- Investor Fit

Each metric should have:

- Unique metric identifier
- Name
- Definition
- Formula
- Input data
- Frequency
- Required history
- Analytical dimension
- Sub-dimension
- Interpretation
- Limitations
- Directionality
- Weighting or aggregation rule
- Methodology version

No metric should enter the scoring system without a documented purpose.

---

# 8. Return Quality

Return Quality evaluates whether an investment has generated attractive returns and whether those returns appear economically meaningful and repeatable.

Potential areas include:

- Absolute returns
- Annualized returns
- Rolling returns
- Benchmark-relative returns
- Category-relative returns
- Excess return
- Return consistency
- Downside-adjusted returns
- Risk-adjusted returns
- Rolling outperformance
- Persistence of outperformance

The analysis should distinguish between:

```text
High Return
```

and:

```text
High-Quality Return
```

A high historical return achieved through extreme concentration, excessive volatility, or an unsustainable factor exposure should not automatically be classified as high-quality performance.

Return analysis should therefore be interpreted together with risk, portfolio, factor, and regime information.

---

# 9. Risk & Tail Risk

Risk analysis evaluates both normal variability and adverse outcomes.

Potential areas include:

- Volatility
- Downside deviation
- Maximum drawdown
- Drawdown duration
- Recovery period
- Value-at-Risk where appropriate
- Expected Shortfall where appropriate
- Downside capture
- Tail-event behaviour
- Loss frequency
- Benchmark-relative downside
- Correlation during stress
- Risk concentration

YUKIRA should distinguish between:

**Observed Risk**

Risk measured from historical data.

**Structural Risk**

Risk arising from portfolio construction, concentration, liquidity, leverage, strategy, or other persistent characteristics.

**Scenario Risk**

Risk estimated under specified hypothetical or historical stress scenarios.

Historical risk should not be treated as a complete representation of future risk.

---

# 10. Portfolio Quality

Portfolio Quality evaluates the underlying holdings and their economic characteristics.

Potential areas include:

- Portfolio concentration
- Sector concentration
- Security concentration
- Market-cap distribution
- Valuation characteristics
- Profitability
- Growth characteristics
- Balance-sheet characteristics
- Quality characteristics
- Portfolio turnover
- Diversification
- Factor exposures
- Active share where applicable
- Portfolio overlap
- Liquidity characteristics

Portfolio analysis should attempt to answer:

> **What does the investor actually own?**

A fund's label, category, or stated mandate should not substitute for analysis of its actual portfolio.

Portfolio characteristics should be evaluated over time where sufficient historical holdings data exists.

---

# 11. Manager Skill

Manager Skill evaluates whether observed investment outcomes can reasonably be associated with the investment team's decisions and process.

Potential areas include:

- Historical active performance
- Benchmark-relative consistency
- Security selection
- Sector allocation
- Factor allocation
- Portfolio positioning
- Attribution
- Decision persistence
- Behaviour across market regimes
- Tenure
- Process consistency
- Team stability

Manager performance should not automatically be interpreted as skill.

YUKIRA should distinguish between:

```text
Observed Outperformance
        ↓
Possible Explanations
        ↓
Factor / Market Exposure
        ↓
Portfolio Decisions
        ↓
Evidence of Repeatable Skill
```

Where evidence is insufficient to establish skill, the system should explicitly state that uncertainty.

Short manager tenure, limited observations, or major process changes should reduce confidence in manager-skill conclusions.

---

# 12. Portfolio Construction

Portfolio Construction evaluates how the fund converts its investment philosophy into an actual portfolio.

Potential areas include:

- Position sizing
- Concentration
- Diversification
- Sector allocation
- Cash allocation
- Turnover
- Rebalancing behaviour
- Risk budgeting
- Factor concentration
- Correlation between holdings
- Benchmark deviation
- Active share where applicable
- Portfolio flexibility
- Consistency with stated mandate

The objective is to determine whether the portfolio construction process is coherent with the fund's stated strategy and risk profile.

A strong investment process can still produce poor outcomes if portfolio construction introduces excessive unintended risk.

---

# 13. Valuation

Valuation evaluates whether the current price paid for the underlying exposure is reasonable relative to its fundamentals and expected future outcomes.

For mutual funds, valuation should primarily be assessed through the underlying portfolio rather than treating the fund's NAV itself as a conventional valuation measure.

Potential areas include:

- Portfolio P/E
- Portfolio P/B
- Earnings yield
- Free-cash-flow characteristics
- Growth-adjusted valuation
- Historical valuation ranges
- Category-relative valuation
- Benchmark-relative valuation
- Valuation dispersion
- Valuation contribution to expected returns

Valuation analysis should distinguish between:

```text
Good Asset
```

and:

```text
Good Asset At An Attractive Price
```

An investment may have excellent underlying businesses but limited prospective opportunity when valuations are excessive.

Valuation should therefore influence the **Opportunity Score** without unnecessarily contaminating the underlying **Quality Score**.

---

# 14. Future Sustainability

Future Sustainability evaluates whether the characteristics supporting an investment's historical success are likely to remain viable.

Potential areas include:

- Strategy durability
- Competitive positioning
- Earnings sustainability
- Structural growth drivers
- Factor-cycle dependence
- Capacity constraints
- Portfolio liquidity
- Manager/process continuity
- Business-model durability
- Regulatory or structural changes
- Dependence on unusually favourable historical conditions

The purpose is not to predict the future with certainty.

Instead, YUKIRA should identify:

1. What currently supports the investment thesis
2. Which supporting conditions may persist
3. Which conditions may deteriorate
4. What evidence would indicate deterioration

Sustainability conclusions should carry explicit uncertainty.

---

# 15. Liquidity & Capacity

Liquidity & Capacity evaluates whether the investment can continue to implement its strategy effectively as assets, market conditions, or investor flows change.

Potential areas include:

- Portfolio liquidity
- Trading volume
- Position liquidity
- Asset size
- Position size relative to market liquidity
- Investor flow sensitivity
- Turnover
- Capacity constraints
- Small-cap or mid-cap exposure
- Redemption stress
- Market-impact risk

The analysis should distinguish:

**Fund Liquidity**

The ability of the fund to meet investor redemptions.

**Portfolio Liquidity**

The ability to transact in the underlying securities without material market impact.

**Strategy Capacity**

The ability of the investment process to continue generating its intended exposure or opportunity as assets under management increase.

A fund may appear highly liquid under normal conditions while experiencing materially different liquidity characteristics during market stress.

---

# 16. Governance & Operations

Governance & Operations evaluates non-investment factors that may materially affect investor outcomes.

Potential areas include:

- Regulatory compliance
- Operational reliability
- Disclosure quality
- Reporting consistency
- Fund structure
- Expense structure
- Changes in key personnel
- Process governance
- Conflicts of interest
- Operational incidents
- Material policy changes
- Transparency

Governance analysis should focus on factors that can affect the reliability, integrity, or sustainability of the investment.

Operational concerns should not be treated as equivalent to investment-performance weakness. They should be represented separately and incorporated into the overall assessment according to their materiality.

---

# 17. Macro, Scenario & Stress Resilience

YUKIRA should evaluate how an investment may behave under different market environments.

Relevant scenarios may include:

- Equity market drawdown
- Rapid interest-rate changes
- Inflation shock
- Economic slowdown
- Liquidity contraction
- Credit stress
- Sector-specific disruption
- Factor reversal
- Extreme valuation compression
- Historical crisis periods

Scenario analysis should distinguish between:

**Historical Stress**

How the investment behaved during an observed historical event.

**Hypothetical Stress**

How the investment may behave under a defined hypothetical shock.

**Modelled Scenario**

An analytically estimated outcome based on specified assumptions.

Scenario outputs must never be presented as guaranteed forecasts.

---

# 18. Factor Analysis

Factor analysis evaluates the systematic exposures that may explain investment behaviour.

Potential factors include:

- Market beta
- Size
- Value
- Momentum
- Quality
- Low volatility
- Growth
- Sector exposure
- Interest-rate sensitivity
- Credit exposure
- Other relevant systematic drivers

The objective is to distinguish:

```text
Manager / Strategy Skill
```

from:

```text
Systematic Factor Exposure
```

For example, persistent outperformance may be partly explained by exposure to a factor that happened to perform strongly during the observation period.

Factor analysis should therefore be incorporated into performance attribution and manager-skill assessment.

Factor models themselves must be versioned and validated.

---

# 19. Investor Fit

Investor Fit evaluates whether an investment is appropriate for a particular investor objective and constraint set.

Relevant inputs may include:

- Investment horizon
- Risk tolerance
- Risk capacity
- Liquidity requirements
- Return objective
- Income requirements
- Existing portfolio
- Asset allocation
- Concentration
- Investment purpose
- Tax considerations where supported

The same investment can therefore produce different fit assessments for different investors.

Conceptually:

```text
Investment Quality
        +
Opportunity
        +
Investor Constraints
        ↓
Investor Fit
```

Investor Fit should not be used to artificially improve the underlying quality assessment of an investment.

---

# 20. Evidence Framework

Every material analytical conclusion should be associated with an evidence classification.

YUKIRA should distinguish at minimum:

| Evidence Type | Meaning |
|---|---|
| Fact | Directly supported by source data |
| Calculation | Deterministically derived from validated data |
| Assumption | Explicit input required for analysis |
| Estimate | Approximation based on available evidence |
| Hypothesis | Proposed explanation requiring validation |
| Judgment | Analytical interpretation |
| Scenario | Outcome under specified assumptions |

The system should clearly distinguish observed facts from analytical interpretation.

For example:

```text
FACT
Fund returned X% over the period.

CALCULATION
Its annualized excess return versus the benchmark was Y%.

INTERPRETATION
The result suggests persistent relative strength.

HYPOTHESIS
Some of the strength may be explained by factor exposure.

JUDGMENT
Evidence for repeatable manager skill is currently moderate.

UNCERTAINTY
The conclusion is limited by the available manager tenure.
```

This evidence framework is fundamental to YUKIRA's explainability and auditability.

---

# 21. Metric Selection & MVP Scope

The MVP should contain approximately **25–35 high-value metrics**.

Metric selection should prioritize:

1. Analytical usefulness
2. Data availability
3. Data reliability
4. Interpretability
5. Testability
6. Low redundancy
7. Relevance to investor decisions

The MVP should not attempt to implement the eventual 120–150+ metric framework immediately.

Each MVP metric should have a clearly documented reason for inclusion.

A metric should be excluded when:

- Reliable data is unavailable
- Its interpretation is ambiguous
- It substantially duplicates another metric
- It provides little incremental decision value
- It cannot be adequately validated

The metric framework should expand only when additional metrics provide demonstrable analytical value.

---

# 22. Scoring Architecture

YUKIRA should use hierarchical scoring rather than a flat weighted average of every metric.

Conceptually:

```text
Raw Data
   ↓
Metrics
   ↓
Sub-Dimensions
   ↓
Major Dimensions
   ↓
Quality Score
Opportunity Score
Investor Fit Score
   ↓
Confidence Score
   ↓
Decision Engine
```

Scores should be normalized where necessary to allow meaningful aggregation.

The scoring methodology must specify:

- Metric direction
- Normalization method
- Reference population
- Time horizon
- Weight
- Aggregation method
- Missing-data treatment
- Confidence treatment
- Version

Weights should reflect economic importance and incremental information value rather than the number of available metrics.

---

# 23. Quality Score

The **Quality Score** evaluates the underlying strength and characteristics of the investment.

It should primarily incorporate:

- Return Quality
- Risk & Tail Risk
- Portfolio Quality
- Manager Skill
- Portfolio Construction
- Future Sustainability
- Liquidity & Capacity
- Governance & Operations
- Other validated structural characteristics

Quality should generally be less sensitive to short-term market valuation.

The objective is to answer:

> **How strong is this investment as an underlying investment opportunity, independent of whether today's price is attractive?**

A high Quality Score does not automatically imply a BUY decision.

---

# 24. Opportunity Score

The **Opportunity Score** evaluates whether the current opportunity is attractive relative to the investment's quality, valuation, risks, and expected future outcomes.

It may incorporate:

- Current valuation
- Historical valuation range
- Expected return
- Risk-adjusted expected return
- Scenario outcomes
- Market regime
- Forward-looking opportunity
- Margin of safety

The Opportunity Score should be explicitly distinguished from historical performance.

An investment can have:

```text
High Quality + Low Opportunity
```

or:

```text
Moderate Quality + High Opportunity
```

The decision engine should consider both rather than assuming quality and opportunity are identical.

---

# 25. Confidence Score

The **Confidence Score** represents confidence in the analytical conclusion, not confidence that the investment will generate a particular return.

Confidence should consider:

- Data completeness
- Data freshness
- Data quality
- Historical depth
- Methodology stability
- Model validation
- Signal consistency
- Agreement between analytical dimensions
- Scenario robustness
- Presence of conflicting evidence
- Model uncertainty

Conceptually:

```text
Strong Evidence
      +
Reliable Data
      +
Sufficient History
      +
Validated Methodology
      +
Consistent Signals
      ↓
Higher Confidence
```

Conversely, missing data, contradictory signals, short histories, structural breaks, or unvalidated models should reduce confidence.

A high score with low confidence should not be treated as a high-conviction investment conclusion.

---

# 26. Decision Engine

The Decision Engine converts the analytical assessment into an investor-facing decision outcome.

Possible outcomes are:

- BUY
- HOLD
- WATCH
- INVESTIGATE
- REDUCE
- AVOID

The decision should not be generated from a single total score.

It should consider:

```text
Quality
    +
Opportunity
    +
Investor Fit
    +
Confidence
    +
Material Risks
    +
Scenario Resilience
    ↓
Decision
```

The decision engine should also apply explicit constraints and veto conditions where appropriate.

Examples may include:

- Critical data-quality failure
- Severe governance concern
- Insufficient analytical history
- Extreme concentration
- Material liquidity concern
- Unvalidated model dependency
- Significant conflict between evidence and conclusion

A high score must not override a critical risk automatically.

The Decision Engine must be deterministic and version-controlled.

---

# 27. Decision Rules

Decision rules should be explicit, testable, and versioned.

Rules should define:

- Required analytical inputs
- Thresholds where applicable
- Conditions
- Overrides
- Confidence requirements
- Missing-data behaviour
- Risk constraints
- Investor-fit constraints

The system should avoid arbitrary thresholds unless supported by:

- Financial theory
- Historical evidence
- Statistical analysis
- Backtesting
- Domain research
- Explicit expert judgment

Where a threshold is based on judgment rather than empirical evidence, this should be documented.

---

# 28. Uncertainty & Missing Data

Missing or unreliable data must be treated as an analytical condition rather than silently substituted.

Possible states include:

- Data available
- Data incomplete
- Data stale
- Data unreliable
- Data unavailable
- Insufficient history
- Calculation unavailable
- Model unavailable

YUKIRA should distinguish:

```text
"We do not know."
```

from:

```text
"The evidence indicates a negative result."
```

Missing information should reduce confidence where appropriate.

The system must not manufacture values merely to produce a complete score.

Where a score cannot be responsibly calculated, YUKIRA should be capable of returning:

**INSUFFICIENT EVIDENCE**

rather than forcing a decision.

---

# 29. Correlation, Redundancy & Double Counting

Metric aggregation must account for correlation and conceptual overlap.

Examples of potentially overlapping information include:

- Multiple return periods
- Multiple volatility measures
- Several drawdown measures
- Related valuation ratios
- Similar factor measures
- Multiple benchmark-relative measures

The presence of many correlated metrics should not create artificial confidence.

Possible techniques include:

- Metric grouping
- Correlation analysis
- Hierarchical weighting
- Dimensionality reduction where justified
- Information-value analysis
- Expert-defined caps
- Regularization in predictive models

Any method used to control redundancy must itself be validated.

---

# 30. Temporal Analysis

YUKIRA should analyze investments across time rather than relying solely on point-in-time observations.

Where data permits, analysis should include:

- Rolling periods
- Multiple market cycles
- Bull markets
- Bear markets
- Recovery periods
- Stress periods
- Regime changes
- Pre/post strategy changes

Historical analysis must preserve the information that would actually have been available at the time being evaluated.

This is essential for avoiding **look-ahead bias**.

For backtesting and historical analysis:

```text
Information Available At Time T
             ↓
        Analysis At T
             ↓
        Decision At T
             ↓
      Future Outcome
```

Future information must not leak into the historical decision.

---

# 31. Probabilistic Forecasting

Where forward-looking analysis is used, YUKIRA should prefer probabilistic estimates over point predictions.

Forecasts may be expressed through:

- Expected ranges
- Probability distributions
- Scenario probabilities
- Confidence intervals
- Expected downside
- Expected upside
- Probability of achieving an investor objective

Forecasts must clearly distinguish:

```text
Historical Observation
        ↓
Model Estimate
        ↓
Scenario Assumption
        ↓
Probabilistic Outcome
```

A forecast is not a promise.

Forecasting models must be validated using appropriate out-of-sample testing and backtesting before being used to influence investment decisions.

---

# 32. Scenario Framework

Scenario analysis should provide a structured representation of possible future environments.

At minimum, the framework should support:

- Base case
- Bull case
- Bear case
- Severe stress case

Each scenario should define:

- Economic assumptions
- Market assumptions
- Portfolio implications
- Expected risks
- Potential outcomes
- Probability where justified
- Confidence level

Probabilities should not be assigned merely to make scenarios appear precise.

Where reliable probability estimation is unavailable, scenarios may remain qualitative or use explicitly labelled assumptions.

---

# 33. Investment Thesis

YUKIRA should produce a structured investment thesis rather than only numerical scores.

The thesis should answer:

### Why might this investment work?

Identify the strongest supporting evidence.

### What could make it fail?

Identify the principal risks and invalidating conditions.

### What is already priced in?

Assess valuation and market expectations where appropriate.

### What should be monitored?

Identify leading indicators or conditions that could change the assessment.

### What would change YUKIRA's mind?

Define measurable or observable conditions that could materially alter the conclusion.

The thesis should be evidence-based and should not introduce facts unsupported by the underlying data.

---

# 34. Contradictory Evidence

YUKIRA must explicitly identify material conflicts between analytical signals.

Examples:

```text
Strong Quality
        +
Expensive Valuation
        ↓
Potentially Attractive Investment
But Limited Current Opportunity
```

or:

```text
Strong Historical Returns
        +
Weakening Portfolio Quality
        ↓
Historical Strength May Not Persist
```

Contradictory evidence should not simply be averaged away.

The system should explain:

- Which signals conflict
- Why they conflict
- Which evidence is more reliable
- What remains uncertain
- What additional evidence would resolve the conflict

A disagreement between analytical dimensions may itself be an important investment signal.

---

# 35. Investor Conclusion

Every completed YUKIRA analysis should provide a concise investor-facing conclusion before presenting detailed analysis.

The conclusion should contain:

1. **Decision**
2. **Quality assessment**
3. **Opportunity assessment**
4. **Investor-fit assessment**
5. **Confidence**
6. **Primary reasons**
7. **Key risks**
8. **What could make the conclusion wrong**
9. **What the investor should investigate**

Conceptually:

```text
                    YUKIRA CONCLUSION

Decision:            WATCH

Quality:             Strong
Opportunity:         Moderate
Investor Fit:        Strong
Confidence:           Medium

Why:
- ...
- ...
- ...

Key Risks:
- ...
- ...

What Could Change The View:
- ...
- ...

Investor Investigation:
- ...
- ...
```

The conclusion should be understandable without reading the full analytical report.

Detailed evidence and methodology should remain available through progressive disclosure.

---

# 36. Model Validation

Any analytical model that materially influences an investment conclusion must be validated before production use.

Validation should consider:

- In-sample versus out-of-sample performance
- Historical backtesting
- Walk-forward testing where appropriate
- Stability across market regimes
- Sensitivity to assumptions
- Parameter stability
- Prediction error
- Calibration
- False positives
- False negatives
- Economic plausibility

Predictive performance should not be evaluated solely on statistical fit.

A model that performs well historically but lacks economic robustness should not be treated as reliable.

Validation results should be documented and versioned.

---

# 37. Bias & Failure Controls

YUKIRA must actively guard against analytical and data biases.

Important risks include:

- Survivorship bias
- Look-ahead bias
- Selection bias
- Data-mining bias
- Overfitting
- Benchmark changes
- Stale data
- Missing data
- Incorrect corporate actions
- Inconsistent historical classifications
- Regime dependence
- Metric double-counting
- Model instability

Historical analysis should use point-in-time information whenever the objective is to reproduce historical investment decisions.

Known limitations should be disclosed rather than hidden.

---

# 38. Methodology Versioning

Every material analytical output should be associated with a methodology version.

A methodology version should identify, where applicable:

- Metric definitions
- Formulas
- Data requirements
- Normalization methods
- Weights
- Scoring rules
- Decision rules
- Model versions
- Scenario assumptions
- Relevant configuration

When methodology changes materially, results should be identifiable as belonging to different methodology versions.

Historical results should not be silently rewritten without preserving the ability to understand the methodology that originally produced them.

---

# 39. Analytical Standard

YUKIRA's investment intelligence should ultimately satisfy five requirements:

```text
CORRECT
   ↓
TRACEABLE
   ↓
EXPLAINABLE
   ↓
VALIDATED
   ↓
USEFUL
```

The system should prefer:

- Correctness over quantity
- Evidence over opinion
- Probability over prediction
- Risk awareness over return chasing
- Explainability over unnecessary complexity
- Transparency over marketing
- Robustness over sophistication
- Investor decision quality over engagement

The ultimate objective is not to produce the highest score, the most impressive report, or the most confident prediction.

It is to improve the probability that an investor makes a better-informed decision.

YUKIRA should therefore be willing to conclude:

**BUY, HOLD, WATCH, INVESTIGATE, REDUCE, AVOID, or INSUFFICIENT EVIDENCE.**
