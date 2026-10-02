# YUKIRA — Institutional Quantitative Research Design System

> Design system of record for the YUKIRA investment intelligence platform.
> Dark-first. Analytical. Evidence-forward. Restrained.

---

## 0. Product Thesis (drives every visual decision)

YUKIRA is a **verification checkpoint**, not a storefront. It exists to answer
*"before you commit capital, ask one more question."*

The interface must therefore behave like a **research terminal published for
professional allocators**, not like a consumer brokerage app. Three rules follow,
and they override decorative instinct:

1. **Evidence over ornament.** Every pixel of emphasis must attach to a claim,
   a value, a limitation, or a piece of provenance. Nothing is emphasized for
   balance.
2. **Uncertainty is content.** Absence, candidation, and revision state are
   first-class visual citizens — not empty space to be filled.
3. **Determinism is legible.** Numbers are monospaced and tabular so digits align
   vertically across rows. Formulas, hashes, dates, and codes are always mono.

### Anti-goals (explicit rejections)

| Rejected | Reason |
| :--- | :--- |
| Rainbow category palettes (sky/amber/rose/purple/teal/indigo per section) | Categorical color used decoratively destroys the semantic color system. Color must mean *state*, never *decoration*. |
| Gradient headline text (`bg-clip-text`) | Marketing idiom. Reads as consumer SaaS. |
| Glassmorphism (`backdrop-blur` on every surface) | Blur is not information. Reserve for the one genuinely overlaid layer (mobile nav). |
| Neon glow / pulse dots as ambient decoration | Pulsing chrome signals "live system" while carrying no investor meaning. |
| Monospace as the *body* typeface | Mono body copy is illegible at length and is the single clearest "developer tool" tell. |
| Star ratings, scores, grades, "Good/Fair/Poor" chips | Forbidden by methodology governance. There is no composite score. |
| Promotional hero sections, testimonial layouts, oversized marketing CTAs | YUKIRA is a tool, not a landing page. |
| Charts with more than two series without direct labelling | Unreadable. Series identity must never depend on a legend. |

---

## 1. Color System

### 1.1 Philosophy

A **near-neutral, faintly cool substrate** carries almost all visual weight. Exactly
one **signal accent** (a desaturated institutional cyan-teal) marks interactive
and selected state. All remaining hue budget is reserved for **semantic state**:
approved, candidate, risk, and unavailability.

This is the core of the YUKIRA character. The UI is calm and nearly monochrome;
color is spent only where it carries governance or risk meaning.

### 1.2 Dark mode (default / primary)

| Token | Value | Role |
| :--- | :--- | :--- |
| `--background` | `#0A0C0E` | Page substrate. Cool near-black, not pure black. |
| `--surface` | `#101316` | Panels, tables, cards — the default container. |
| `--surface-raised` | `#161A1E` | Nested panels, table headers, hover fills. |
| `--surface-inset` | `#0C0F11` | Inset wells: code, hashes, empty states, chart plots. |
| `--border` | `#1E2429` | Standard 1px separator. |
| `--border-strong` | `#2C343B` | Emphasized separator, table column rules, active tab underline. |
| `--border-focus` | `#3A444D` | Focus ring at low emphasis. |
| `--text-primary` | `#E8ECEF` | Headings, primary values. |
| `--text-secondary` | `#9BA6B0` | Body copy, descriptions. |
| `--text-tertiary` | `#6B7681` | Labels, captions, metadata. |
| `--text-disabled` | `#454E57` | Unavailable / suppressed. |
| `--accent` | `#4CC4D6` | Interactive, selected, focus. Desaturated cyan — not sky blue. |
| `--accent-hover` | `#6AD4E4` | |
| `--accent-muted` | `#1A2E33` | Accent fill at rest (selected row, active chip). |
| `--accent-foreground` | `#04191D` | Text on solid accent. |

### 1.3 Light mode

