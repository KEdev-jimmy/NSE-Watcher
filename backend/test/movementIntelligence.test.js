const test = require('node:test');
const assert = require('node:assert/strict');

const { latestMove } = require('../lib/movementIntelligence');

test('latestMove uses dated candles when an exact window is available', () => {
  const result = latestMove([
    { date: '2026-09-10', close: 100 },
    { date: '2026-09-11', close: 105 },
    { date: '2026-09-17', close: 110 },
  ], 7);

  assert.equal(result.from, '2026-09-10');
  assert.equal(result.to, '2026-09-17');
  assert.equal(result.priceBefore, 100);
  assert.equal(result.priceAfter, 110);
  assert.equal(result.change, '+10.00%');
});

test('latestMove returns unavailable instead of inventing a time window from a quote', () => {
  assert.equal(latestMove([], 1), null);
});

test('latestMove does not use undated observations', () => {
  const result = latestMove([
    { date: '', close: 100 },
    { date: '2026-09-17', close: 110 },
  ], 1);

  assert.equal(result, null);
});

test('latestMove can use the nearest earlier dated session when the calendar cutoff is a weekend', () => {
  const result = latestMove([
    { date: '2026-09-11', close: 100 },
    { date: '2026-09-15', close: 105 },
    { date: '2026-09-17', close: 110 },
  ], 7);

  assert.equal(result.from, '2026-09-11');
  assert.equal(result.to, '2026-09-17');
  assert.equal(result.change, '+10.00%');
});