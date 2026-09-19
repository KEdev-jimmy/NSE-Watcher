const assert = require('node:assert/strict');
const test = require('node:test');

const {
  normalizedComparable,
  buildFieldQuality,
  evidenceFor,
  normalizeWebsite,
  normalizeLocation,
  parseFinancials,
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


test('financial parser selects the annual FY column instead of TTM when both are present', () => {
  const html = `
    <table>
      <tr><th>Fiscal Year</th><td>TTM</td><td>FY 2025</td><td>FY 2024</td></tr>
      <tr><th>Period Ending</th><td>Jun '26</td><td>Dec '25</td><td>Dec '24</td></tr>
      <tr><th>Revenue Revenue Growth</th><td>1,760</td><td>1,061</td><td>815.23</td></tr>
      <tr><th>Net Income Net Income Growth</th><td>857.54</td><td>272.24</td><td>116.27</td></tr>
      <tr><th>Earnings Per Share EPS Growth</th><td>3.28</td><td>1.04</td><td>0.45</td></tr>
      <tr><th>Profit Margin</th><td>48.73%</td><td>25.66%</td><td>14.26%</td></tr>
    </table>`;

  const result = parseFinancials(html);

  assert.equal(result.profile.revenue, '1,061');
  assert.equal(result.profile.profit, '272.24');
  assert.equal(result.profile.eps, '1.04');
  assert.equal(result.profile.margin, '25.66%');
  assert.match(result.profile.financialPeriod, /FY 2025/);
  assert.match(result.profile.financialPeriod, /Dec '25/);
  assert.equal(result.profile.financialUnit, 'Millions KES');
  assert.equal(result.profile.revenueGrowth, '30.17%');
  assert.equal(result.profile.profitGrowth, '133.91%');
  assert.equal(result.profile.epsGrowth, '133.91%');
});