| Token | Value | Role |
| :--- | :--- | :--- |
| `--background` | `#FBFCFD` | |
| `--surface` | `#FFFFFF` | |
| `--surface-raised` | `#F4F6F8` | |
| `--surface-inset` | `#EFF2F5` | |
| `--border` | `#E1E6EB` | |
| `--border-strong` | `#C9D2DA` | |
| `--border-focus` | `#AEBAC4` | |
| `--text-primary` | `#0D1114` | |
| `--text-secondary` | `#4A565F` | |
| `--text-tertiary` | `#6E7A84` | |
| `--text-disabled` | `#A3ADB6` | |
| `--accent` | `#0E7C8C` | |
| `--accent-hover` | `#0A6673` | |
| `--accent-muted` | `#DFF2F5` | |
| `--accent-foreground` | `#FFFFFF` | |

### 1.4 Semantic state colors

These encode **governance and epistemic state**. They are the only permitted hue
usage beyond the accent. Each ships as `-fg` (text), `-bg` (fill), and `-border`.

| State | Meaning | Dark fg / bg / border |
| :--- | :--- | :--- |
| `approved` | Methodology approved for production decision support | `#5BC98C` / `#0F2A1D` / `#1E4433` |
| `candidate` | Candidate specification, not validated for production | `#D9A441` / `#2A2113` / `#4A3A1A` |
| `operational` | Implemented and executing, awaiting approval | `#5AA9D9` / `#12232E` / `#1F3D4F` |
| `unavailable` | No validated data; explicitly not available | `#8A949D` / `#15191C` / `#242A2F` |
| `risk` | Drawdown, tail loss, capital impairment | `#E0685F` / `#2C1513` / `#4E2622` |
| `critical` | Execution failure, invalid state | `#F07167` / `#2E1211` / `#56201C` |
| `info` | Neutral analytical note | `#9BA6B0` / `#15191C` / `#252B30` |

> **Rule:** never encode a *metric category* (return / risk / tail) with a unique
> hue. Categories are conveyed by **section ordinal + label**, not color. A single
> `risk` hue is reserved for genuine capital-loss semantics.

### 1.5 Chart palette

Maximum two series per chart. Ordered by assignment, not by category.

1. `--series-1` — **accent** `#4CC4D6` (fund / subject)
2. `--series-2` — **neutral comparative** `#7C8894` (benchmark / peer)
3. `--series-neg` — `--risk` `#E0685F` (drawdown fill, loss regions only)
4. `--series-muted` — `#2A3239` (gridlines, reference bands)

Rule: the subject is always `--series-1`; comparators are always neutral. Never
two saturated hues. Drawdown uses `--series-neg` fill at low alpha.

---

## 2. Typography

### 2.1 Families

| Role | Stack | Use |
| :--- | :--- | :--- |
| Sans (UI + body) | `Geist`, `-apple-system`, `Segoe UI`, sans-serif | **All** headings, body, labels, navigation, buttons, table cells. |
| Mono (data) | `Geist Mono`, `ui-monospace`, monospace | Values, dates, codes, hashes, formulas, badges, status tokens, table numerics. |

> **The governing rule:** mono is *data*, sans is *language*. If a string is
> prose a human reads to understand something, it is sans. If it is a measured
> quantity, identifier, timestamp, or digest, it is mono. This one rule removes
> the current "everything is mono" character that makes YUKIRA read as a CLI tool.

### 2.2 Type scale

Fluid via `clamp()` for page-level display sizes; fixed steps below that.

| Token | Size | Line height | Tracking | Weight | Family | Use |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `display` | `clamp(2rem, 4vw, 2.75rem)` | 1.1 | `-0.03em` | 600 | sans | Route title (fund name, run name) |
| `h1` | `1.5rem` | 1.2 | `-0.02em` | 600 | sans | Page title |
| `h2` | `1.125rem` | 1.3 | `-0.01em` | 600 | sans | Section heading |
| `h3` | `0.9375rem` | 1.4 | `0` | 600 | sans | Subsection / panel title |
| `body` | `0.875rem` | 1.6 | `0` | 400 | sans | Prose, descriptions, interpretations |
| `body-sm` | `0.8125rem` | 1.55 | `0` | 400 | sans | Secondary prose, table cells |
| `label` | `0.75rem` | 1.4 | `0` | 500 | sans | Field labels, nav, buttons |
| `eyebrow` | `0.6875rem` | 1.3 | `0.09em` | 600 | mono, uppercase | Section eyebrows, column heads, status |
| `data-lg` | `1.5rem` | 1.15 | `-0.01em` | 600 | mono, tabular | Headline metric value |
| `data` | `0.9375rem` | 1.3 | `0` | 500 | mono, tabular | Metric value in grid / table |
| `data-sm` | `0.8125rem` | 1.3 | `0` | 500 | mono, tabular | Inline data, dense table numerics |
| `mono-xs` | `0.6875rem` | 1.35 | `0` | 400 | mono | Hashes, formulas, footnotes, disclosure meta |

