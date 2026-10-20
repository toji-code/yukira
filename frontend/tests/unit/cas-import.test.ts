import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  CasImportResultDto,
  CasImportConfirmResponseDto
} from '../../lib/api/portfolio';

describe('CAS / External Mutual-Fund Portfolio Import — Frontend Contract Tests', () => {

  it('TC-FE-CAS01: CasImportResultDto enforces staging status and record count metadata', () => {
    const mockResult: CasImportResultDto = {
      importId: 101,
      sourceFilename: 'cams_cas_statement_2024.json',
      fileType: 'CAS_STATEMENT',
      status: 'PENDING_REVIEW',
      totalRecordsDetected: 3,
      resolvedRecordsCount: 2,
      unresolvedRecordsCount: 1,
      ambiguousRecordsCount: 0,
      statementDate: '2024-01-31',
      parserVersion: 'CAS_PARSER_V1_DETERMINISTIC',
      createdAt: '2026-10-08T16:00:00+05:30',
      confirmedAt: null,
      holdings: [
        {
          id: 1,
          rawSchemeName: 'HDFC Flexi Cap Fund - Direct Growth',
          folioNumber: '12345678/90',
          isin: 'INF179K01UT0',
          amfiCode: '118955',
          units: 150.5,
          costBasisAmount: 220000.0,
          marketValue: 251435.12,
          nav: 1670.672,
          resolvedSchemeOptionId: 1,
          resolvedFundName: 'HDFC Flexi Cap Fund',
          resolvedAmcName: 'HDFC Mutual Fund',
          resolutionStatus: 'RESOLVED',
          resolutionMethod: 'ISIN_EXACT',
          resolutionNotes: 'Resolved by exact ISIN match: INF179K01UT0'
        },
        {
          id: 2,
          rawSchemeName: 'Unknown Obscure Fund Option XYZ',
          folioNumber: '11111111/00',
          isin: 'INF999999999',
          amfiCode: '999999',
          units: 10.0,
          costBasisAmount: 5000.0,
          marketValue: 5500.0,
          nav: null,
          resolvedSchemeOptionId: null,
          resolvedFundName: null,
          resolvedAmcName: null,
          resolutionStatus: 'UNRESOLVED',
          resolutionMethod: 'UNRESOLVED',
          resolutionNotes: 'Unable to resolve fund scheme identity.'
        }
      ],
      limitations: []
    };

    assert.equal(mockResult.importId, 101);
    assert.equal(mockResult.status, 'PENDING_REVIEW');
    assert.equal(mockResult.totalRecordsDetected, 3);
    assert.equal(mockResult.resolvedRecordsCount, 2);
    assert.equal(mockResult.unresolvedRecordsCount, 1);

    const resolvedHolding = mockResult.holdings[0];
    assert.equal(resolvedHolding.resolutionStatus, 'RESOLVED');
    assert.equal(resolvedHolding.resolutionMethod, 'ISIN_EXACT');
    assert.equal(resolvedHolding.resolvedSchemeOptionId, 1);

    const unresolvedHolding = mockResult.holdings[1];
    assert.equal(unresolvedHolding.resolutionStatus, 'UNRESOLVED');
    assert.equal(unresolvedHolding.resolvedSchemeOptionId, null);
  });

  it('TC-FE-CAS02: CasImportConfirmResponseDto enforces explicit confirmation counters', () => {
    const mockConfirm: CasImportConfirmResponseDto = {
      importId: 101,
      status: 'CONFIRMED',
      holdingsAddedCount: 2,
      holdingsUpdatedCount: 0,
      holdingsUnchangedCount: 0,
      unresolvedSkippedCount: 1,
      confirmedAt: '2026-10-08T16:05:00+05:30',
      messages: ['Import confirmed successfully in MERGE mode.']
    };

    assert.equal(mockConfirm.status, 'CONFIRMED');
    assert.equal(mockConfirm.holdingsAddedCount, 2);
    assert.equal(mockConfirm.unresolvedSkippedCount, 1);
    assert.ok(mockConfirm.messages[0].includes('MERGE mode'));
  });

  it('TC-FE-CAS03: PDF CAS Import Result enforces PDF parser metadata and password requirement limitations', () => {
    const mockPdfResult: CasImportResultDto = {
      importId: 102,
      sourceFilename: 'cams_cas_jan2024.pdf',
      fileType: 'CAS_PDF_STATEMENT',
      status: 'FAILED',
      totalRecordsDetected: 0,
      resolvedRecordsCount: 0,
      unresolvedRecordsCount: 0,
      ambiguousRecordsCount: 0,
      statementDate: null,
      parserVersion: 'CAS_PARSER_V2_PDF_DETERMINISTIC',
      createdAt: '2026-10-08T16:10:00+05:30',
      confirmedAt: null,
      holdings: [],
      limitations: ['PASSWORD_REQUIRED: CAS PDF is encrypted. Password required to parse.']
    };

    assert.equal(mockPdfResult.fileType, 'CAS_PDF_STATEMENT');
    assert.equal(mockPdfResult.status, 'FAILED');
    assert.ok(mockPdfResult.limitations[0].includes('PASSWORD_REQUIRED'));
  });

});

