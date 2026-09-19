const assert = require('node:assert/strict');
const test = require('node:test');

const { normalizeMarketStatus } = require('../api/market');

test('missing provider status is UNKNOWN, never CLOSED', () => {
  assert.deepEqual(
    normalizeMarketStatus({}),
    { isOpen: false, status: 'UNKNOWN', isKnown: false }
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