**Minimum size is 11px (`0.6875rem`).** Nothing renders at 9px or 10px. Dense
context is achieved through *layout density* and *tabular alignment*, never by
shrinking type below this floor.

`font-variant-numeric: tabular-nums` is mandatory on `data*` tokens so digits
align in columns.

### 2.3 Reading width

Prose blocks cap at `68ch`. Analytical tables may run to the container edge.
Page gutter scales: `16px` mobile → `24px` tablet → `32px` desktop.

---

## 3. Spacing & Layout

### 3.1 Spacing scale

4px base. Only these steps are legal:

`2 · 4 · 6 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64 · 80`

Named: `space-3xs` 2 · `space-2xs` 4 · `space-xs` 6 · `space-sm` 8 ·
`space-md` 12 · `space-lg` 16 · `space-xl` 20 · `space-2xl` 24 ·
`space-3xl` 32 · `space-4xl` 40 · `space-5xl` 48 · `space-6xl` 64

### 3.2 Containers

| Token | Width | Use |
| :--- | :--- | :--- |
| `container-prose` | `720px` | Methodology / governance narrative |
| `container-app` | `1440px` | Default route content (was `max-w-7xl`) |
| `container-wide` | `1600px` | Analytical profile, comparison matrices |
| `container-full` | `100%` | Tables, lineage records |

Dense analytical routes (`/analysis/[id]`, `/compare`, `/funds`) use
`container-wide`; the wider measure earns its keep by reducing table wrapping.

### 3.3 Grid

- Desktop ≥1280px: 12 columns, `24px` gutter, max content `container-wide`.
- Tablet 768–1279px: 8 columns, `20px` gutter.
- Mobile <768px: 4 columns, `16px` gutter.

Panels use CSS Grid with `auto-fit, minmax()` for fluid metric tile rows so tile
count responds to width rather than to a fixed breakpoint ladder.

### 3.4 Breakpoints

| Name | Width | Behavior |
| :--- | :--- | :--- |
| `xs` | <480 | Single column. Metrics stack. Nav collapses to sheet. |
| `sm` | ≥640 | Two-column metric rows. |
| `md` | ≥768 | Three-column metric rows. |
| `lg` | ≥1024 | Full nav inline. Multi-panel analytical layouts. |
| `xl` | ≥1280 | Comparison matrices without horizontal scroll. |

---

## 4. Surfaces, Borders, Radii, Elevation

### 4.1 Surface ladder

Exactly four levels, each a step of deliberate contrast:

```
background  →  surface  →  surface-raised  →  surface-inset
 (page)         (panel)     (nested/hover)     (code/wells)
```

Containers must not skip levels. A panel directly on the page is `--surface`.
Anything inside it is `--surface-raised` or `--surface-inset`. Depth is
communicated by **border contrast and surface step**, never by drop shadow.

### 4.2 Borders

- Default `1px solid var(--border)`.
- `--border-strong` for: table column rules, active tab underline, focused panels.
- **No double borders. No `border-2`.** Heavy borders read as "draft" and cheapen
  dense analytical layouts.
- Panels are separated by *space + border*, not by shadow.

### 4.3 Radii

| Token | Value | Use |
| :--- | :--- | :--- |
| `radius-xs` | `2px` | Status dots, inline tags, hash chips |
| `radius-sm` | `4px` | Buttons, inputs, badges, small cards |
| `radius-md` | `6px` | Panels, cards, tables, dropdowns |
| `radius-lg` | `8px` | Modals, large panels, mobile nav sheet |
| `radius-pill` | `999px` | Reserved: segmented control, count chips only |

Restrained radii are a load-bearing choice. Large, soft radii read as consumer
app; sharp-ish radii read as instrument. **No `rounded-2xl` / `rounded-3xl`** on
structural panels.

### 4.4 Elevation

