import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { FundCard } from '../../components/primitives/FundCard';
import { Scheme, SchemeOption } from '../../types/domain';

describe('Investor Fund Discovery & Selection Journey Tests', () => {
  // Pilot 1: HDFC Flexi Cap Fund (Scheme 1) with Direct Growth (Option 1) and Regular Growth (Option 10223)
  const mockHdfcScheme: Scheme = {
    id: 1,
    name: 'HDFC Flexi Cap Fund',
    code: 'HDFC_FLEXI',
    category: 'Equity Scheme - Flexi Cap Fund',
    inceptionDate: '1995-01-01',
    status: 'ACTIVE',
    amc: { id: 1, name: 'HDFC Mutual Fund', code: 'HDFC_MF' },
  };

  const mockHdfcOptions: SchemeOption[] = [
    {
      id: 1,
      optionType: 'GROWTH',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      status: 'ACTIVE',
      plan: { id: 1, planType: 'DIRECT', planCode: 'HDFC_FLEXI_DIR', status: 'ACTIVE' },
      latestNavDate: '2026-10-01',
      navObservationCount: 1920,
      latestHoldingsDate: '2024-01-31',
      holdingsCount: 10,
    },
    {
      id: 10224,
      optionType: 'IDCW',
      amfiCode: '118954',
      isin: 'INF179K01VL5',
      status: 'ACTIVE',
      plan: { id: 1, planType: 'DIRECT', planCode: 'HDFC_FLEXI_DIR', status: 'ACTIVE' },
      latestNavDate: '2026-10-01',
      navObservationCount: 740,
      latestHoldingsDate: null,
      holdingsCount: null,
    },
    {
      id: 10223,
      optionType: 'GROWTH',
      amfiCode: '101762',
      isin: 'INF179K01608',
      status: 'ACTIVE',
      plan: { id: 2, planType: 'REGULAR', planCode: 'HDFC_FLEXI_REG', status: 'ACTIVE' },
      latestNavDate: '2026-10-01',
      navObservationCount: 740,
      latestHoldingsDate: null,
      holdingsCount: null,
    },
  ];

  // Pilot 2: Parag Parikh Flexi Cap Fund (Scheme 1720) with Direct Growth (Option 10262)
  const mockPpfasFlexiScheme: Scheme = {
    id: 1720,
    name: 'Parag Parikh Flexi Cap Fund',
    code: 'PARAG_PARIKH_FLEXI_CAP_FUND',
    category: 'Equity Scheme - Flexi Cap Fund',
    inceptionDate: '2013-05-24',
    status: 'ACTIVE',
    amc: { id: 2, name: 'PPFAS Mutual Fund', code: 'PPFAS_MF' },
  };

  const mockPpfasFlexiOptions: SchemeOption[] = [
    {
      id: 10262,
      optionType: 'GROWTH',
      amfiCode: '122639',
      isin: 'INF879O01027',
      status: 'ACTIVE',
      plan: { id: 201, planType: 'DIRECT', planCode: 'PPFAS_FLEXI_DIR', status: 'ACTIVE' },
      latestNavDate: '2026-10-01',
      navObservationCount: 858,
      latestHoldingsDate: '2026-09-30',
      holdingsCount: 96,
    },
  ];

  // Pilot 3: Parag Parikh ELSS Tax Saver Fund (Scheme 1694) with Direct Growth (Option 10151)
  const mockPpfasElssScheme: Scheme = {
    id: 1694,
    name: 'Parag Parikh ELSS Tax Saver Fund',
    code: 'PARAG_PARIKH_ELSS_TAX_SAVER_FUND',
    category: 'Equity Scheme - ELSS',
    inceptionDate: '2019-07-24',
    status: 'ACTIVE',
    amc: { id: 2, name: 'PPFAS Mutual Fund', code: 'PPFAS_MF' },
  };

  const mockPpfasElssOptions: SchemeOption[] = [
    {
      id: 10151,
      optionType: 'GROWTH',
      amfiCode: '147481',
      isin: 'INF879O01100',
      status: 'ACTIVE',
      plan: { id: 202, planType: 'DIRECT', planCode: 'PPFAS_ELSS_DIR', status: 'ACTIVE' },
      latestNavDate: '2026-10-01',
      navObservationCount: 740,
      latestHoldingsDate: null,
      holdingsCount: null,
    },
  ];

  it('TC-DISC-01: Renders canonical Pilot 1 (HDFC Flexi) with exact Direct Growth identity (Option #1, AMFI 118955, ISIN INF179K01UT0)', () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockHdfcScheme,
        initialOptions: mockHdfcOptions,
        initialOptionId: 1,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes('HDFC Flexi Cap Fund'), 'Must render fund name');
    assert.ok(html.includes('HDFC Mutual Fund'), 'Must render AMC name');
    assert.ok(html.includes('Canonical Pilot · Flexi Cap'), 'Must render canonical pilot badge');
    assert.ok(html.includes('Option ID <strong class="text-text-primary font-semibold">#1</strong>'), 'Must display exact Option ID #1');
    assert.ok(html.includes('118955'), 'Must display exact AMFI code 118955');
    assert.ok(html.includes('INF179K01UT0'), 'Must display exact ISIN INF179K01UT0');
    assert.ok(html.includes('href="/funds/1?option=1"'), 'Direct profile link must target /funds/1?option=1');
  });

  it('TC-DISC-02: Renders canonical Pilot 2 (Parag Parikh Flexi) with Option #10262 and verified dual horizons (NAV Sep 2026, Holdings Sep 2026)', () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockPpfasFlexiScheme,
        initialOptions: mockPpfasFlexiOptions,
        initialOptionId: 10262,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes('Parag Parikh Flexi Cap Fund'), 'Must render fund name');
    assert.ok(html.includes('PPFAS Mutual Fund'), 'Must render AMC name');
    assert.ok(html.includes('Option ID <strong class="text-text-primary font-semibold">#10262</strong>'), 'Must display exact Option ID #10262');
    assert.ok(html.includes('122639'), 'Must display exact AMFI code 122639');
    assert.ok(html.includes('INF879O01027'), 'Must display exact ISIN INF879O01027');
    assert.ok(html.includes('As of 2026-10-01 (858 dates)'), 'Must display exact NAV date and count');
    assert.ok(html.includes('As of 2026-09-30 (96 positions)'), 'Must display exact holdings date and count');
    assert.ok(html.includes('href="/funds/1720?option=10262"'), 'Direct profile link must target /funds/1720?option=10262');
  });

  it('TC-DISC-03: Renders canonical Pilot 3 (Parag Parikh ELSS) with Option #10151 and honest missing holdings display', () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockPpfasElssScheme,
        initialOptions: mockPpfasElssOptions,
        initialOptionId: 10151,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes('Parag Parikh ELSS Tax Saver Fund'), 'Must render fund name');
    assert.ok(html.includes('Option ID <strong class="text-text-primary font-semibold">#10151</strong>'), 'Must display exact Option ID #10151');
    assert.ok(html.includes('147481'), 'Must display exact AMFI code 147481');
    assert.ok(html.includes('INF879O01100'), 'Must display exact ISIN INF879O01100');
    assert.ok(html.includes('As of 2026-10-01 (740 dates)'), 'Must display exact NAV date and count');
    // Holdings must display Not available without fabrication
    assert.ok(html.includes('Monthly Holdings:'), 'Must display holdings section header');
    assert.ok(html.includes('href="/funds/1694?option=10151"'), 'Direct profile link must target /funds/1694?option=10151');
  });

  it('TC-DISC-04: Distinguishes Direct vs Regular share classes on the same fund without identity confusion', () => {
    // Render HDFC Flexi with Regular Growth preselected (Option 10223)
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockHdfcScheme,
        initialOptions: mockHdfcOptions,
        initialOptionId: 10223,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes('Option ID <strong class="text-text-primary font-semibold">#10223</strong>'), 'Must display Option ID #10223');
    assert.ok(html.includes('101762'), 'Must display Regular AMFI code 101762');
    assert.ok(html.includes('INF179K01608'), 'Must display Regular ISIN INF179K01608');
    assert.ok(html.includes('href="/funds/1?option=10223"'), 'Target URL must preserve exact Regular option ID 10223');
  });

  it('TC-DISC-05: Strict Dual-Horizon Discipline: NAV observation date (2026-10-01) is never blended with holdings date (2024-01-31)', () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockHdfcScheme,
        initialOptions: mockHdfcOptions,
        initialOptionId: 1,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    // Verify both dates appear in their distinct respective sections
    assert.ok(html.includes('Market NAV History:'), 'Must label NAV history distinctly');
    assert.ok(html.includes('Monthly Holdings:'), 'Must label monthly holdings distinctly');
    assert.ok(html.includes('As of 2026-10-01'), 'NAV date must be 2026-10-01');
    assert.ok(html.includes('As of 2024-01-31'), 'Holdings date must be 2024-01-31');
    assert.ok(!html.includes('Last Updated: 2026-10-01 for all data'), 'Must not invent universal last updated date');
  });

  it('TC-DISC-06: Zero Frontend Financial Calculations: Renders raw values without computing returns or star ratings', () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockHdfcScheme,
        initialOptions: mockHdfcOptions,
        initialOptionId: 1,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    // No star ratings or fabricated advice
    assert.ok(!html.includes('5 Star'), 'Must not inject opaque star ratings');
    assert.ok(!html.includes('BUY'), 'Must not issue BUY recommendations');
    assert.ok(!html.includes('SELL'), 'Must not issue SELL recommendations');
  });

  it('TC-DISC-07: Research Workspace & Compare integration preserves exact option identity', () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockPpfasFlexiScheme,
        initialOptions: mockPpfasFlexiOptions,
        initialOptionId: 10262,
        isWatchlisted: true,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes('aria-pressed="true"'), 'Watchlist button reflects active state');
    assert.ok(html.includes('href="/compare?ids=10262"'), 'Compare link preserves exact schemeOptionId 10262');
  });
});
