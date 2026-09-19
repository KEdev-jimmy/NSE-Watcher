const assert = require('node:assert/strict');
const test = require('node:test');

const {
  normalizedComparable,
  buildFieldQuality,
  evidenceFor,
  normalizeWebsite,
  normalizeLocation,
  parseFinancials,
  parseRatios,
  mergeProfile,
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
      <tr><th>Revenue Growth</th><td>6.50%</td><td>30.17%</td><td>35.00%</td></tr>
      <tr><th>Net Income Net Income Growth</th><td>857.54</td><td>272.24</td><td>116.27</td></tr>
      <tr><th>Net Income Growth</th><td>215.00%</td><td>133.91%</td><td>10.00%</td></tr>
      <tr><th>Earnings Per Share EPS Growth</th><td>3.28</td><td>1.04</td><td>0.45</td></tr>
      <tr><th>EPS Growth</th><td>150.00%</td><td>133.91%</td><td>10.00%</td></tr>
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
  assert.equal(result.profile.financialProviderUpdatedAt, '');
  assert.equal(result.profile.financialPageCheckedAt, '');
});


test('ratio parser selects the explicit Current column and exposes its basis', () => {
  const html = `
    <table>
      <tr><th>Fiscal Year</th><td>Current</td><td>FY 2025</td><td>FY 2024</td></tr>
      <tr><th>Period Ending</th><td>Sep '26</td><td>Dec '25</td><td>Dec '24</td></tr>
      <tr><th>Market Capitalization</th><td>6,869</td><td>5,278</td><td>1,563</td></tr>
      <tr><th>PE Ratio</th><td>8.00</td><td>19.39</td><td>13.44</td></tr>
      <tr><th>PB Ratio</th><td>2.39</td><td>2.15</td><td>0.79</td></tr>
      <tr><th>Debt / Equity Ratio</th><td>0.32</td><td>0.34</td><td>0.34</td></tr>
      <tr><th>Return on Equity (ROE)</th><td>34.80%</td><td>12.30%</td><td>6.05%</td></tr>
      <tr><th>Dividend Yield</th><td>2.64%</td><td>3.80%</td><td>5.88%</td></tr>
    </table>`;

  const result = parseRatios(html);

  assert.equal(result.marketCap, '6869000000');
  assert.equal(result.pe, '8.00');
  assert.equal(result.pb, '2.39');
  assert.equal(result.debtToEquity, '0.32');
  assert.equal(result.roe, '34.80%');
  assert.equal(result.dividendYield, '2.64%');
  assert.equal(result.ratioBasis, 'Current');
  assert.equal(result.ratioPeriod, "Sep '26");
});

test('evidence uses the latest financial history rows', () => {
  const history = Array.from({ length: 10 }, (_, index) => ({
    period: `FY ${2016 + index}`,
    revenue: String(100 + index),
    profit: String(50 + index),
    eps: String(1 + index),
    providerUpdatedAt: '2026-09-01',
    pageCheckedAt: '2026-09-02',
  }));

  const evidence = evidenceFor(
    {},
    {},
    {},
    history,
    [],
    { financialsUrl: 'https://example.com/financials', ratiosUrl: '' },
    '2026-09-19T00:00:00.000Z',
    'SCOM.KE'
  );

  const periods = evidence
    .filter(item => /^Revenue FY/.test(item.claim))
    .map(item => item.period);

  assert.deepEqual(periods, [
    'FY 2018', 'FY 2019', 'FY 2020', 'FY 2021',
    'FY 2022', 'FY 2023', 'FY 2024', 'FY 2025',
  ]);
});