Shadows exist only for genuinely floating layers: mobile nav sheet, dropdowns,
modals, sticky sub-nav.

| Token | Value | Use |
| :--- | :--- | :--- |
| `shadow-sticky` | `0 1px 0 0 var(--border), 0 4px 12px -6px rgba(0,0,0,.4)` | Sticky header / section nav |
| `shadow-overlay` | `0 12px 32px -8px rgba(0,0,0,.55), 0 0 0 1px var(--border)` | Mobile sheet, dropdown, modal |

**No `shadow-md` / `shadow-lg` on cards.** Card elevation is a border + surface
step, nothing more.

---

## 5. Components

### 5.1 Global application shell

- **Header**, 56px (`lg`) / 52px (`xs`), opaque `--background`, sticky, `shadow-sticky`.
  Sticky bars are **opaque, not translucent** — blur is reserved for the mobile nav sheet
  (the one genuinely overlaid layer, per §2). Sticky separation comes from the 1px border
  plus `shadow-sticky`, never from a translucent backdrop.
  - Left: wordmark lockup — 28px mark (mono `Y`, `--accent` on `--surface-inset`,
    `radius-sm`, 1px `--border`) + `YUKIRA` in sans 600 `-0.02em`, with an 11px
    mono uppercase `--text-tertiary` subline `INVESTMENT INTELLIGENCE`.
  - Center/left: primary nav, **sans** 13px, `--text-secondary`; active =
    `--text-primary` + 2px `--accent` bottom rule inset from the item edges.
  - Right: theme toggle, then a single high-emphasis account control
    (signed in → avatar-initial + name + caret menu; signed out → `Sign in`
    primary button).
  - **Explicitly forbidden in the header:** API health pills, governance counters,
  status readouts, engine version strings, developer diagnostics, build metadata.
  These are *system* facts, not *navigation*. System facts appear **contextually**,
  in the panel of the screen whose data they qualify.
- **Mobile:** header collapses to wordmark + menu button. Menu is a full-height
  sheet (`--shadow-overlay`), nav items 44px touch targets, plus sign-in/out.
- **Footer:** compressed to a single disclosure band — 3 link columns
  (Explore / Account / Methodology) plus one line of non-commercial-advice
  disclosure. No marketing pillars, no architecture essay, no feature lists.

### 5.2 Buttons

| Variant | Rest | Hover | Active | Use |
| :--- | :--- | :--- | :--- | :--- |
| `primary` | `--accent` bg, `--accent-foreground` | `--accent-hover` | 96% | One per view. Execute/dispatch actions. |
| `secondary` | `--surface` bg, 1px `--border` | `--surface-raised` | — | Watchlist, run parameters, defaults |
| `ghost` | transparent, `--text-secondary` | `--surface` | — | Tabular toggles, disclosure, dismiss |
| `danger` | transparent, 1px `danger-border`, `danger-fg` | `danger-bg` | — | Destructive only |

- Height `32px` (`sm`) / `36px` (`md`). Padding `0 / 12px`.
- `radius-sm`, `label` type, weight 500, `gap-6px` to any leading icon (14px).
- **Never** `shadow-md`. `hover:opacity-90` is banned; use real token transitions.
- Icon-only buttons are 32×32 with a required `aria-label`.

### 5.3 Inputs

- Height 32/36px, `radius-sm`, bg `--surface-inset`, 1px `--border`.
- Focus: 1px `--accent` border + `0 0 0 3px --accent-muted` ring. No `ring-1`.
- Placeholder `--text-disabled`. Label above input, `label` type, `--text-secondary`.
- Search inputs carry a 16px leading icon at 10px inset and a clear affordance.
- Selects use a 16px chevron at 10px right inset over `--surface-inset`.
- Date/ISO inputs remain mono — they carry data.

### 5.4 Panels

- bg `--surface`, 1px `--border`, `radius-md`, padding `20px` (`lg`) / `16px` (`md`).
- Panel header: eyebrow (mono uppercase 11px `--text-tertiary`) + title
  (`h3` sans) + optional right-aligned action. Bottom rule `--border` with
  `16px` clearance beneath.
- **Maximum two nested levels.** A panel inside a panel inside a panel is a
  hierarchy failure.

### 5.5 Metric tile — the core analytical unit

