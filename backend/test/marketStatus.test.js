const assert = require('node:assert/strict');
const test = require('node:test');

const { normalizeMarketStatus, latestTradingSession, chronologicallyOrderedCandles } = require('../api/market');

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
    { isOpen: true, status: 'OPEN', isKnown: true }
  );
});

test('explicit provider isOpen=false is a known CLOSED state', () => {
  assert.deepEqual(
    normalizeMarketStatus({ isOpen: false }),
    { isOpen: false, status: 'CLOSED', isKnown: true }
  );
});

test('explicit provider isOpen=true is a known OPEN state', () => {
  assert.deepEqual(
    normalizeMarketStatus({ isOpen: true }),
    { isOpen: true, status: 'OPEN', isKnown: true }
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
