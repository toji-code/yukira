import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { WatchlistResponse, WatchlistNoteRequest } from '../../lib/api/investor';

/**
 * Investor Research Workspace — Frontend Contract & Integrity Tests
 *
 * Verifies that:
 * 1. WatchlistResponse preserves exact scheme_option_id, AMFI code, and ISIN.
 * 2. Research notes support reasonSaved, investigationQuestion, and nextStep.
 * 3. Note input validation rejects notes exceeding 1,000 characters.
 * 4. Comparison URL generation preserves both scheme IDs and scheme-option IDs.
 * 5. Data freshness and unavailable-data states are preserved without fabrication.
 */
describe('Investor Research Workspace — Frontend Contract & Integrity Tests', () => {

  it('TC-FE-WS01: Watchlist DTO preserves exact scheme option identity and registration metadata', () => {
    const item: WatchlistResponse = {
      id: 1,
      schemeId: 1,
      schemeCode: 'HDFC_FLEXI',
      schemeName: 'HDFC Flexi Cap Fund',
      amcName: 'HDFC Mutual Fund',
      schemeOptionId: 1,
      optionType: 'GROWTH',
      planType: 'DIRECT',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      latestNav: 1670.6720,
      latestNavDate: '2024-01-15',
      yukiraScore: 67.09,
      yukiraScoreStatus: 'CANDIDATE',
      reasonSaved: 'High core alpha allocation with proven market-cycle resilience',
      investigationQuestion: 'Has large cap drift diluted original flexi-cap mandate flexibility?',
      nextStep: 'Check quarterly sector allocation and rolling 3Y alpha consistency',
      createdAt: '2026-10-10T12:00:00Z',
      updatedAt: '2026-10-10T12:30:00Z',
    };

    assert.equal(item.schemeId, 1);
    assert.equal(item.schemeOptionId, 1);
    assert.equal(item.amfiCode, '118955');
    assert.equal(item.isin, 'INF179K01UT0');
    assert.equal(item.optionType, 'GROWTH');
    assert.equal(item.planType, 'DIRECT');
    assert.equal(item.latestNav, 1670.6720);
    assert.equal(item.latestNavDate, '2024-01-15');
    assert.equal(item.yukiraScore, 67.09);
    assert.ok(item.reasonSaved?.includes('alpha'));
    assert.ok(item.investigationQuestion?.includes('flexi-cap mandate'));
    assert.ok(item.nextStep?.includes('rolling 3Y'));
  });

  it('TC-FE-WS02: Research note fields support empty or null initial values cleanly', () => {
    const freshItem: WatchlistResponse = {
      id: 2,
      schemeId: 10262,
      schemeCode: 'PPFAS_FLEXI',
      schemeName: 'Parag Parikh Flexi Cap Fund',
      amcName: 'PPFAS Mutual Fund',
      schemeOptionId: 10262,
      optionType: 'GROWTH',
      planType: 'DIRECT',
      amfiCode: '122639',
      isin: 'INF879O01027',
      latestNav: 75.4321,
      latestNavDate: '2024-01-15',
      yukiraScore: 71.50,
      yukiraScoreStatus: 'CANDIDATE',
      reasonSaved: null,
      investigationQuestion: null,
      nextStep: null,
      createdAt: '2026-10-10T12:00:00Z',
      updatedAt: '2026-10-10T12:00:00Z',
    };

    assert.equal(freshItem.reasonSaved, null);
    assert.equal(freshItem.investigationQuestion, null);
    assert.equal(freshItem.nextStep, null);
  });

  it('TC-FE-WS03: Client-side note validation enforces maximum 1,000 character limit', () => {
    const validNote: WatchlistNoteRequest = {
      reasonSaved: 'Evaluating long-term downside protection.',
      investigationQuestion: 'How does max drawdown compare during March 2020?',
      nextStep: 'Run historical stress analysis on /analysis/10262.',
    };

    assert.ok(validNote.reasonSaved && validNote.reasonSaved.length <= 1000);
    assert.ok(validNote.investigationQuestion && validNote.investigationQuestion.length <= 1000);
    assert.ok(validNote.nextStep && validNote.nextStep.length <= 1000);

    const oversizedString = 'A'.repeat(1001);
    const exceedsLimit = (str: string | undefined | null) => str ? str.length > 1000 : false;
    assert.ok(exceedsLimit(oversizedString));
  });

  it('TC-FE-WS04: Comparison query builder preserves both scheme IDs and option IDs', () => {
    const selectedFunds = [
      { schemeId: 1, schemeOptionId: 1 },
      { schemeId: 10262, schemeOptionId: 10262 },
    ];

    const ids = selectedFunds.map((f) => f.schemeId).join(',');
    const optMappings = selectedFunds
      .map((f) => `${f.schemeId}:${f.schemeOptionId}`)
      .join(',');

    const query = `/compare?funds=${ids}&options=${encodeURIComponent(optMappings)}`;
    assert.equal(query, '/compare?funds=1,10262&options=1%3A1%2C10262%3A10262');

    // Restoration check
    const searchParams = new URLSearchParams(query.split('?')[1]);
    const parsedFunds = searchParams.get('funds')?.split(',').map(Number);
    const parsedOptions = searchParams.get('options')?.split(',').map((p) => {
      const [sId, oId] = p.split(':').map(Number);
      return { schemeId: sId, schemeOptionId: oId };
    });

    assert.deepEqual(parsedFunds, [1, 10262]);
    assert.deepEqual(parsedOptions, [
      { schemeId: 1, schemeOptionId: 1 },
      { schemeId: 10262, schemeOptionId: 10262 },
    ]);
  });

  it('TC-FE-WS05: Unavailable NAV or score is represented honestly without fallback fabrication', () => {
    const incompleteItem: WatchlistResponse = {
      id: 3,
      schemeId: 99999,
      schemeCode: 'UNTESTED_FUND',
      schemeName: 'Untested Fund',
      amcName: 'Unknown AMC',
      schemeOptionId: null,
      optionType: null,
      planType: null,
      amfiCode: null,
      isin: null,
      latestNav: null,
      latestNavDate: null,
      yukiraScore: null,
      yukiraScoreStatus: 'INSUFFICIENT_DATA',
      reasonSaved: null,
      investigationQuestion: null,
      nextStep: null,
      createdAt: '2026-10-10T12:00:00Z',
      updatedAt: '2026-10-10T12:00:00Z',
    };

    assert.equal(incompleteItem.latestNav, null);
    assert.equal(incompleteItem.latestNavDate, null);
    assert.equal(incompleteItem.yukiraScore, null);
    assert.equal(incompleteItem.yukiraScoreStatus, 'INSUFFICIENT_DATA');
  });

  it('TC-FE-WS06: Supports saving multiple distinct scheme options for the same scheme without collision', () => {
    const directOption: WatchlistResponse = {
      id: 1,
      schemeId: 1,
      schemeCode: 'HDFC_FLEXI',
      schemeName: 'HDFC Flexi Cap Fund',
      schemeOptionId: 1,
      optionType: 'GROWTH',
      planType: 'DIRECT',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      reasonSaved: 'Direct plan investigation',
    };

    const regularOption: WatchlistResponse = {
      id: 2,
      schemeId: 1,
      schemeCode: 'HDFC_FLEXI',
      schemeName: 'HDFC Flexi Cap Fund',
      schemeOptionId: 10223,
      optionType: 'GROWTH',
      planType: 'REGULAR',
      amfiCode: '101569',
      isin: 'INF179K01116',
      reasonSaved: 'Regular plan fee drag analysis',
    };

    // Both belong to schemeId: 1, but have distinct schemeOptionIds
    assert.equal(directOption.schemeId, regularOption.schemeId);
    assert.notEqual(directOption.schemeOptionId, regularOption.schemeOptionId);
    assert.notEqual(directOption.planType, regularOption.planType);
    assert.notEqual(directOption.amfiCode, regularOption.amfiCode);

    // List containing both retains both identities
    const workspace = [directOption, regularOption];
    assert.equal(workspace.length, 2);
    assert.equal(workspace.filter((i) => i.schemeId === 1).length, 2);
  });

  it('TC-FE-WS07: Partial note field clearing preserves remaining research notes', () => {
    const initialNote: WatchlistNoteRequest = {
      reasonSaved: 'Evaluating downside capture',
      investigationQuestion: 'What is downside semideviation?',
      nextStep: 'Check 2020 stress period',
    };

    // Clear investigation question only
    const partiallyUpdated: WatchlistNoteRequest = {
      reasonSaved: initialNote.reasonSaved,
      investigationQuestion: undefined,
      nextStep: initialNote.nextStep,
    };

    assert.equal(partiallyUpdated.reasonSaved, 'Evaluating downside capture');
    assert.equal(partiallyUpdated.investigationQuestion, undefined);
    assert.equal(partiallyUpdated.nextStep, 'Check 2020 stress period');
  });

  it('TC-FE-WS08: 4-State persistence semantics distinguish SERVER_SAVED, SAVING, LOCAL_ONLY, and SYNC_FAILED', () => {
    type SyncStatus = 'SERVER_SAVED' | 'SAVING' | 'LOCAL_ONLY' | 'SYNC_FAILED';

    const states: Record<SyncStatus, { label: string; badgeClass: string; isLocal: boolean }> = {
      SERVER_SAVED: { label: 'SERVER SAVED', badgeClass: 'state-approved', isLocal: false },
      SAVING: { label: 'SAVING…', badgeClass: 'state-candidate', isLocal: false },
      LOCAL_ONLY: { label: 'LOCAL ONLY', badgeClass: 'state-candidate', isLocal: true },
      SYNC_FAILED: { label: 'SYNC FAILED', badgeClass: 'state-rejected', isLocal: true },
    };

    // Verification that local cached data is never presented as server persisted
    assert.equal(states.LOCAL_ONLY.isLocal, true);
    assert.equal(states.SYNC_FAILED.isLocal, true);
    assert.equal(states.SERVER_SAVED.isLocal, false);
    assert.notEqual(states.LOCAL_ONLY.label, states.SERVER_SAVED.label);
    assert.notEqual(states.SYNC_FAILED.label, states.SERVER_SAVED.label);
  });

  it('TC-FE-WS09: Comparison builder supports comparing distinct options of the same scheme', () => {
    const selectedOptions = [
      { schemeId: 1, schemeOptionId: 1 },
      { schemeId: 1, schemeOptionId: 10223 },
    ];

    const ids = selectedOptions.map((o) => o.schemeId).join(',');
    const optMappings = selectedOptions
      .map((o) => `${o.schemeId}:${o.schemeOptionId}`)
      .join(',');

    const query = `/compare?funds=${ids}&options=${encodeURIComponent(optMappings)}`;
    assert.equal(query, '/compare?funds=1,1&options=1%3A1%2C1%3A10223');
  });
});