Replaces the flat "metric card". Reads top-to-bottom as an evidence chain:

```
┌────────────────────────────────────────────┐
│ RETURN QUALITY                    [CANDIDATE]  ← eyebrow + status badge
│ Simple Period Return                            ← h3 sans, metric name
│ ──────────────────────────────────────────    ← border-subtle
│  +2.4491%                                      ← data-lg mono tabular
│  PERCENTAGE · 2024-01-01 → 2024-01-15          ← mono-xs context line
│  ──────────────────────────────────────────
│  Measures realized point-to-point capital    ← body-sm interpretation,
│  appreciation across the selected window.       max 3 lines
│  ▸ Formula · Assumptions · Limitation         ← ghost disclosure trigger
└────────────────────────────────────────────┘
```

Rules:
- Value is the visual anchor. `data-lg` mono, tabular, `--text-primary`.
- **Missing data never renders as `0` or blank.** It renders the literal
  `Not available` in `--text-disabled` mono, with the reason beneath.
- Status badge is **always visible**, not hidden behind disclosure. Approved
  vs candidate is decision-critical and cannot be an interaction away.
- Interpretation is prose (sans), never mono.
- A metric exposes at most 4 progressive levels, via one disclosure row:
  `Formula` · `Interpretation` · `Assumptions` · `Limitation`.
- Metric identity code (`RET-02`) is available via the disclosure row or `title`,
  never as the card title.

### 5.6 Tables

- Wrapper: `--surface`, 1px `--border`, `radius-md`, `overflow-x: auto` on mobile.
- Header: `--surface-raised`, mono uppercase 11px `--text-tertiary`,
  `12px 16px` cell padding, sticky `top` when inside a tall scroll region.
- Body: sans `body-sm` for text columns, **mono `data-sm` tabular, right-aligned**
  for every numeric column.
- Row separator `1px --border` at 60% opacity. Row hover: `--surface-raised`.
- No vertical gridlines. No zebra striping — it fights the row hover.
- Numeric columns get explicit `text-align: right` + `tabular-nums` so digits
  align on the decimal, which is what makes financial tables scannable.
- Responsive: below `md`, tables that exceed 4 columns become
  **stacked key/value disclosure rows** (label above value, per-record card).
  The fund/analysis lineage tables keep horizontal scroll because the column set
  *is* the evidence.

### 5.7 Status badges

- Height 20px, `radius-xs`, `padding 0 6px`, mono uppercase 10px, weight 600,
  `letter-spacing .06em`, 1px border in the semantic `--border`.
- 6px status dot in `--fg`, `currentColor`.
- Never use badges for values, categories, or navigation. Only for state.

### 5.8 Governance & methodology markers

- `MethodologyBadge` — retained as a component; renders the lifecycle state
  (`APPROVED` / `CANDIDATE` / `IMPLEMENTED` / `UNAVAILABLE`) in semantic color.
- Governance **counts** live only inside `/methodology` and in metric disclosure
  rows. They are never global navigation chrome.
- A short inline **"why this matters"** affordance links a metric's status to the
  governance page; the link is a text affordance, not a nav pill.

### 5.9 Charts

- Plot area bg `--surface-inset`, 1px `--border`, `radius-md`.
- Gridlines: `--series-muted`, 1px, dashed. No more than 4 horizontal lines.
- Axis labels: mono 11px `--text-tertiary`. Y-axis values right-aligned in a
  reserved 48px gutter so they never overlap the plot.
- Series stroke 1.5px, `linecap: round`. Area fill at 12% alpha max.
- **Direct labelling mandatory.** Series name rendered at the line end, not in a
  legend box. Legend boxes are only permitted for a third series.
- Crosshair tooltip: `--surface-raised` + `--shadow-overlay`, 1px `--border-strong`,
  mono values, `radius-sm`.
- Missing observations: render a **gap**, never interpolate, never zero-fill.
- Empty plot state: centered mono `Not available` + reason, on `--surface-inset`
  with dashed `--border`.

### 5.10 Tabs / disclosure levels

- Underline tabs (not pills) for analytical disclosure: 1px bottom border on the
  container, 2px `--accent` bottom rule on the active tab, sans `label` type.
