const assert = require('node:assert/strict');
const test = require('node:test');

const {
  normalizeMarketStatus,
  latestTradingSession,
  chronologicallyOrderedCandles,
  technicalHistoryConfig,
  normalizeTechnicalCandles,
  technicalDataQuality,
} = require('../api/market');

test('missing provider status is UNKNOWN, never CLOSED', () => {
  assert.deepEqual(
    normalizeMarketStatus({}),
    { isOpen: false, status: 'UNKNOWN', isKnown: false }
  );
});

test('NSE exchange-map response is parsed instead of becoming UNKNOWN', () => {
  assert.deepEqual(
    normalizeMarketStatus({
      anyOpen: true,
      exchanges: {
        NSE: {
          name: 'Nairobi Securities Exchange',
          isOpen: true,
          status: 'OPEN',
          nextOpen: '2026-09-22T06:00:00.000Z',
          nextClose: '2026-09-21T12:00:00.000Z'
        }
      }
    }),
    { isOpen: true, status: 'OPEN', isKnown: true, nextOpen: '2026-09-22T06:00:00.000Z', nextClose: '2026-09-21T12:00:00.000Z' }
  );
});

test('explicit provider isOpen=false is a known CLOSED state', () => {
  assert.deepEqual(
    normalizeMarketStatus({ isOpen: false }),
    { isOpen: false, status: 'CLOSED', isKnown: true, nextOpen: null, nextClose: null }
  );
});

test('explicit provider isOpen=true is a known OPEN state', () => {
  assert.deepEqual(
    normalizeMarketStatus({ isOpen: true }),
    { isOpen: true, status: 'OPEN', isKnown: true, nextOpen: null, nextClose: null }
  );
});

test('recognized provider status is normalized to OPEN', () => {
  assert.deepEqual(
    normalizeMarketStatus({ status: 'trading' }),
    { isOpen: true, status: 'OPEN', isKnown: true }
  );
});

test('recognized provider status is normalized to CLOSED', () => {
  assert.deepEqual(
    normalizeMarketStatus({ marketStatus: 'closed' }),
    { isOpen: false, status: 'CLOSED', isKnown: true }
  );
});

test('conflicting explicit boolean and status remain UNKNOWN', () => {
  assert.deepEqual(
    normalizeMarketStatus({ isOpen: false, status: 'OPEN' }),
    { isOpen: false, status: 'UNKNOWN', isKnown: false }
  );
});

test('unrecognized provider state remains UNKNOWN', () => {
  assert.deepEqual(
    normalizeMarketStatus({ state: 'halted_pending' }),
    { isOpen: false, status: 'UNKNOWN', isKnown: false }
  );
});


test('latestTradingSession chooses the latest Nairobi day even when provider candles are unordered', () => {
  const session = latestTradingSession([
    { timestamp: '2026-09-18T12:00:00.000Z', open: 101, close: 103 },
    { timestamp: '2026-09-18T09:00:00.000Z', open: 100, close: 101 },
    { timestamp: '2026-09-17T12:00:00.000Z', open: 98, close: 99 },
    { timestamp: '2026-09-18T10:00:00.000Z', open: 101, close: 102 },
  ]);

  assert.deepEqual(session.candles.map((c) => c.timestamp), [
    '2026-09-18T09:00:00.000Z',
    '2026-09-18T10:00:00.000Z',
    '2026-09-18T12:00:00.000Z',
  ]);
  assert.equal(session.open, 100);
  assert.equal(session.close, 103);
});

test('latestTradingSession does not use close as a synthetic session open', () => {
  const session = latestTradingSession([
    { timestamp: '2026-09-18T09:00:00.000Z', close: 100 },
    { timestamp: '2026-09-18T10:00:00.000Z', close: 102 },
  ]);

  assert.equal(session.open, null);
  assert.equal(session.close, 102);
  assert.equal(session.openAt, '2026-09-18T09:00:00.000Z');
  assert.equal(session.closeAt, '2026-09-18T10:00:00.000Z');
});


test('historical candles are ordered chronologically when all timestamps are valid', () => {
  const candles = [
    { timestamp: '2026-09-18T12:00:00.000Z', close: 103 },
    { timestamp: '2026-09-18T09:00:00.000Z', close: 100 },
    { timestamp: '2026-09-18T10:00:00.000Z', close: 102 },
  ];

  assert.deepEqual(
    chronologicallyOrderedCandles(candles).map((c) => c.timestamp),
    [
      '2026-09-18T09:00:00.000Z',
      '2026-09-18T10:00:00.000Z',
      '2026-09-18T12:00:00.000Z',
    ]
  );
});

