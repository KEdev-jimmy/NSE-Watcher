const assert = require('node:assert/strict');
const test = require('node:test');

const {
  intelligenceRelevance,
  isMarketRelevantText,
  INTELLIGENCE_MARKET_TERMS,
} = require('../lib/newsRelevance');

test('market terminology is exported for the news pipeline', () => {
  assert.ok(INTELLIGENCE_MARKET_TERMS.includes('nse'));
  assert.ok(INTELLIGENCE_MARKET_TERMS.includes('dividend'));
  assert.ok(INTELLIGENCE_MARKET_TERMS.includes('financial results'));
});

test('news with an NSE market signal is classified as market relevant', () => {
  const result = intelligenceRelevance({
    title: 'NSE trading session sees higher turnover',
    summary: 'Investors traded listed shares during the session.',
    body: '',
    symbol: '',
    companyName: '',
    category: 'Market',
  });

  assert.equal(result.level, 'market');
  assert.match(result.reason, /market-relevant/i);
});

test('company news without a market signal remains company-level', () => {
  const result = intelligenceRelevance({
    title: 'Safaricom launches a new customer service initiative',
    summary: 'The company announced changes to its customer experience.',
    body: '',
    symbol: 'SCOM',
    companyName: 'Safaricom',
    category: 'Company News',
  });

  assert.equal(result.level, 'company');
});

test('dividend category is market relevant even without a keyword in the text', () => {
  const result = intelligenceRelevance({
    title: 'Shareholder notice',
    summary: 'A distribution announcement was published.',
    body: '',
    symbol: '',
    companyName: '',
    category: 'Dividends',
  });

  assert.equal(result.level, 'market');
});

test('unrelated news is classified as general', () => {
  const result = intelligenceRelevance({
    title: 'Local school hosts annual sports day',
    summary: 'Students competed in athletics and football.',
    body: '',
    symbol: '',
    companyName: '',
    category: 'General',
  });

  assert.equal(result.level, 'general');
});

test('legacy market relevance helper agrees with intelligence relevance', () => {
  assert.equal(
    isMarketRelevantText('NSE announces a new listing', ''),
    true
  );
  assert.equal(
    isMarketRelevantText('Community sports event held in Nairobi', ''),
    false
  );
});
