const { isMarketRelevantText } = require('./newsRelevance');
const BASE_URL = process.env.MYSTOCKS_BASE_URL || 'https://mystocks.africa/api/v1/partner';
const API_KEY = process.env.MYSTOCKS_API_KEY;
const CACHE_CONTROL = 's-maxage=300, stale-while-revalidate=900';

function symbolFor(raw) {
  const value = String(raw || '').trim().toUpperCase();
  return value.includes('.') ? value : `${value}.KE`;
}

function bareSymbol(raw) {
  return symbolFor(raw).replace(/\.KE$/i, '');
}

function number(value) {
  const parsed = Number(String(value ?? '').replace(/,/g, '').replace(/[^0-9.-]/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
}

function dateValue(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function isoDate(value) {
  const date = dateValue(value);
  return date ? date.toISOString().slice(0, 10) : '';
}

function daysBetween(a, b) {
  const first = dateValue(a);
  const second = dateValue(b);
  if (!first || !second) return null;
  return Math.round(Math.abs(first.getTime() - second.getTime()) / 86400000);
}

function pct(current, previous) {
  const a = number(current);
  const b = number(previous);
  if (a === null || b === null || b === 0) return null;
  return ((a - b) / Math.abs(b)) * 100;
}

function formatPct(value) {
  return value === null || !Number.isFinite(value) ? '' : `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;
}

async function mystocks(path) {
  if (!API_KEY) throw new Error('MYSTOCKS_API_KEY is not configured');
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'x-api-key': API_KEY, Accept: 'application/json' },
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = { raw: text }; }
  if (!response.ok) {
    const error = new Error(`MyStocks ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

function arrayFrom(value, keys = []) {
  if (Array.isArray(value)) return value;
  for (const key of keys) {
    if (Array.isArray(value?.[key])) return value[key];
    if (Array.isArray(value?.data?.[key])) return value.data[key];
  }
  if (Array.isArray(value?.data)) return value.data;
  return [];
}

function candleDate(candle) {
  return candle?.date || candle?.timestamp || candle?.time || candle?.datetime || candle?.label || '';
}

function candleClose(candle) {
  return number(candle?.close ?? candle?.price ?? candle?.c);
}

function normalizeCandles(raw) {
  return arrayFrom(raw, ['candles', 'history', 'prices'])
    .map(item => ({ date: candleDate(item), close: candleClose(item), volume: number(item?.volume ?? item?.v) }))
    .filter(item => item.close !== null && dateValue(item.date))
    .sort((a, b) => dateValue(a.date) - dateValue(b.date));
}

function latestMove(candles, days) {
  if (!candles.length) return null;
  const latest = candles[candles.length - 1];
  const cutoff = new Date(dateValue(latest.date).getTime() - days * 86400000);
  let previous = null;
  for (const candle of candles) {
    if (dateValue(candle.date) <= cutoff) previous = candle;
  }
  if (!previous && candles.length > 1) previous = candles[Math.max(0, candles.length - Math.min(candles.length, days + 1))];
  if (!previous || previous.close === null) return null;
  const change = pct(latest.close, previous.close);
  return {
    periodDays: days,
    from: isoDate(previous.date),
    to: isoDate(latest.date),
    priceBefore: previous.close,
    priceAfter: latest.close,
    changePct: change,
    change: formatPct(change),
  };
}

function currentQuote(raw) {
  const data = raw?.data && !Array.isArray(raw.data) ? raw.data : raw;
  return {
    price: number(data?.price ?? data?.lastPrice ?? data?.close ?? data?.ltp),
    changePct: number(data?.changePercent ?? data?.percentChange ?? data?.changePct),
    change: number(data?.change),
    currency: data?.currency || 'KES',
    delayMinutes: number(data?.delayMinutes ?? data?.delay) ?? 15,
  };
}

function newsItems(raw) {
  return arrayFrom(raw, ['news', 'items', 'articles']).map(item => ({
    title: String(item?.title || item?.headline || '').replace(/\s+/g, ' ').trim(),
    description: String(item?.description || item?.summary || item?.content || '').replace(/\s+/g, ' ').trim(),
    date: item?.publishedAt || item?.createdAt || item?.date || item?.published || '',
    source: item?.source || item?.publisher || 'MyStocks Africa',
    url: item?.url || item?.link || item?.sourceUrl || '',
  })).filter(item => item.title && dateValue(item.date));
}

function dividendItems(raw) {
  return arrayFrom(raw, ['history', 'dividends', 'items']).map(item => ({
    amount: number(item?.amount ?? item?.dividend ?? item?.value),
    exDate: item?.exDate || item?.ex_dividend_date || item?.date || '',
    paymentDate: item?.paymentDate || item?.payDate || '',
    declaredDate: item?.declaredDate || item?.declarationDate || '',
    source: 'MyStocks Africa',
  })).filter(item => item.amount !== null && (item.exDate || item.paymentDate || item.declaredDate));
}

function classifyNews(item) {
  const text = `${item.title} ${item.description}`.toLowerCase();
  if (/dividend|payout|interim dividend|final dividend/.test(text)) return 'dividend';
  if (/results|profit|revenue|earnings|eps|financial statements|full year|half year|interim results/.test(text)) return 'financial-results';
  if (/acqui|merger|takeover|deal|partnership|contract|agreement|investment|stake|sale of|disposal/.test(text)) return 'corporate-action';
  if (/ceo|chief executive|director|board|management|appointment|resign/.test(text)) return 'management';
  if (/regulator|cma|court|fine|penalty|license|approval|government|policy/.test(text)) return 'regulatory';
  return 'company-news';
}

function eventFromNews(item, moveDate) {
  const age = daysBetween(item.date, moveDate);
  const category = classifyNews(item);
  let relationship = 'possible';
  if (age !== null && age <= 2) relationship = 'related';
  if (age !== null && age > 7) relationship = 'not-established';
  return {
    eventType: category,
    title: item.title,
    date: isoDate(item.date),
    source: item.source,
    sourceUrl: item.url,
    description: item.description,
    relationship,
    daysFromMove: age,
  };
}

function dividendEvents(dividends, moveDate) {
  return dividends.map(item => {
    const eventDate = item.exDate || item.paymentDate || item.declaredDate;
    const age = daysBetween(eventDate, moveDate);
    return {
      eventType: 'dividend',
      title: `Dividend of KSh ${item.amount.toFixed(2)} per share`,
      date: isoDate(eventDate),
      source: item.source,
      sourceUrl: '',
      description: `Dividend record: ex-dividend ${isoDate(item.exDate) || 'not supplied'}; payment ${isoDate(item.paymentDate) || 'not supplied'}.`,
      relationship: age !== null && age <= 2 ? 'related' : age !== null && age <= 7 ? 'possible' : 'not-established',
      daysFromMove: age,
    };
  });
}

function rankEvents(events) {
  const rank = { related: 3, possible: 2, 'not-established': 1 };
  return events.sort((a, b) => {
    const scoreA = (rank[a.relationship] || 0) * 100 - (a.daysFromMove ?? 99);
    const scoreB = (rank[b.relationship] || 0) * 100 - (b.daysFromMove ?? 99);
    return scoreB - scoreA;
  });
}

function buildSummary(symbol, quote, move, events) {
  if (!move) return `Price history was not available for ${symbol}; the app cannot establish a reliable move window yet.`;
  if (!events.length) return `${symbol} moved ${move.change} over the latest ${move.periodDays === 1 ? '1-day' : `${move.periodDays}-day`} window, but no dated company event was found in the available evidence.`;
  const related = events.filter(event => event.relationship === 'related');
  if (related.length) return `${symbol} moved ${move.change} and there are ${related.length} company event(s) dated close to the move. These are evidence-linked relationships, not proof of causation.`;
  return `${symbol} moved ${move.change}. Recent company evidence was found, but the available data does not establish that any single event caused the move.`;
}

async function handler(req, res) {
  const rawSymbol = req.query?.symbol || req.query?.ticker;
  const symbol = symbolFor(rawSymbol);
  if (!bareSymbol(rawSymbol)) {
    return res.status(400).json({ error: 'symbol is required' });
  }

  try {
    const [quoteResult, historyResult, newsResult, dividendsResult] = await Promise.allSettled([
      mystocks(`/stocks/${encodeURIComponent(symbol)}`),
      mystocks(`/stocks/${encodeURIComponent(symbol)}/candles?interval=1d&from=${new Date(Date.now() - 1000 * 86400000 * 400).toISOString().slice(0, 10)}&to=${new Date().toISOString().slice(0, 10)}`),
      mystocks(`/companies/${encodeURIComponent(symbol)}/news?limit=30`),
      mystocks(`/dividends/${encodeURIComponent(symbol)}/history?limit=12`),
    ]);

    const quote = quoteResult.status === 'fulfilled' ? currentQuote(quoteResult.value) : currentQuote({});
    const candles = historyResult.status === 'fulfilled' ? normalizeCandles(historyResult.value) : [];
    const move = latestMove(candles, 1) || (quote.changePct !== null ? { change: formatPct(quote.changePct), changePct: quote.changePct, from: '', to: '', priceBefore: null, priceAfter: quote.price, periodDays: 1 } : null);
    const news = newsResult.status === 'fulfilled'
      ? newsItems(newsResult.value).filter(item => isMarketRelevantText(item.title, item.description))
      : [];
    const dividends = dividendsResult.status === 'fulfilled' ? dividendItems(dividendsResult.value) : [];
    const moveDate = move?.to || isoDate(new Date());

    const events = rankEvents([
      ...news.slice(0, 20).map(item => eventFromNews(item, moveDate)),
      ...dividendEvents(dividends, moveDate),
    ]).slice(0, 10);

    const moves = {
      '1D': latestMove(candles, 1),
      '1W': latestMove(candles, 7),
      '1M': latestMove(candles, 30),
      '3M': latestMove(candles, 90),
    };

    const sources = [
      quoteResult.status === 'fulfilled' ? 'MyStocks Africa • stock quote' : '',
      historyResult.status === 'fulfilled' ? 'MyStocks Africa • historical candles' : '',
      newsResult.status === 'fulfilled' ? 'MyStocks Africa • company news' : '',
      dividendsResult.status === 'fulfilled' ? 'MyStocks Africa • dividend history' : '',
    ].filter(Boolean);

    return res.status(200)
      .setHeader('Content-Type', 'application/json')
      .setHeader('Cache-Control', CACHE_CONTROL)
      .end(JSON.stringify({
        source: 'NSE Watcher movement intelligence',
        symbol,
        fetchedAt: new Date().toISOString(),
        quote,
        move,
        moves,
        summary: buildSummary(symbol, quote, move, events),
        evidence: events,
        limitations: [
          'A time correlation does not prove that an event caused a price move.',
          'Only dated evidence returned by the connected providers is included.',
          'Market-wide and sector-wide drivers are not yet attributed in this first evidence pass.',
        ],
        sources,
        partial: [quoteResult, historyResult, newsResult, dividendsResult].some(result => result.status === 'rejected'),
      }));
  } catch (error) {
    return res.status(500).json({
      error: 'Movement intelligence unavailable',
      message: error.message,
    });
  }
}

module.exports = handler;