test('historical candle order is preserved when a timestamp is missing', () => {
  const candles = [
    { timestamp: '2026-09-18T12:00:00.000Z', close: 103 },
    { close: 100 },
  ];

  assert.deepEqual(chronologicallyOrderedCandles(candles), candles);
});


test('technical history uses a bounded daily lookback suitable for long moving averages', () => {
  const config = technicalHistoryConfig(new Date('2026-09-26T12:00:00.000Z'), 400);

  assert.equal(config.interval, '1d');
  assert.equal(config.to, '2026-09-26');
  assert.equal(config.from, '2025-08-22');
  assert.equal(config.lookbackDays, 400);

  assert.equal(technicalHistoryConfig(new Date('2026-09-26T12:00:00.000Z'), 10).lookbackDays, 260);
  assert.equal(technicalHistoryConfig(new Date('2026-09-26T12:00:00.000Z'), 5000).lookbackDays, 730);
});

test('technical candles preserve verified OHLCV and never invent missing fields', () => {
  const candles = normalizeTechnicalCandles([
    {
      date: '2026-09-25',
      close: 26.4,
      open: null,
      high: null,
      low: null,
      volume: 1200000,
      volumeAvailable: false,
    },
    {
      date: '2026-09-24',
      open: 25.0,
      high: 26.0,
      low: 24.8,
      close: 25.6,
      volume: 900000,
      volumeAvailable: true,
    },
  ]);

  assert.deepEqual(candles.map((candle) => candle.date), ['2026-09-24', '2026-09-25']);
  assert.equal(candles[0].open, 25);
  assert.equal(candles[0].volume, 900000);
  assert.equal(candles[0].volumeAvailable, true);

  assert.equal(candles[1].open, null);
  assert.equal(candles[1].high, null);
  assert.equal(candles[1].low, null);
  assert.equal(candles[1].volume, null);
  assert.equal(candles[1].volumeAvailable, false);
});

test('technical data quality reports coverage and indicator readiness from actual candles', () => {
  const candles = Array.from({ length: 205 }, (_, index) => ({
    date: `2026-01-${String((index % 28) + 1).padStart(2, '0')}`,
    timestamp: new Date(Date.UTC(2026, 0, 1 + index)).toISOString(),
    open: 10 + index,
    high: 11 + index,
    low: 9 + index,
    close: 10.5 + index,
    volume: index >= 185 ? 100000 + index : null,
    volumeAvailable: index >= 185,
  }));

  const quality = technicalDataQuality(candles, {
    qualityStatus: 'PARTIAL',
    qualityIssues: ['OLDER_VOLUME_MISSING'],
    recommendedChartType: 'candlestick',
    sandbox: false,
  });

  assert.equal(quality.candleCount, 205);
  assert.equal(quality.completeOhlcCount, 205);
  assert.equal(quality.volumeAvailableCount, 20);
  assert.equal(quality.ohlcCoveragePct, 100);
  assert.equal(quality.volumeCoveragePct, 9.76);
  assert.equal(quality.indicatorReadiness.sma200, true);
  assert.equal(quality.indicatorReadiness.macd, true);
  assert.equal(quality.indicatorReadiness.stochastic14, true);
  assert.equal(quality.indicatorReadiness.volume20, true);
  assert.deepEqual(quality.qualityIssues, ['OLDER_VOLUME_MISSING']);
});

test('stochastic and volume readiness stay false when required trailing observations are incomplete', () => {
  const candles = Array.from({ length: 40 }, (_, index) => ({
    date: `row-${index}`,
    close: 100 + index,
    open: index < 30 ? 99 + index : null,
    high: index < 30 ? 101 + index : null,
    low: index < 30 ? 98 + index : null,
    volume: index < 15 ? 1000 + index : null,
    volumeAvailable: index < 15,
  }));

  const quality = technicalDataQuality(candles);

  assert.equal(quality.indicatorReadiness.rsi14, true);
  assert.equal(quality.indicatorReadiness.macd, true);
  assert.equal(quality.indicatorReadiness.stochastic14, false);
  assert.equal(quality.indicatorReadiness.volume20, false);
});
