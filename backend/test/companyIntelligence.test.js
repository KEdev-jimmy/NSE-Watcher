const assert = require('node:assert/strict');
const test = require('node:test');

const {
  normalizedComparable,
  buildFieldQuality,
  evidenceFor,
  normalizeWebsite,
  normalizeLocation,
} = require('../lib/companyIntelligence');

test('company field quality marks a single available source as AVAILABLE', () => {
  const quality = buildFieldQuality(
    { revenue: 'KES 10B' },
    { revenue: '' }
  );

  assert.equal(quality.fieldQuality.revenue, 'AVAILABLE');
  assert.deepEqual(quality.fieldSources.revenue, ['MyStocks Africa']);
  assert.equal(Object.keys(quality.conflicts).length, 0);
});

test('company field quality marks missing fields as UNAVAILABLE', () => {
  const quality = buildFieldQuality({}, {});

  assert.equal(quality.fieldQuality.revenue, 'UNAVAILABLE');
  assert.deepEqual(quality.fieldSources.revenue, []);
  assert.equal(Object.keys(quality.conflicts).length, 0);
});

test('equivalent formatting from two sources is not treated as a conflict', () => {
  assert.equal(normalizedComparable('  10.0  '), normalizedComparable('10'));
  const quality = buildFieldQuality(
    { eps: '10.0' },
    { eps: ' 10 ' }
  );

  assert.equal(quality.fieldQuality.eps, 'AVAILABLE');
  assert.equal(Object.keys(quality.conflicts).length, 0);
});

test('different values are marked CONFLICT and both values are preserved', () => {
  const quality = buildFieldQuality(
    { profit: 'KES 8B' },
    { profit: 'KES 9B' }
  );

  assert.equal(quality.fieldQuality.profit, 'CONFLICT');
  assert.deepEqual(quality.conflicts.profit.values, [
    { source: 'MyStocks Africa', value: 'KES 8B' },
    { source: 'StockAnalysis / S&P Global Market Intelligence', value: 'KES 9B' },
  ]);
});

test('conflicted evidence states both source values instead of silently selecting one', () => {
  const evidence = evidenceFor(
    { revenue: 'KES 12B' },
    { revenue: 'KES 10B' },
    { revenue: 'KES 12B' },
    [],
    [],
    { financialsUrl: 'https://example.com/financials', ratiosUrl: '' },
    '2026-09-18T00:00:00.000Z',
    'SCOM.KE'
  );

  assert.equal(evidence.length, 1);
  assert.equal(evidence[0].source, 'CONFLICT');
  assert.match(evidence[0].value, /MyStocks Africa: KES 10B/);
  assert.match(evidence[0].value, /StockAnalysis \/ S&P Global Market Intelligence: KES 12B/);
});


test('company website and headquarters fields are normalized defensively', () => {
  assert.equal(normalizeWebsite('www.example.com/'), 'https://www.example.com');
  assert.equal(normalizeWebsite('https://example.com/'), 'https://example.com');
  assert.equal(normalizeWebsite('not a valid website value'), '');
  assert.equal(normalizeLocation(' HQ: Nairobi, Kenya '), 'Nairobi, Kenya');
  assert.equal(normalizeLocation('Headquarters - Nairobi, Kenya'), 'Nairobi, Kenya');
});