- Mobile: horizontal scroll with edge fade, or `<select>` for >5 sections.
- Each analytical level keeps a **one-line description** visible above the panel so
  the disclosure depth is self-explanatory.

### 5.11 Evidence / provenance blocks

- `ProvenanceCard` → **Provenance panel**: a definition-list grid
  (`label` above `mono-xs` value) at 2–3 columns, values in `--surface-inset` wells
  with `select-all` for digests.
- Full SHA-256 wraps across lines in mono 11px; never truncated without an
  expand/select affordance.
- Source artifact entries: id + byte size + retrieval timestamp as an eyebrow row;
  digest in a well; source URL as a truncated link with full value in `title`.

### 5.12 Risk presentation

- Drawdown regions use `--series-neg` fill at 14% with a 1px `--series-neg` edge.
- Drawdown trough annotated inline with value and recovery status.
- Risk metric tiles carry a 2px `--risk` left rule — the *only* place a colored
  left rule is used, so it always means "capital impairment".

### 5.13 Epistemic distinction block

The observation → interpretation → limitation chain, as a 3-column grid with a
1px left rule per column (rule color = `operational` / `info` / `candidate`).
Column headers are mono uppercase eyebrows numbered `01 / 02 / 03`. This structure
is a signature YUKIRA pattern and must be visually unmistakable.

### 5.14 System-state surfaces (replacing header status pills)

- Loading → skeleton blocks matching final layout geometry, `animate-pulse` at
  low alpha, with a mono eyebrow `Resolving point-in-time observations…`.
- Error / API unreachable → inline `--critical` panel scoped to the panel whose
  data failed, with mono reason code and a `Retry` secondary button. The rest of
  the page stays usable.
- Empty → `--surface-inset` dashed-border well, mono `Not available`, sans reason,
  one contextual action.
- Health diagnostics remain available on demand via a **"System status"** control
  in the footer, which opens a panel reporting backend reachability, engine
  version, and knowledge-cutoff state. It is opt-in, never ambient.

---

## 6. Accessibility

- All text meets `4.5:1`; all non-text UI meets `3:1`. Semantic colors are tuned
  against the actual surface they sit on in **both** themes.
- Focus is always visible: 1px `--accent` + 3px `--accent-muted` ring. Never
  `outline-none` without a replacement.
- Landmarks: one `<main>` per route, `<nav aria-label>`, `<table>` with real
  `<thead>`/`<th scope>`.
- Interactive targets ≥32px desktop / 44px mobile.
- Status is never conveyed by color alone — every badge carries a text token.
- `prefers-reduced-motion` disables all transitions, pulses, and smooth scroll.

---

## 7. Motion

Restrained, functional, hardware-accelerated.

| Purpose | Spec |
| :--- | :--- |
| Hover / focus | `background-color` `120ms` `ease-out` |
| Disclosure expand | `grid-template-rows` `0fr→1fr`, `180ms` |
| Sheet / dropdown | `transform: translateY(-4px)→0` + opacity, `160ms` |
| Skeleton | `opacity 1.4s` `ease-in-out` infinite |

No spring physics, no parallax, no scroll-jacking, no ambient loops other than
skeletons. Motion confirms a state change; it never performs.

---

## 8. Voice

- Institutional register: precise, declarative, unhurried.
- Sentence case for UI labels. Uppercase mono only for eyebrows and status tokens.
- State vocabulary is fixed and never paraphrased:
  `Approved` · `Candidate methodology` · `Not validated for production` ·
  `Not available` · `Insufficient evidence` · `Pending ingestion` ·
  `Superseded` · `Unverified`.
- Never: "amazing", "robust", "powerful", "smart", "AI-powered", "seamless".
- Never a score, grade, rank, or verdict.

---

## 9. YUKIRA Recognition Cues

The identity must be felt, not stamped. Four cues carry it:

1. **Mono-tabular data discipline.** Numbers align, always. This alone reads as
   instrumentation rather than app.
2. **Near-monochrome substrate.** Color is rationed to governance and risk.
3. **The epistemic chain.** Observation → Interpretation → Limitation, visibly
   separated, on every analytical surface.
4. **The ordinal section spine.** Analytical routes are numbered
   (`01 Identity` … `06 Portfolio`) as a persistent mono eyebrow rail, giving the
   page the structure of a research report.