test('financial history rows and evidence retain provider provenance', () => {
  const html = `
    <table>
      <tr><th>Fiscal Year</th><td>FY 2025</td><td>FY 2024</td></tr>
      <tr><th>Period Ending</th><td>Dec '25</td><td>Dec '24</td></tr>
      <tr><th>Revenue</th><td>1,061</td><td>815.23</td></tr>
      <tr><th>Net Income</th><td>272.24</td><td>116.27</td></tr>
      <tr><th>EPS</th><td>1.04</td><td>0.45</td></tr>
    </table>
    <p>Data Source: S&amp;P Global Market Intelligence Last updated: Jul 9, 2026</p>
    <p>Last checked: Sep 17, 2026</p>`;
  const parsed = parseFinancials(html);

  assert.ok(parsed.financialHistory.length >= 1);
  assert.equal(parsed.financialHistory.at(-1).providerUpdatedAt, '2026-07-09');
  assert.equal(parsed.financialHistory.at(-1).pageCheckedAt, '2026-09-17');

  const evidence = evidenceFor(
    parsed.profile,
    {},
    parsed.profile,
    parsed.financialHistory,
    [],
    { financialsUrl: 'https://example.com/financials', ratiosUrl: '' },
    '2026-09-19T00:00:00.000Z',
    'SCOM.KE'
  );
  const revenueEvidence = evidence.find(item => item.claim === "Revenue FY 2025 • Dec '25");
  assert.equal(revenueEvidence.providerUpdatedAt, '2026-07-09');
  assert.equal(revenueEvidence.providerCheckedAt, '2026-09-17');
  assert.equal(revenueEvidence.period, "FY 2025 • Dec '25");
  assert.equal(parsed.financialHistory.at(-2).period, "FY 2024 • Dec '24");
});

test('financial parser carries provider update/check dates separately from statement period', () => {
  const html = `
    <table>
      <tr><th>Fiscal Year</th><td>TTM</td><td>FY 2025</td></tr>
      <tr><th>Period Ending</th><td>Jun '26</td><td>Dec '25</td></tr>
      <tr><th>Revenue</th><td>1,760</td><td>1,061</td></tr>
    </table>
    <p>Data Source: S&amp;P Global Market Intelligence Last updated: Jul 9, 2026</p>
    <p>Last checked: Sep 17, 2026</p>`;
  const result = parseFinancials(html);
  assert.equal(result.profile.financialProviderUpdatedAt, '2026-07-09');
  assert.equal(result.profile.financialPageCheckedAt, '2026-09-17');
  assert.match(result.profile.financialPeriod, /FY 2025/);
  assert.match(result.profile.financialPeriod, /Dec '25/);
});

test('ratio parser carries provider update/check dates separately from current ratio period', () => {
  const html = `
    <table>
      <tr><th>Fiscal Year</th><td>Current</td><td>FY 2025</td></tr>
      <tr><th>Period Ending</th><td>Sep '26</td><td>Dec '25</td></tr>
      <tr><th>PE Ratio</th><td>8.00</td><td>19.39</td></tr>
    </table>
    <p>Data Source: S&amp;P Global Market Intelligence Last updated: Jul 9, 2026</p>
    <p>Last checked: Sep 17, 2026</p>`;
  const result = parseRatios(html);
  assert.equal(result.ratioProviderUpdatedAt, '2026-07-09');
  assert.equal(result.ratioPageCheckedAt, '2026-09-17');
  assert.equal(result.ratioPeriod, "Sep '26");
});

test('ratio parser does not guess a ratio basis when Current is missing', () => {
  const html = `
    <table>
      <tr><th>Fiscal Year</th><td>FY 2025</td><td>FY 2024</td></tr>
      <tr><th>Period Ending</th><td>Dec '25</td><td>Dec '24</td></tr>
      <tr><th>PE Ratio</th><td>19.39</td><td>13.44</td></tr>
    </table>`;

  const result = parseRatios(html);

  assert.equal(result.pe, '');
  assert.equal(result.ratioBasis, 'UNKNOWN');
  assert.equal(result.ratioPeriod, '');
});


test('merged company profile preserves external financial unit and reporting period', () => {
  const merged = mergeProfile(
    { revenue: 'MyStocks value' },
    {
      revenue: '1,061',
      financialUnit: 'Millions KES',
      financialPeriod: "FY 2025 • Dec '25",
    }
  );

  assert.equal(merged.revenue, '1,061');
  assert.equal(merged.financialUnit, 'Millions KES');
  assert.equal(merged.financialPeriod, "FY 2025 • Dec '25");
});
