const INTELLIGENCE_MARKET_TERMS = [
  'nse', 'nairobi securities exchange', 'capital markets', 'stock market', 'share price',
  'shareholders', 'listed company', 'listing', 'dividend', 'payout', 'earnings', 'revenue',
  'financial results', 'financial statements', 'eps', 'rights issue', 'bonus issue', 'share split',
  'acquisition', 'merger', 'takeover', 'ipo', 'bond', 'treasury', 'cbk', 'central bank', 'cma',
  'investor', 'investors', 'trading', 'broker', 'brokerage', 'fund manager', 'reit', 'etf',
  'interest rate', 'inflation', 'forex', 'shilling', 'corporate action', 'agm',
  'annual general meeting', 'profit warning', 'profit after tax', 'profit before tax',
  'operating profit', 'net income', 'guidance', 'outlook', 'material contract',
  'stake acquisition', 'stake sale', 'disposal', 'regulatory approval', 'regulatory action',
  'fine', 'penalty', 'license', 'licence', 'suspension', 'appointment of', 'resignation of'
];

function intelligenceRelevance(item) {
  const haystack = [item.title, item.summary, item.body].filter(Boolean).join(' ').toLowerCase();
  const category = String(item.category || '').toLowerCase();
  const hasCompany = Boolean(item.symbol || item.companyName);
  const hasMarketSignal = INTELLIGENCE_MARKET_TERMS.some(term => haystack.includes(term));
  const marketCategory = category.includes('dividend') || category.includes('corporate') ||
    category.includes('analysis') || category.includes('earnings');

  if (marketCategory || hasMarketSignal) {
    return { level: 'market', reason: marketCategory
      ? 'the story is classified as a market or corporate-action category'
      : 'market-relevant terms or events are present in the story text' };
  }
  if (hasCompany) {
    return { level: 'company', reason: 'a listed company is identified, but no market-relevant signal was established' };
  }
  return { level: 'general', reason: 'no listed-company or market-relevant signal was established' };
}

function isMarketRelevantText(title, description) {
  const text = [title, description].filter(Boolean).join(' ');
  return intelligenceRelevance({ title, summary: description, body: '', symbol: '', companyName: '' }).level === 'market';
}

module.exports = { intelligenceRelevance, isMarketRelevantText };
