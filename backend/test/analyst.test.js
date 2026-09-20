const assert = require('node:assert/strict');
const test = require('node:test');

const {
  buildEvidencePacket,
  validateCitations,
  buildGeminiRequest,
  extractGeminiAnswer,
  extractGeminiAnalysis,
  validateStructuredAnalysis,
  buildCompanyStoryRequest,
  extractGeminiStory,
  validateCompanyStory,
  normalizeGeminiStatus,
} = require('../api/analyst');
const { buildNseIntelligenceContext, validateNseIntelligenceContext } = require('../lib/nseIntelligenceContext');

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
  assert.equal(request.generationConfig.responseMimeType, 'application/json');
  assert.equal(request.generationConfig.responseSchema.type, 'object');
  assert.deepEqual(request.generationConfig.responseSchema.required, ['headline', 'summary', 'signals', 'interpretation', 'unknowns']);
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


test('extractGeminiAnalysis parses the structured Analyst response', () => {
  const analysis = extractGeminiAnalysis({
    candidates: [{
      content: {
        parts: [{
          text: JSON.stringify({
            headline: 'Revenue improved',
            summary: 'The company reported stronger revenue.',
            signals: [{ type: 'CHANGE', title: 'Revenue', detail: 'Revenue increased.', evidenceIds: ['E1'] }],
            interpretation: 'This may indicate stronger business activity.',
            unknowns: ['The supplied evidence does not explain the cause.'],
          }),
        }],
      },
    }],
  });

  assert.equal(analysis.headline, 'Revenue improved');
  assert.equal(analysis.signals[0].evidenceIds[0], 'E1');
});

test('validateStructuredAnalysis accepts only supplied evidence IDs', () => {
  const analysis = {
    signals: [
      { evidenceIds: ['E1', 'E2'] },
      { evidenceIds: ['E999'] },
    ],
  };
  const result = validateStructuredAnalysis(analysis, [{ id: 'E1' }, { id: 'E2' }]);
  assert.equal(result.valid, false);
  assert.deepEqual(result.invalidIds, ['E999']);

  const valid = validateStructuredAnalysis(
    { signals: [{ evidenceIds: ['E1'] }] },
    [{ id: 'E1' }],
  );
  assert.equal(valid.valid, true);
});


test('buildNseIntelligenceContext combines verified company intelligence without raw-provider noise', () => {
  const context = buildNseIntelligenceContext({
    company: {
      symbol: 'SCOM.KE',
      source: 'NSE Watcher multi-source company intelligence',
      fetchedAt: '2026-09-20T08:00:00Z',
      profile: { sector: 'Telecommunications', revenue: '100', ignoredField: 'drop' },
      financialHistory: [{ period: 'FY 2025', revenue: '100', profit: '20', eps: '1.2' }],
      dividends: [{ amount: '1.00', exDate: '2026-04-01', source: 'Provider' }],
      evidence: [
        { id: 'E1', claim: 'Revenue', value: '100', source: 'Provider', url: 'https://example.com', period: 'FY 2025' },
      ],
      dataQuality: { conflictCount: 0, evidenceCount: 1 },
    },
  });

  assert.equal(context.contextVersion, 1);
  assert.equal(context.symbol, 'SCOM.KE');
  assert.equal(context.company.profile.sector, 'Telecommunications');
  assert.equal(context.company.profile.ignoredField, undefined);
  assert.equal(context.company.financialHistory.length, 1);
  assert.equal(context.company.dividends.length, 1);
  assert.equal(context.evidence[0].id, 'E1');
  assert.equal(context.freshness.fetchedAt, '2026-09-20T08:00:00Z');
});

test('validateNseIntelligenceContext rejects duplicate evidence IDs', () => {
  const result = validateNseIntelligenceContext({
    evidence: [{ id: 'E1' }, { id: 'E1' }],
  });
  assert.equal(result.valid, false);
  assert.deepEqual(result.duplicateIds, ['E1']);
});


test('buildCompanyStoryRequest creates a structured evidence-grounded story request', () => {
  const request = buildCompanyStoryRequest({ symbol: 'SCOM.KE', evidence: [{ id: 'E1', claim: 'Revenue', value: '100' }] });
  assert.equal(request.generationConfig.responseMimeType, 'application/json');
  assert.equal(request.generationConfig.responseSchema.type, 'object');
  assert.match(request.contents[0].parts[0].text, /Create a Company Story/i);
  assert.match(request.contents[0].parts[0].text, /"id": "E1"/);
});

test('extractGeminiStory parses a structured Company Story', () => {
  const story = extractGeminiStory({ candidates: [{ content: { parts: [{ text: JSON.stringify({
    title: 'Company story', business: 'Telecommunications', performance: 'Reported performance is available.',
    changes: ['Revenue changed'], events: ['A dated event'], interpretation: 'The evidence may indicate change.',
    unknowns: ['Cause is unknown'], evidenceIds: ['E1'],
  }) }] } }] });
  assert.equal(story.title, 'Company story');
  assert.deepEqual(story.evidenceIds, ['E1']);
});

test('validateCompanyStory rejects evidence IDs outside the supplied context', () => {
  const result = validateCompanyStory({ evidenceIds: ['E1', 'E999'] }, [{ id: 'E1' }]);
  assert.equal(result.valid, false);
  assert.deepEqual(result.invalidIds, ['E999']);
});

test('normalizeGeminiStatus prevents upstream 5xx errors from becoming app 5xx responses', () => {
  assert.equal(normalizeGeminiStatus(500), 502);
  assert.equal(normalizeGeminiStatus(503), 502);
  assert.equal(normalizeGeminiStatus(429), 429);
  assert.equal(normalizeGeminiStatus(400), 400);
});
