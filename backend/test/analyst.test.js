const assert = require('node:assert/strict');
const test = require('node:test');

const {
  buildEvidencePacket,
  validateCitations,
  buildGeminiRequest,
  extractGeminiAnswer,
} = require('../api/analyst');

test('buildEvidencePacket preserves only the evidence fields used by the Analyst', () => {
  const packet = buildEvidencePacket({
    symbol: 'SCOM',
    source: 'MyStocks Africa',
    fetchedAt: '2026-09-20T08:00:00Z',
    profile: { price: 25.5 },
    financialHistory: [{ period: 'FY 2025', revenue: '100' }],
    dividends: [],
    evidence: [
      { id: 'E1', claim: 'Revenue', value: '100', source: 'Provider', url: 'https://example.com', period: 'FY 2025', extra: 'ignored' },
    ],
    dataQuality: { status: 'OK' },
  });

  assert.equal(packet.symbol, 'SCOM');
  assert.equal(packet.evidence.length, 1);
  assert.deepEqual(packet.evidence[0], {
    id: 'E1',
    claim: 'Revenue',
    value: '100',
    source: 'Provider',
    url: 'https://example.com',
    period: 'FY 2025',
  });
  assert.equal(packet.dataQuality.status, 'OK');
});

test('validateCitations accepts only evidence IDs supplied in the packet', () => {
  const evidence = [{ id: 'E1' }, { id: 'E2' }];

  assert.deepEqual(
    validateCitations('Revenue increased [E1]. Profit also changed [E2].', evidence),
    {
      valid: true,
      citedIds: ['E1', 'E2'],
      invalidIds: [],
      warning: '',
    },
  );

  const invalid = validateCitations('Revenue increased [E999].', evidence);
  assert.equal(invalid.valid, false);
  assert.deepEqual(invalid.invalidIds, ['E999']);
});

test('validateCitations flags an answer with no evidence citation', () => {
  const result = validateCitations('The company reported growth.', [{ id: 'E1' }]);

  assert.equal(result.valid, false);
  assert.deepEqual(result.citedIds, []);
  assert.deepEqual(result.invalidIds, []);
  assert.match(result.warning, /did not contain an evidence citation/i);
});

test('buildGeminiRequest keeps the evidence-grounded prompt and output ceiling', () => {
  const request = buildGeminiRequest('What changed?', {
    symbol: 'SCOM',
    evidence: [{ id: 'E1', claim: 'Revenue increased', value: '100' }],
  });

  assert.equal(request.generationConfig.maxOutputTokens, 900);
  assert.equal(request.contents.length, 1);
  assert.match(request.contents[0].parts[0].text, /Use ONLY the supplied evidence packet/i);
  assert.match(request.contents[0].parts[0].text, /What changed\?/);
  assert.match(request.contents[0].parts[0].text, /"id": "E1"/);
});

test('extractGeminiAnswer reads text parts and ignores non-text parts', () => {
  const answer = extractGeminiAnswer({
    candidates: [{
      content: {
        parts: [
          { text: 'What is happening [E1].' },
          { inlineData: { mimeType: 'image/png', data: 'ignored' } },
          { text: ' What may matter [E2].' },
        ],
      },
    }],
  });

  assert.equal(answer, 'What is happening [E1].\n What may matter [E2].');
});

test('extractGeminiAnswer returns empty text when Gemini returns no candidates', () => {
  assert.equal(extractGeminiAnswer({ candidates: [] }), '');
});
